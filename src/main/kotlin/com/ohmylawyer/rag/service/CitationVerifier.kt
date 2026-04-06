package com.ohmylawyer.rag.service

import com.ohmylawyer.rag.dto.Citation
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class CitationVerifier(
    private val jdbcTemplate: JdbcTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun verify(citations: List<RagService.LlmCitation>): List<Citation> {
        return citations.map { citation ->
            val exists = existsInDb(citation.source)
            if (!exists) {
                log.warn("Citation not verified: {}", citation.source)
            }
            Citation(
                source = citation.source,
                content = citation.content,
                existsInDb = exists
            )
        }
    }

    private fun existsInDb(source: String): Boolean {
        // extract law name or case number from source
        val searchTerms = extractSearchTerms(source)
        if (searchTerms.first.isBlank()) return false

        // check law_documents title or law_chunks content (for LEGAL_OPINION)
        val count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM (
                SELECT 1 FROM law_documents WHERE title ILIKE ? OR title ILIKE ?
                UNION ALL
                SELECT 1 FROM law_chunks WHERE content ILIKE ? LIMIT 1
            ) matches
            """,
            Long::class.java,
            "%${searchTerms.first}%",
            "%${searchTerms.second}%",
            "%${searchTerms.first}%"
        ) ?: 0

        return count > 0
    }

    companion object {
        // "개인정보보호법 제18조 제2항" → ("개인정보보호법", "개인정보 보호법")
        // "2012다105482" → ("2012다105482", "2012다105482")
        internal fun extractSearchTerms(source: String): Pair<String, String> {
            val trimmed = source.trim()
            if (trimmed.isBlank()) return Pair("", "")

            // case number pattern: e.g. "2012다105482", "99누12894"
            val caseNumberRegex = Regex("""(\d{2,4}[가-힣]+\d+)""")
            val caseMatch = caseNumberRegex.find(trimmed)
            if (caseMatch != null) {
                return Pair(caseMatch.value, caseMatch.value)
            }

            // law name: take the first part before 제X조
            val lawNameRegex = Regex("""^(.+?)(?:\s*제\d+조.*)?$""")
            val lawMatch = lawNameRegex.find(trimmed)
            val lawName = lawMatch?.groupValues?.get(1)?.trim() ?: trimmed

            // some law names have spaces, some don't: "개인정보보호법" vs "개인정보 보호법"
            val withoutSpaces = lawName.replace(" ", "")
            return Pair(lawName, withoutSpaces)
        }
    }
}
