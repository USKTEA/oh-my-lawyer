package com.ohmylawyer.rag.dto

import com.ohmylawyer.domain.entity.DocumentType

data class RagRequest(
    val question: String,
    val documentTypes: List<DocumentType>? = null
)
