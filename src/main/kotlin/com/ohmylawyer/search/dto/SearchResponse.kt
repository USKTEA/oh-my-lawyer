package com.ohmylawyer.search.dto

data class SearchResponse(
    val query: String,
    val results: List<SearchResult>,
    val totalCount: Int
)

data class SearchResult(
    val chunkId: String,
    val documentId: String,
    val documentTitle: String,
    val documentType: String,
    val content: String,
    val chunkType: String,
    val score: Double,
    val vectorScore: Double,
    val keywordScore: Double,
    val metadata: String
)
