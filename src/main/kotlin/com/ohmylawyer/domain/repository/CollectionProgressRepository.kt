package com.ohmylawyer.domain.repository

import com.ohmylawyer.domain.entity.CollectionProgress
import com.ohmylawyer.domain.entity.DocumentType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CollectionProgressRepository : JpaRepository<CollectionProgress, UUID> {
    fun findByTaskTypeAndDataType(
        taskType: String,
        dataType: DocumentType,
    ): CollectionProgress?
}
