package com.ohmylawyer.domain.repository

import com.ohmylawyer.domain.entity.LawChunk
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LawChunkRepository : JpaRepository<LawChunk, UUID> {
    fun findAllByDocumentId(documentId: UUID): List<LawChunk>
    fun deleteAllByDocumentId(documentId: UUID)
}
