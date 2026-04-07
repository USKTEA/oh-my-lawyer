package com.ohmylawyer.collection.dto

import com.ohmylawyer.domain.entity.CollectionStatus
import com.ohmylawyer.domain.entity.DocumentType
import java.time.LocalDateTime

data class CollectionCommandResponse(
    val status: CollectionStatus,
    val dataType: DocumentType,
)

data class CollectionStatusResponse(
    val dataType: DocumentType,
    val status: CollectionStatus,
    val totalCount: Int,
    val processedCount: Int,
    val lastCursor: String?,
    val errorMessage: String?,
    val startedAt: LocalDateTime?,
    val completedAt: LocalDateTime?,
)
