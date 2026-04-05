package com.ohmylawyer.search.service

import com.ohmylawyer.embedding.client.GeminiEmbeddingClient
import com.ohmylawyer.embedding.client.GeminiEmbeddingClient.TaskType
import com.ohmylawyer.search.dto.SearchRequest
import com.ohmylawyer.search.dto.SearchResponse
import com.ohmylawyer.search.repository.SearchRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class SearchService(
    private val embeddingClient: GeminiEmbeddingClient,
    private val searchRepository: SearchRepository,
    @Value("\${search.top-k:10}") private val defaultTopK: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun search(request: SearchRequest): SearchResponse {
        log.info("Searching for: {}", request.query)

        val queryEmbedding = embeddingClient.embed(request.query, TaskType.RETRIEVAL_QUERY)
        val topK = request.topK ?: defaultTopK

        val results = searchRepository.hybridSearch(
            queryEmbedding = queryEmbedding,
            queryText = request.query,
            topK = topK,
            documentTypes = request.documentTypes
        )

        log.info("Found {} results for query: {}", results.size, request.query)

        return SearchResponse(
            query = request.query,
            results = results,
            totalCount = results.size
        )
    }
}
