package com.ohmylawyer.search.repository

import com.ohmylawyer.domain.entity.DocumentType
import com.ohmylawyer.search.dto.SearchResult
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class SearchRepository(
    private val jdbcTemplate: JdbcTemplate
) {
    companion object {
        const val RRF_K = 60
        const val CANDIDATE_MULTIPLIER = 5
    }

    fun hybridSearch(
        queryEmbedding: List<Float>,
        queryText: String,
        topK: Int,
        documentTypes: List<DocumentType>?
    ): List<SearchResult> {
        val vectorStr = "[${queryEmbedding.joinToString(",")}]"
        val candidateLimit = topK * CANDIDATE_MULTIPLIER
        val typeFilter = if (!documentTypes.isNullOrEmpty()) {
            "AND d.type IN (${documentTypes.joinToString(",") { "'${it.name}'::document_type" }})"
        } else ""

        val sql = """
            WITH vector_matches AS (
                SELECT c.id AS chunk_id,
                       ROW_NUMBER() OVER (ORDER BY c.embedding <=> ?::vector) AS rank
                FROM law_chunks c
                JOIN law_documents d ON c.document_id = d.id
                WHERE c.embedding IS NOT NULL $typeFilter
                ORDER BY c.embedding <=> ?::vector
                LIMIT ?
            ),
            keyword_matches AS (
                SELECT c.id AS chunk_id,
                       ROW_NUMBER() OVER (ORDER BY ts_rank(c.search_vector, plainto_tsquery('simple', ?)) DESC) AS rank
                FROM law_chunks c
                JOIN law_documents d ON c.document_id = d.id
                WHERE c.search_vector @@ plainto_tsquery('simple', ?) $typeFilter
                LIMIT ?
            ),
            rrf AS (
                SELECT COALESCE(v.chunk_id, k.chunk_id) AS chunk_id,
                       COALESCE(1.0 / (? + v.rank), 0.0) AS vector_rrf,
                       COALESCE(1.0 / (? + k.rank), 0.0) AS keyword_rrf,
                       COALESCE(1.0 / (? + v.rank), 0.0) + COALESCE(1.0 / (? + k.rank), 0.0) AS rrf_score
                FROM vector_matches v
                FULL OUTER JOIN keyword_matches k ON v.chunk_id = k.chunk_id
            )
            SELECT c.id AS chunk_id,
                   d.id AS document_id,
                   d.title AS document_title,
                   d.type AS document_type,
                   c.content,
                   c.chunk_type,
                   c.metadata,
                   r.rrf_score AS score,
                   r.vector_rrf AS vector_score,
                   r.keyword_rrf AS keyword_score
            FROM rrf r
            JOIN law_chunks c ON c.id = r.chunk_id
            JOIN law_documents d ON c.document_id = d.id
            ORDER BY r.rrf_score DESC
            LIMIT ?
        """.trimIndent()

        return jdbcTemplate.query(
            sql,
            { rs, _ ->
                SearchResult(
                    chunkId = rs.getString("chunk_id"),
                    documentId = rs.getString("document_id"),
                    documentTitle = rs.getString("document_title"),
                    documentType = rs.getString("document_type"),
                    content = rs.getString("content"),
                    chunkType = rs.getString("chunk_type"),
                    score = rs.getDouble("score"),
                    vectorScore = rs.getDouble("vector_score"),
                    keywordScore = rs.getDouble("keyword_score"),
                    metadata = rs.getString("metadata")
                )
            },
            vectorStr,      // vector_matches: <=> compare
            vectorStr,      // vector_matches: ORDER BY
            candidateLimit, // vector_matches: LIMIT
            queryText,      // keyword_matches: plainto_tsquery
            queryText,      // keyword_matches: WHERE
            candidateLimit, // keyword_matches: LIMIT
            RRF_K,          // rrf: vector_rrf
            RRF_K,          // rrf: keyword_rrf
            RRF_K,          // rrf: rrf_score (vector part)
            RRF_K,          // rrf: rrf_score (keyword part)
            topK            // final LIMIT
        )
    }
}
