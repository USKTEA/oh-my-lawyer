package com.ohmylawyer.search.dto

import com.ohmylawyer.domain.entity.DocumentType

data class SearchRequest(
    val query: String,
    val documentTypes: List<DocumentType>? = null,
    val topK: Int? = null,
)
