package com.ohmylawyer.collector

import com.fasterxml.jackson.databind.JsonNode
import com.ohmylawyer.domain.entity.*
import com.ohmylawyer.domain.repository.CollectionProgressRepository
import com.ohmylawyer.domain.repository.LawChunkRepository
import com.ohmylawyer.domain.repository.LawDocumentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 법령 수집기 (target=eflaw).
 * 법제처 Open API에서 법령 목록을 조회하고, 각 법령의 본문을 가져와 조문 단위로 chunking.
 */
@Service
class LawCollector(
    progressRepository: CollectionProgressRepository,
    private val apiClient: LawApiClient,
    props: LawApiProperties,
    private val lawDocumentRepository: LawDocumentRepository,
    private val lawChunkRepository: LawChunkRepository
) : AbstractCollector(progressRepository, apiClient, props) {

    override val log = LoggerFactory.getLogger(javaClass)
    override val taskType = "COLLECT_LAW"
    override val dataType = DocumentType.LAW

    override fun doSearch(query: String?, page: Int): JsonNode {
        return apiClient.searchLaws(query, page)
    }

    override fun parseTotalCount(searchResult: JsonNode): Int {
        return searchResult.path("LawSearch").path("totalCnt").asInt(0)
    }

    override fun parseSearchItems(searchResult: JsonNode): List<JsonNode> {
        val items = searchResult.path("LawSearch").path("law")
        if (items.isMissingNode) return emptyList()
        // API returns single object (not array) when only 1 result
        return if (items.isArray) items.toList() else listOf(items)
    }

    @Transactional
    override fun processItem(item: JsonNode) {
        val lawId = item.textOrNull("법령ID") ?: return
        val lawName = item.textOrNull("법령명한글") ?: item.textOrNull("법령명_한글") ?: ""

        // Skip if already collected
        if (lawDocumentRepository.findByTypeAndSourceId(DocumentType.LAW, lawId) != null) {
            log.debug("Law already exists: {} ({})", lawName, lawId)
            return
        }

        log.info("Fetching law detail: {} ({})", lawName, lawId)
        val detail = apiClient.getLawDetail(lawId)

        val lawNode = detail.path("법령")
        val basicInfo = lawNode.path("기본정보")

        val fullText = buildFullText(lawNode)
        val metadata = buildMetadata(basicInfo)

        val document = lawDocumentRepository.save(
            LawDocument(
                type = DocumentType.LAW,
                title = basicInfo.textOrNull("법령명_한글") ?: lawName,
                fullText = fullText,
                sourceUrl = "https://www.law.go.kr/법령/${lawName}",
                sourceId = lawId,
                metadata = metadata,
                enactedDate = basicInfo.textOrNull("시행일자")?.toLocalDate(),
                lastAmended = basicInfo.textOrNull("공포일자")?.toLocalDate()
            )
        )

        // Chunk by articles (조문 단위)
        val articles = lawNode.path("조문").path("조문단위")
        if (articles.isArray) {
            articles.forEachIndexed { index, article ->
                val content = buildArticleContent(article)
                if (content.isNotBlank()) {
                    lawChunkRepository.save(
                        LawChunk(
                            document = document,
                            content = content,
                            chunkType = ChunkType.ARTICLE,
                            metadata = buildArticleMetadata(article),
                            chunkIndex = index
                        )
                    )
                }
            }
        }

        log.info("Saved law: {} with {} chunks", document.title, articles.size())
    }

    private fun buildFullText(lawNode: JsonNode): String {
        val sb = StringBuilder()
        val articles = lawNode.path("조문").path("조문단위")
        if (articles.isArray) {
            for (article in articles) {
                sb.appendLine(buildArticleContent(article))
                sb.appendLine()
            }
        }
        return sb.toString().trim()
    }

    private fun buildArticleContent(article: JsonNode): String {
        val sb = StringBuilder()
        val articleNum = article.textOrNull("조문번호") ?: ""
        val articleTitle = article.textOrNull("조문제목") ?: ""
        val articleContent = article.textOrNull("조문내용") ?: ""

        if (articleTitle.isNotBlank()) {
            sb.appendLine("제${articleNum}조(${articleTitle})")
        } else if (articleNum.isNotBlank()) {
            sb.appendLine("제${articleNum}조")
        }
        if (articleContent.isNotBlank()) {
            sb.appendLine(articleContent)
        }

        // 항 (paragraphs)
        val paragraphs = article.path("항")
        if (paragraphs.isArray) {
            for (para in paragraphs) {
                val paraContent = para.textOrNull("항내용") ?: continue
                sb.appendLine(paraContent)

                // 호 (sub-items)
                val subItems = para.path("호")
                if (subItems.isArray) {
                    for (sub in subItems) {
                        val subContent = sub.textOrNull("호내용") ?: continue
                        sb.appendLine("  $subContent")
                    }
                }
            }
        }

        return sb.toString().trim()
    }

    private fun buildMetadata(basicInfo: JsonNode): String {
        val fields = mapOf(
            "lawNameAbbr" to (basicInfo.textOrNull("법령명_약칭") ?: ""),
            "lawType" to (basicInfo.textOrNull("법종구분") ?: ""),
            "ministry" to (basicInfo.textOrNull("소관부처명") ?: ""),
            "lawStatus" to (basicInfo.textOrNull("현행연혁코드") ?: "")
        ).filter { it.value.isNotBlank() }

        return com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(fields)
    }

    private fun buildArticleMetadata(article: JsonNode): String {
        val fields = mapOf(
            "articleNum" to (article.textOrNull("조문번호") ?: ""),
            "articleTitle" to (article.textOrNull("조문제목") ?: "")
        ).filter { it.value.isNotBlank() }

        return com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(fields)
    }
}

// -- Extension helpers --

fun JsonNode.textOrNull(field: String): String? {
    val node = this.path(field)
    if (node.isMissingNode || node.isNull) return null
    val text = node.asText()
    return if (text.isBlank() || text == "null") null else text
}

private val DATE_FORMAT_8 = DateTimeFormatter.ofPattern("yyyyMMdd")

fun String.toLocalDate(): LocalDate? {
    return try {
        LocalDate.parse(this.take(8), DATE_FORMAT_8)
    } catch (e: Exception) {
        null
    }
}
