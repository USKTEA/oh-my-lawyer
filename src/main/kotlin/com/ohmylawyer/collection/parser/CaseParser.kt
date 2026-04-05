package com.ohmylawyer.collection.parser

import com.fasterxml.jackson.databind.JsonNode
import com.ohmylawyer.domain.entity.ChunkType
import com.ohmylawyer.domain.entity.DocumentType
import org.springframework.stereotype.Component

@Component
class CaseParser : LawApiParser {

    override fun parseTotalCount(searchResult: JsonNode): Int =
        searchResult.path("PrecSearch").path("totalCnt").asInt(0)

    override fun parseSearchItems(searchResult: JsonNode): List<JsonNode> {
        val items = searchResult.path("PrecSearch").path("prec")
        if (items.isMissingNode) return emptyList()
        return if (items.isArray) items.toList() else listOf(items)
    }

    override fun parseItemId(item: JsonNode): String? =
        item.textOrNull("판례일련번호")

    override fun parseDetail(searchItem: JsonNode, detailResponse: JsonNode): ParsedDocument {
        val node = detailResponse.path("PrecService")
        val caseName = node.textOrNull("사건명") ?: searchItem.textOrNull("사건명") ?: ""
        val caseNumber = node.textOrNull("사건번호") ?: searchItem.textOrNull("사건번호") ?: ""
        val courtName = node.textOrNull("법원명") ?: searchItem.textOrNull("법원명") ?: ""
        val decisionDate = node.textOrNull("선고일자") ?: searchItem.textOrNull("선고일자")

        val fullText = (node.textOrNull("판례내용") ?: "").stripHtmlBr()
        val holding = (node.textOrNull("판시사항") ?: "").stripHtmlBr()
        val summary = (node.textOrNull("판결요지") ?: "").stripHtmlBr()

        val chunks = mutableListOf<ParsedChunk>()
        var chunkIndex = 0

        if (holding.isNotBlank() || summary.isNotBlank()) {
            val content = buildString {
                if (holding.isNotBlank()) { appendLine("[판시사항]"); appendLine(holding); appendLine() }
                if (summary.isNotBlank()) { appendLine("[판결요지]"); appendLine(summary) }
            }.trim()
            chunks.add(ParsedChunk(content, ChunkType.SUMMARY,
                mapOf("caseNumber" to caseNumber, "courtName" to courtName).toJsonString(), chunkIndex++))
        }

        if (fullText.isNotBlank()) {
            for (section in splitCaseText(fullText)) {
                chunks.add(ParsedChunk(section, ChunkType.HOLDING,
                    mapOf("caseNumber" to caseNumber, "courtName" to courtName, "sectionIndex" to chunkIndex.toString()).toJsonString(), chunkIndex++))
            }
        }

        return ParsedDocument(
            type = DocumentType.CASE,
            title = "$caseName ($caseNumber)",
            fullText = fullText.ifBlank { "$holding\n\n$summary" },
            sourceUrl = "https://www.law.go.kr/판례/${caseName}",
            sourceId = parseItemId(searchItem) ?: "",
            metadata = mapOf(
                "caseNumber" to caseNumber,
                "courtName" to courtName,
                "caseType" to (node.textOrNull("사건종류명") ?: searchItem.textOrNull("사건종류명") ?: ""),
                "decisionType" to (node.textOrNull("판결유형") ?: ""),
                "refArticles" to (node.textOrNull("참조조문") ?: ""),
                "refCases" to (node.textOrNull("참조판례") ?: "")
            ).toJsonString(),
            enactedDate = decisionDate?.toLocalDate(),
            chunks = chunks
        )
    }

    fun splitCaseText(text: String, maxChunkSize: Int = 4000): List<String> {
        if (text.length <= maxChunkSize) return listOf(text)

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

        return sections.flatMap { section ->
            if (section.length <= maxChunkSize) listOf(section)
            else section.chunked(maxChunkSize)
        }
    }
}
