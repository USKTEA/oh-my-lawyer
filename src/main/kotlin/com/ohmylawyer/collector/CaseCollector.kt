package com.ohmylawyer.collector

import com.fasterxml.jackson.databind.JsonNode
import com.ohmylawyer.domain.entity.*
import com.ohmylawyer.domain.repository.CollectionProgressRepository
import com.ohmylawyer.domain.repository.LawChunkRepository
import com.ohmylawyer.domain.repository.LawDocumentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 판례 수집기 (target=prec).
 * 판시사항 + 판결요지를 SUMMARY chunk, 판례내용(전문)을 HOLDING chunk로 분리.
 */
@Service
class CaseCollector(
    progressRepository: CollectionProgressRepository,
    private val apiClient: LawApiClient,
    props: LawApiProperties,
    private val lawDocumentRepository: LawDocumentRepository,
    private val lawChunkRepository: LawChunkRepository
) : AbstractCollector(progressRepository, apiClient, props) {

    override val log = LoggerFactory.getLogger(javaClass)
    override val taskType = "COLLECT_CASE"
    override val dataType = DocumentType.CASE

    override fun doSearch(query: String?, page: Int): JsonNode {
        return apiClient.searchCases(query, page)
    }

    override fun parseTotalCount(searchResult: JsonNode): Int {
        // Response: {"PrecSearch": {"totalCnt": N, "prec": [...]}}
        return searchResult.path("PrecSearch").path("totalCnt").asInt(0)
    }

    override fun parseSearchItems(searchResult: JsonNode): List<JsonNode> {
        val items = searchResult.path("PrecSearch").path("prec")
        if (items.isMissingNode) return emptyList()
        return if (items.isArray) items.toList() else listOf(items)
    }



    @Transactional
    override fun processItem(item: JsonNode) {
        val caseId = item.textOrNull("판례일련번호") ?: return
        val caseName = item.textOrNull("사건명") ?: ""

        if (lawDocumentRepository.findByTypeAndSourceId(DocumentType.CASE, caseId) != null) {
            log.debug("Case already exists: {} ({})", caseName, caseId)
            return
        }

        log.info("Fetching case detail: {} ({})", caseName, caseId)
        val detail = apiClient.getCaseDetail(caseId)

        val caseNode = detail.path("PrecService")
        val fullText = caseNode.textOrNull("판례내용") ?: ""
        val holding = caseNode.textOrNull("판시사항") ?: ""
        val summary = caseNode.textOrNull("판결요지") ?: ""
        val caseNumber = caseNode.textOrNull("사건번호") ?: item.textOrNull("사건번호") ?: ""
        val courtName = caseNode.textOrNull("법원명") ?: item.textOrNull("법원명") ?: ""
        val decisionDate = caseNode.textOrNull("선고일자") ?: item.textOrNull("선고일자")

        val metadata = buildCaseMetadata(caseNode, item)

        // Strip HTML <br/> tags from content
        val cleanFullText = fullText.replace(Regex("<br\\s*/?>"), "\n")
        val cleanHolding = holding.replace(Regex("<br\\s*/?>"), "\n")
        val cleanSummary = summary.replace(Regex("<br\\s*/?>"), "\n")

        val document = lawDocumentRepository.save(
            LawDocument(
                type = DocumentType.CASE,
                title = "$caseName ($caseNumber)",
                fullText = cleanFullText.ifBlank { "$cleanHolding\n\n$cleanSummary" },
                sourceUrl = "https://www.law.go.kr/판례/${caseName}",
                sourceId = caseId,
                metadata = metadata,
                enactedDate = decisionDate?.toLocalDate()
            )
        )

        var chunkIndex = 0

        // SUMMARY chunk: 판시사항 + 판결요지
        if (cleanHolding.isNotBlank() || cleanSummary.isNotBlank()) {
            val summaryContent = buildString {
                if (cleanHolding.isNotBlank()) {
                    appendLine("[판시사항]")
                    appendLine(cleanHolding)
                    appendLine()
                }
                if (cleanSummary.isNotBlank()) {
                    appendLine("[판결요지]")
                    appendLine(cleanSummary)
                }
            }.trim()

            lawChunkRepository.save(
                LawChunk(
                    document = document,
                    content = summaryContent,
                    chunkType = ChunkType.SUMMARY,
                    metadata = """{"caseNumber":"$caseNumber","courtName":"$courtName"}""",
                    chunkIndex = chunkIndex++
                )
            )
        }

        // HOLDING chunks: 판례내용(전문) - split into sections if too long
        if (cleanFullText.isNotBlank()) {
            val sections = splitCaseText(cleanFullText)
            for (section in sections) {
                lawChunkRepository.save(
                    LawChunk(
                        document = document,
                        content = section,
                        chunkType = ChunkType.HOLDING,
                        metadata = """{"caseNumber":"$caseNumber","courtName":"$courtName","sectionIndex":$chunkIndex}""",
                        chunkIndex = chunkIndex++
                    )
                )
            }
        }

        log.info("Saved case: {} with {} chunks", document.title, chunkIndex)
    }

    private fun splitCaseText(text: String, maxChunkSize: Int = 4000): List<String> {
        if (text.length <= maxChunkSize) return listOf(text)

        // Split by major sections: 【이유】, 【주문】, 【판결요지】 etc.
        val sectionPattern = Regex("【[^】]+】")
        val sections = mutableListOf<String>()
        var lastEnd = 0

        for (match in sectionPattern.findAll(text)) {
            if (match.range.first > lastEnd) {
                val segment = text.substring(lastEnd, match.range.first).trim()
                if (segment.isNotBlank()) sections.add(segment)
            }
            lastEnd = match.range.first
        }
        if (lastEnd < text.length) {
            val segment = text.substring(lastEnd).trim()
            if (segment.isNotBlank()) sections.add(segment)
        }

        // If no sections found or still too long, split by paragraphs
        return sections.flatMap { section ->
            if (section.length <= maxChunkSize) listOf(section)
            else section.chunked(maxChunkSize)
        }
    }

    private fun buildCaseMetadata(caseNode: JsonNode, searchItem: JsonNode): String {
        val fields = mapOf(
            "caseNumber" to (caseNode.textOrNull("사건번호") ?: searchItem.textOrNull("사건번호") ?: ""),
            "courtName" to (caseNode.textOrNull("법원명") ?: searchItem.textOrNull("법원명") ?: ""),
            "caseType" to (caseNode.textOrNull("사건종류명") ?: searchItem.textOrNull("사건종류명") ?: ""),
            "decisionType" to (caseNode.textOrNull("판결유형") ?: ""),
            "refArticles" to (caseNode.textOrNull("참조조문") ?: ""),
            "refCases" to (caseNode.textOrNull("참조판례") ?: "")
        ).filter { it.value.isNotBlank() }

        return com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(fields)
    }
}
