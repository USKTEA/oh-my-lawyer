package com.ohmylawyer.collection.parser

import com.fasterxml.jackson.databind.JsonNode
import com.ohmylawyer.domain.entity.ChunkType
import com.ohmylawyer.domain.entity.DocumentType
import org.springframework.stereotype.Component

@Component
class InterpretationParser : LawApiParser {

    override fun parseTotalCount(searchResult: JsonNode): Int =
        searchResult.path("Expc").path("totalCnt").asInt(0)

    override fun parseSearchItems(searchResult: JsonNode): List<JsonNode> {
        val items = searchResult.path("Expc").path("expc")
        if (items.isMissingNode) return emptyList()
        return if (items.isArray) items.toList() else listOf(items)
    }

    override fun parseItemId(item: JsonNode): String? =
        item.textOrNull("법령해석례일련번호")

    override fun parseDetail(searchItem: JsonNode, detailResponse: JsonNode): ParsedDocument {
        val node = detailResponse.path("ExpcService")
        val title = node.textOrNull("안건명") ?: searchItem.textOrNull("안건명") ?: ""
        val caseNumber = node.textOrNull("안건번호") ?: searchItem.textOrNull("안건번호") ?: ""
        val interpretDate = node.textOrNull("해석일자") ?: searchItem.textOrNull("회신일자")

        val question = (node.textOrNull("질의요지") ?: "").stripHtmlBr()
        val answer = (node.textOrNull("회답") ?: "").stripHtmlBr()
        val reason = (node.textOrNull("이유") ?: "").stripHtmlBr()

        val fullText = buildString {
            if (question.isNotBlank()) { appendLine("[질의요지]"); appendLine(question); appendLine() }
            if (answer.isNotBlank()) { appendLine("[회답]"); appendLine(answer); appendLine() }
            if (reason.isNotBlank()) { appendLine("[이유]"); appendLine(reason) }
        }.trim()

        val chunks = if (fullText.isNotBlank()) {
            listOf(ParsedChunk(fullText, ChunkType.INTERPRETATION_BODY,
                mapOf("caseNumber" to caseNumber).toJsonString(), 0))
        } else emptyList()

        return ParsedDocument(
            type = DocumentType.INTERPRETATION,
            title = "$title ($caseNumber)",
            fullText = fullText,
            sourceId = parseItemId(searchItem) ?: "",
            metadata = mapOf(
                "caseNumber" to caseNumber,
                "interpretOrg" to (node.textOrNull("해석기관명") ?: ""),
                "questionOrg" to (node.textOrNull("질의기관명") ?: "")
            ).toJsonString(),
            enactedDate = interpretDate?.toLocalDate(),
            chunks = chunks
        )
    }
}
