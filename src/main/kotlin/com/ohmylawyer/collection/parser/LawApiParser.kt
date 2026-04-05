package com.ohmylawyer.collection.parser

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

interface LawApiParser {
    fun parseTotalCount(searchResult: JsonNode): Int
    fun parseSearchItems(searchResult: JsonNode): List<JsonNode>
    fun parseItemId(item: JsonNode): String?
    fun parseDetail(searchItem: JsonNode, detailResponse: JsonNode): ParsedDocument
}

// -- Shared extension helpers --

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
    } catch (_: Exception) {
        null
    }
}

fun String.stripHtmlBr(): String = this.replace(Regex("<br\\s*/?>"), "\n")

fun Map<String, String>.toJsonString(): String {
    val filtered = this.filter { it.value.isNotBlank() }
    if (filtered.isEmpty()) return "{}"
    return filtered.entries.joinToString(",", "{", "}") { (k, v) ->
        "\"$k\":\"${v.replace("\"", "\\\"")}\""
    }
}
