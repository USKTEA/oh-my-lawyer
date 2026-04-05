package com.ohmylawyer.collection.parser

import com.fasterxml.jackson.databind.JsonNode
import com.ohmylawyer.domain.entity.ChunkType
import com.ohmylawyer.domain.entity.DocumentType
import org.springframework.stereotype.Component

@Component
class ConstitutionalParser : LawApiParser {

    override fun parseTotalCount(searchResult: JsonNode): Int =
        searchResult.path("DetcSearch").path("totalCnt").asInt(0)

    override fun parseSearchItems(searchResult: JsonNode): List<JsonNode> {
        val items = searchResult.path("DetcSearch").path("Detc")
        if (items.isMissingNode) return emptyList()
        return if (items.isArray) items.toList() else listOf(items)
    }

    override fun parseItemId(item: JsonNode): String? =
        item.textOrNull("헌재결정례일련번호")

    override fun parseDetail(searchItem: JsonNode, detailResponse: JsonNode): ParsedDocument {
        val node = detailResponse.path("DetcService")
        val caseName = node.textOrNull("사건명") ?: searchItem.textOrNull("사건명") ?: ""
        val caseNumber = node.textOrNull("사건번호") ?: searchItem.textOrNull("사건번호") ?: ""
        val decisionDate = node.textOrNull("종국일자") ?: searchItem.textOrNull("종국일자")

        val fullText = (node.textOrNull("전문") ?: "").stripHtmlBr()
        val holding = (node.textOrNull("판시사항") ?: "").stripHtmlBr()
        val summary = (node.textOrNull("결정요지") ?: "").stripHtmlBr()

        val chunks = mutableListOf<ParsedChunk>()
        var chunkIndex = 0

        if (holding.isNotBlank() || summary.isNotBlank()) {
            val content = buildString {
                if (holding.isNotBlank()) { appendLine("[판시사항]"); appendLine(holding); appendLine() }
                if (summary.isNotBlank()) { appendLine("[결정요지]"); appendLine(summary) }
            }.trim()
            chunks.add(ParsedChunk(content, ChunkType.SUMMARY,
                mapOf("caseNumber" to caseNumber).toJsonString(), chunkIndex++))
        }

        if (fullText.isNotBlank()) {
            for (section in fullText.chunked(4000)) {
                chunks.add(ParsedChunk(section, ChunkType.HOLDING,
                    mapOf("caseNumber" to caseNumber, "sectionIndex" to chunkIndex.toString()).toJsonString(), chunkIndex++))
            }
        }

        return ParsedDocument(
            type = DocumentType.CONSTITUTIONAL,
            title = "$caseName ($caseNumber)",
            fullText = fullText.ifBlank { "$holding\n\n$summary" },
            sourceUrl = "https://www.law.go.kr/헌재결정례/${caseName}",
            sourceId = parseItemId(searchItem) ?: "",
            metadata = mapOf(
                "caseNumber" to caseNumber,
                "caseType" to (node.textOrNull("사건종류명") ?: ""),
                "refArticles" to (node.textOrNull("참조조문") ?: ""),
                "refCases" to (node.textOrNull("참조판례") ?: ""),
                "targetArticles" to (node.textOrNull("심판대상조문") ?: "")
            ).toJsonString(),
            enactedDate = decisionDate?.toLocalDate(),
            chunks = chunks
        )
    }
}
