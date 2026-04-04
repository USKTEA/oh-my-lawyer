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
 * 행정규칙 수집기 (target=admrul).
 * 개인정보보호위원회 고시, 훈령 등 포함.
 */
@Service
class AdministrativeRuleCollector(
    progressRepository: CollectionProgressRepository,
    private val apiClient: LawApiClient,
    props: LawApiProperties,
    private val lawDocumentRepository: LawDocumentRepository,
    private val lawChunkRepository: LawChunkRepository
) : AbstractCollector(progressRepository, apiClient, props) {

    override val log = LoggerFactory.getLogger(javaClass)
    override val taskType = "COLLECT_ADMINISTRATIVE_RULE"
    override val dataType = DocumentType.ADMINISTRATIVE_RULE

    override fun doSearch(query: String?, page: Int): JsonNode {
        return apiClient.search("admrul", query, page)
    }

    override fun parseTotalCount(searchResult: JsonNode): Int {
        // Response: {"AdmRulSearch": {"totalCnt": N, "admrul": [...]}}
        return searchResult.path("AdmRulSearch").path("totalCnt").asInt(0)
    }

    override fun parseSearchItems(searchResult: JsonNode): List<JsonNode> {
        val items = searchResult.path("AdmRulSearch").path("admrul")
        if (items.isMissingNode) return emptyList()
        return if (items.isArray) items.toList() else listOf(items)
    }

    @Transactional
    override fun processItem(item: JsonNode) {
        val id = item.textOrNull("행정규칙일련번호") ?: return
        val title = item.textOrNull("행정규칙명") ?: ""

        if (lawDocumentRepository.findByTypeAndSourceId(DocumentType.ADMINISTRATIVE_RULE, id) != null) {
            log.debug("Administrative rule already exists: {} ({})", title, id)
            return
        }

        log.info("Fetching administrative rule detail: {} ({})", title, id)
        val detail = apiClient.getDetail("admrul", id)

        // Response: {"AdmRulService": {"행정규칙기본정보": {...}, "조문내용": {...}, ...}}
        val node = detail.path("AdmRulService")
        val basicInfo = node.path("행정규칙기본정보")
        val articles = node.path("조문내용")

        val fullText = buildFullText(articles)
        val enactedDate = item.textOrNull("시행일자")

        val metadata = mapOf(
            "ruleType" to (item.textOrNull("행정규칙종류") ?: ""),
            "ministry" to (item.textOrNull("소관부처명") ?: ""),
            "ruleId" to (item.textOrNull("행정규칙ID") ?: ""),
            "amendType" to (item.textOrNull("제개정구분명") ?: "")
        ).filter { it.value.isNotBlank() }

        val document = lawDocumentRepository.save(
            LawDocument(
                type = DocumentType.ADMINISTRATIVE_RULE,
                title = title,
                fullText = fullText,
                sourceId = id,
                metadata = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(metadata),
                enactedDate = enactedDate?.toLocalDate()
            )
        )

        // Chunk by articles
        val articleList = if (articles.isArray) articles.toList()
            else if (articles.isObject) listOf(articles)
            else emptyList()

        var chunkIndex = 0
        for (article in articleList) {
            val content = article.textOrNull("조문내용") ?: article.toString()
            if (content.isNotBlank() && content != "{}") {
                lawChunkRepository.save(
                    LawChunk(
                        document = document,
                        content = content.replace(Regex("<br\\s*/?>"), "\n"),
                        chunkType = ChunkType.ARTICLE,
                        metadata = """{"articleNum":"${article.textOrNull("조문번호") ?: ""}"}""",
                        chunkIndex = chunkIndex++
                    )
                )
            }
        }

        log.info("Saved administrative rule: {} with {} chunks", document.title, chunkIndex)
    }

    private fun buildFullText(articles: JsonNode): String {
        val sb = StringBuilder()
        val list = if (articles.isArray) articles.toList()
            else if (articles.isObject) listOf(articles)
            else emptyList()

        for (article in list) {
            val content = article.textOrNull("조문내용") ?: continue
            sb.appendLine(content.replace(Regex("<br\\s*/?>"), "\n"))
            sb.appendLine()
        }
        return sb.toString().trim()
    }
}
