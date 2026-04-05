package com.ohmylawyer.collection.service

import com.ohmylawyer.collection.parser.ParsedDocument
import com.ohmylawyer.domain.entity.LawChunk
import com.ohmylawyer.domain.entity.LawDocument
import com.ohmylawyer.domain.repository.LawChunkRepository
import com.ohmylawyer.domain.repository.LawDocumentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Handles document persistence in isolated transactions.
 * Each save runs in its own transaction so a duplicate key error
 * doesn't corrupt the JPA session for subsequent operations.
 */
@Service
class DocumentPersistenceService(
    private val lawDocumentRepository: LawDocumentRepository,
    private val lawChunkRepository: LawChunkRepository
) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun saveDocument(parsed: ParsedDocument): LawDocument {
        val document = lawDocumentRepository.save(
            LawDocument(
                type = parsed.type,
                title = parsed.title,
                fullText = parsed.fullText,
                sourceUrl = parsed.sourceUrl,
                sourceId = parsed.sourceId,
                metadata = parsed.metadata,
                enactedDate = parsed.enactedDate,
                lastAmended = parsed.lastAmended
            )
        )

        for (chunk in parsed.chunks) {
            lawChunkRepository.save(
                LawChunk(
                    document = document,
                    content = chunk.content,
                    chunkType = chunk.chunkType,
                    metadata = chunk.metadata,
                    chunkIndex = chunk.chunkIndex
                )
            )
        }

        return document
    }
}
