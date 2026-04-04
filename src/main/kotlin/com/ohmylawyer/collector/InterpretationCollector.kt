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
 * 법령해석례 수집기 (target=expc).
 */
@Service
class InterpretationCollector(
    progressRepository: CollectionProgressRepository,
    private val apiClient: LawApiClient,
    props: LawApiProperties,
    private val lawDocumentRepository: LawDocumentRepository,
    private val lawChunkRepository: LawChunkRepository
) : AbstractCollector(progressRepository, apiClient, props) {

    override val log = LoggerFactory.getLogger(javaClass)
    override val taskType = "COLLECT_INTERPRETATION"
    override val dataType = DocumentType.INTERPRETATION

    override fun doSearch(query: String?, page: Int): JsonNode {
        return apiClient.searchInterpretations(query, page)
    }

    override fun parseTotalCount(searchResult: JsonNode): Int {
        // Response: {"Expc": {"totalCnt": N, "expc": [...]}}
        return searchResult.path("Expc").path("totalCnt").asInt(0)
    }

    override fun parseSearchItems(searchResult: JsonNode): List<JsonNode> {
        val items = searchResult.path("Expc").path("expc")
        if (items.isMissingNode) return emptyList()
        return if (items.isArray) items.toList() else listOf(items)
    }

    @Transactional
    override fun processItem(item: JsonNode) {
        val id = item.textOrNull("법령해석례일련번호") ?: return
        val title = item.textOrNull("안건명") ?: ""

        if (lawDocumentRepository.findByTypeAndSourceId(DocumentType.INTERPRETATION, id) != null) {
            log.debug("Interpretation already exists: {} ({})", title, id)
            return
        }

        log.info("Fetching interpretation detail: {} ({})", title, id)
        val detail = apiClient.getInterpretationDetail(id)

        // Response: {"ExpcService": {...}}
        val node = detail.path("ExpcService")
        val question = node.textOrNull("질의요지") ?: ""
        val answer = node.textOrNull("회답") ?: ""
        val reason = node.textOrNull("이유") ?: ""
        val caseNumber = node.textOrNull("안건번호") ?: item.textOrNull("안건번호") ?: ""
        val interpretDate = node.textOrNull("해석일자") ?: item.textOrNull("해석일자")

        val fullText = buildString {
            if (question.isNotBlank()) { appendLine("[질의요지]"); appendLine(question); appendLine() }
            if (answer.isNotBlank()) { appendLine("[회답]"); appendLine(answer); appendLine() }
            if (reason.isNotBlank()) { appendLine("[이유]"); appendLine(reason) }
        }.trim()

        val metadata = mapOf(
            "caseNumber" to caseNumber,
            "interpretOrg" to (node.textOrNull("해석기관명") ?: ""),
            "questionOrg" to (node.textOrNull("질의기관명") ?: "")
        ).filter { it.value.isNotBlank() }

        val document = lawDocumentRepository.save(
            LawDocument(
                type = DocumentType.INTERPRETATION,
                title = "$title ($caseNumber)",
                fullText = fullText,
                sourceId = id,
                metadata = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(metadata),
                enactedDate = interpretDate?.toLocalDate()
            )
        )

        // Single chunk for interpretation: question + answer + reason
        if (fullText.isNotBlank()) {
            lawChunkRepository.save(
                LawChunk(
                    document = document,
                    content = fullText,
                    chunkType = ChunkType.INTERPRETATION_BODY,
                    metadata = """{"caseNumber":"$caseNumber"}""",
                    chunkIndex = 0
                )
            )
        }

        log.info("Saved interpretation: {}", document.title)
    }
}
