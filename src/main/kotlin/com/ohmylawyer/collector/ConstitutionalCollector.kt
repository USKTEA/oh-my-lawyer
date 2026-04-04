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
 * 헌재결정례 수집기 (target=detc).
 */
@Service
class ConstitutionalCollector(
    progressRepository: CollectionProgressRepository,
    private val apiClient: LawApiClient,
    props: LawApiProperties,
    private val lawDocumentRepository: LawDocumentRepository,
    private val lawChunkRepository: LawChunkRepository
) : AbstractCollector(progressRepository, apiClient, props) {

    override val log = LoggerFactory.getLogger(javaClass)
    override val taskType = "COLLECT_CONSTITUTIONAL"
    override val dataType = DocumentType.CONSTITUTIONAL

    override fun doSearch(query: String?, page: Int): JsonNode {
        return apiClient.searchConstitutional(query, page)
    }

    override fun parseTotalCount(searchResult: JsonNode): Int {
        // Response: {"DetcSearch": {"totalCnt": N, "Detc": [...]}}
        return searchResult.path("DetcSearch").path("totalCnt").asInt(0)
    }

    override fun parseSearchItems(searchResult: JsonNode): List<JsonNode> {
        val items = searchResult.path("DetcSearch").path("Detc")
        if (items.isMissingNode) return emptyList()
        return if (items.isArray) items.toList() else listOf(items)
    }

    @Transactional
    override fun processItem(item: JsonNode) {
        val id = item.textOrNull("헌재결정례일련번호") ?: return
        val caseName = item.textOrNull("사건명") ?: ""

        if (lawDocumentRepository.findByTypeAndSourceId(DocumentType.CONSTITUTIONAL, id) != null) {
            log.debug("Constitutional case already exists: {} ({})", caseName, id)
            return
        }

        log.info("Fetching constitutional detail: {} ({})", caseName, id)
        val detail = apiClient.getConstitutionalDetail(id)

        // Response: {"DetcService": {...}}
        val node = detail.path("DetcService")
        val fullText = node.textOrNull("전문") ?: ""
        val holding = node.textOrNull("판시사항") ?: ""
        val summary = node.textOrNull("결정요지") ?: ""
        val caseNumber = node.textOrNull("사건번호") ?: item.textOrNull("사건번호") ?: ""
        val decisionDate = node.textOrNull("종국일자") ?: item.textOrNull("종국일자")

        val metadata = mapOf(
            "caseNumber" to caseNumber,
            "caseType" to (node.textOrNull("사건종류명") ?: ""),
            "refArticles" to (node.textOrNull("참조조문") ?: ""),
            "refCases" to (node.textOrNull("참조판례") ?: ""),
            "targetArticles" to (node.textOrNull("심판대상조문") ?: "")
        ).filter { it.value.isNotBlank() }

        val document = lawDocumentRepository.save(
            LawDocument(
                type = DocumentType.CONSTITUTIONAL,
                title = "$caseName ($caseNumber)",
                fullText = fullText.ifBlank { "$holding\n\n$summary" },
                sourceUrl = "https://www.law.go.kr/헌재결정례/${caseName}",
                sourceId = id,
                metadata = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(metadata),
                enactedDate = decisionDate?.toLocalDate()
            )
        )

        var chunkIndex = 0

        if (holding.isNotBlank() || summary.isNotBlank()) {
            val content = buildString {
                if (holding.isNotBlank()) { appendLine("[판시사항]"); appendLine(holding); appendLine() }
                if (summary.isNotBlank()) { appendLine("[결정요지]"); appendLine(summary) }
            }.trim()

            lawChunkRepository.save(
                LawChunk(document = document, content = content, chunkType = ChunkType.SUMMARY,
                    metadata = """{"caseNumber":"$caseNumber"}""", chunkIndex = chunkIndex++)
            )
        }

        if (fullText.isNotBlank()) {
            for (section in fullText.chunked(4000)) {
                lawChunkRepository.save(
                    LawChunk(document = document, content = section, chunkType = ChunkType.HOLDING,
                        metadata = """{"caseNumber":"$caseNumber","sectionIndex":$chunkIndex}""", chunkIndex = chunkIndex++)
                )
            }
        }

        log.info("Saved constitutional case: {} with {} chunks", document.title, chunkIndex)
    }
}
