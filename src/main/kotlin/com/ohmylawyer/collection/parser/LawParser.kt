package com.ohmylawyer.collection.parser

import com.fasterxml.jackson.databind.JsonNode
import com.ohmylawyer.domain.entity.ChunkType
import com.ohmylawyer.domain.entity.DocumentType
import org.springframework.stereotype.Component

@Component
class LawParser : LawApiParser {
    override fun parseTotalCount(searchResult: JsonNode): Int = searchResult.path("LawSearch").path("totalCnt").asInt(0)

    override fun parseSearchItems(searchResult: JsonNode): List<JsonNode> {
        val items = searchResult.path("LawSearch").path("law")
        if (items.isMissingNode) return emptyList()
        return if (items.isArray) items.toList() else listOf(items)
    }

    override fun parseItemId(item: JsonNode): String? = item.textOrNull("법령일련번호")

    override fun parseDetail(
        searchItem: JsonNode,
        detailResponse: JsonNode,
    ): ParsedDocument {
        val lawNode = detailResponse.path("법령")
        val basicInfo = lawNode.path("기본정보")
        val lawName =
            basicInfo.textOrNull("법령명_한글")
                ?: searchItem.textOrNull("법령명한글") ?: ""

        val articles = lawNode.path("조문").path("조문단위")
        val chunks = buildChunks(articles)

        return ParsedDocument(
            type = DocumentType.LAW,
            title = lawName,
            fullText = chunks.joinToString("\n\n") { it.content },
            sourceUrl = "https://www.law.go.kr/법령/$lawName",
            sourceId = parseItemId(searchItem) ?: "",
            metadata =
                mapOf(
                    "lawId" to (searchItem.textOrNull("법령ID") ?: ""),
                    "lawSerialNo" to (searchItem.textOrNull("법령일련번호") ?: ""),
                    "lawStatus" to (searchItem.textOrNull("현행연혁코드") ?: ""),
                    "lawNameAbbr" to (basicInfo.textOrNull("법령명_약칭") ?: searchItem.textOrNull("법령약칭명") ?: ""),
                    "lawType" to (basicInfo.textOrNull("법종구분") ?: searchItem.textOrNull("법령구분명") ?: ""),
                    "ministry" to (basicInfo.textOrNull("소관부처명") ?: searchItem.textOrNull("소관부처명") ?: ""),
                ).toJsonString(),
            enactedDate = basicInfo.textOrNull("시행일자")?.toLocalDate(),
            lastAmended = basicInfo.textOrNull("공포일자")?.toLocalDate(),
            chunks = chunks,
        )
    }

    private fun buildChunks(articles: JsonNode): List<ParsedChunk> {
        if (!articles.isArray) return emptyList()
        return articles
            .mapIndexed { index, article ->
                val content = buildArticleContent(article)
                if (content.isBlank()) {
                    null
                } else {
                    ParsedChunk(
                        content = content,
                        chunkType = ChunkType.ARTICLE,
                        metadata =
                            mapOf(
                                "articleNum" to (article.textOrNull("조문번호") ?: ""),
                                "articleTitle" to (article.textOrNull("조문제목") ?: ""),
                            ).toJsonString(),
                        chunkIndex = index,
                    )
                }
            }.filterNotNull()
    }

    fun buildArticleContent(article: JsonNode): String {
        val sb = StringBuilder()
        val articleNum = article.textOrNull("조문번호") ?: ""
        val articleTitle = article.textOrNull("조문제목") ?: ""
        val articleContent = article.textOrNull("조문내용") ?: ""

        if (articleTitle.isNotBlank()) {
            sb.appendLine("제${articleNum}조($articleTitle)")
        } else if (articleNum.isNotBlank()) {
            sb.appendLine("제${articleNum}조")
        }
        if (articleContent.isNotBlank()) {
            sb.appendLine(articleContent)
        }

        val paragraphs = article.path("항")
        if (paragraphs.isArray) {
            for (para in paragraphs) {
                val paraContent = para.textOrNull("항내용") ?: continue
                sb.appendLine(paraContent)

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
}
