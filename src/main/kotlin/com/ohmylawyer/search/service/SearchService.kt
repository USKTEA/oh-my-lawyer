package com.ohmylawyer.search.service

import com.ohmylawyer.embedding.client.GeminiEmbeddingClient
import com.ohmylawyer.embedding.client.GeminiEmbeddingClient.TaskType
import com.ohmylawyer.search.dto.SearchRequest
import com.ohmylawyer.search.dto.SearchResponse
import com.ohmylawyer.search.dto.SearchResult
import com.ohmylawyer.search.repository.SearchRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class SearchService(
    private val embeddingClient: GeminiEmbeddingClient,
    private val searchRepository: SearchRepository,
    private val queryRewriteService: QueryRewriteService,
    @param:Value("\${search.top-k:10}") private val defaultTopK: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun search(request: SearchRequest): SearchResponse {
        log.info("Searching for: {}", request.query)

        val topK = request.topK ?: defaultTopK
        val rewrittenQueries = queryRewriteService.rewrite(request.query)

        val allResults = runBlocking {
            rewrittenQueries.map { query ->
                async {
                    val queryEmbedding = embeddingClient.embed(query, TaskType.RETRIEVAL_QUERY)
                    searchRepository.hybridSearch(
                        queryEmbedding = queryEmbedding,
                        queryText = query,
                        topK = topK,
                        documentTypes = request.documentTypes
                    )
                }
            }.flatMap { it.await() }
        }

        val mergedResults = mergeResults(allResults, topK)

        log.info(
            "Found {} results for query: {} (rewritten: {})",
            mergedResults.size, request.query, rewrittenQueries
        )

        return SearchResponse(
            query = request.query,
            results = mergedResults,
            totalCount = mergedResults.size
        )
    }

    private fun mergeResults(results: List<SearchResult>, topK: Int): List<SearchResult> {
        return results
            .groupBy { it.chunkId }
            .map { (_, duplicates) -> duplicates.maxBy { it.score } }
            .sortedByDescending { it.score }
            .take(topK)
    }
}
