package com.ohmylawyer.collection.parser

import com.fasterxml.jackson.databind.JsonNode
import com.ohmylawyer.domain.entity.ChunkType
import com.ohmylawyer.domain.entity.DocumentType
import org.springframework.stereotype.Component

@Component
class AdministrativeRuleParser : LawApiParser {

    override fun parseTotalCount(searchResult: JsonNode): Int =
        searchResult.path("AdmRulSearch").path("totalCnt").asInt(0)

    override fun parseSearchItems(searchResult: JsonNode): List<JsonNode> {
        val items = searchResult.path("AdmRulSearch").path("admrul")
        if (items.isMissingNode) return emptyList()
        return if (items.isArray) items.toList() else listOf(items)
    }

    override fun parseItemId(item: JsonNode): String? =
        item.textOrNull("행정규칙일련번호")

    override fun parseDetail(searchItem: JsonNode, detailResponse: JsonNode): ParsedDocument {
        val node = detailResponse.path("AdmRulService")
        val title = searchItem.textOrNull("행정규칙명") ?: ""
        val enactedDate = searchItem.textOrNull("시행일자")
        val articles = node.path("조문내용")

        val chunks = buildChunks(articles)

        return ParsedDocument(
            type = DocumentType.ADMINISTRATIVE_RULE,
            title = title,
            fullText = chunks.joinToString("\n\n") { it.content },
            sourceId = parseItemId(searchItem) ?: "",
            metadata = mapOf(
                "ruleType" to (searchItem.textOrNull("행정규칙종류") ?: ""),
                "ministry" to (searchItem.textOrNull("소관부처명") ?: ""),
                "ruleId" to (searchItem.textOrNull("행정규칙ID") ?: ""),
                "amendType" to (searchItem.textOrNull("제개정구분명") ?: "")
            ).toJsonString(),
            enactedDate = enactedDate?.toLocalDate(),
            chunks = chunks
        )
    }

    private fun buildChunks(articles: JsonNode): List<ParsedChunk> {
        val list = when {
            articles.isArray -> articles.toList()
            articles.isObject -> listOf(articles)
            else -> emptyList()
        }

        return list.mapIndexedNotNull { index, article ->
            val content = (article.textOrNull("조문내용") ?: article.toString()).stripHtmlBr()
            if (content.isBlank() || content == "{}") null
            else ParsedChunk(
                content = content,
                chunkType = ChunkType.ARTICLE,
                metadata = mapOf("articleNum" to (article.textOrNull("조문번호") ?: "")).toJsonString(),
                chunkIndex = index
            )
        }
    }
}
