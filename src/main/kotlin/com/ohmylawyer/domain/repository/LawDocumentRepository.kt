package com.ohmylawyer.domain.repository

import com.ohmylawyer.domain.entity.DocumentType
import com.ohmylawyer.domain.entity.LawDocument
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LawDocumentRepository : JpaRepository<LawDocument, UUID> {
    fun findByTypeAndSourceId(
        type: DocumentType,
        sourceId: String,
    ): LawDocument?

    fun findAllByType(type: DocumentType): List<LawDocument>

    fun countByType(type: DocumentType): Long
}
