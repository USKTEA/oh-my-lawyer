package com.ohmylawyer.embedding.service

import com.ohmylawyer.domain.entity.CollectionProgress
import com.ohmylawyer.domain.entity.CollectionStatus
import com.ohmylawyer.domain.entity.DocumentType
import com.ohmylawyer.domain.repository.CollectionProgressRepository
import com.ohmylawyer.embedding.client.GeminiEmbeddingClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class EmbeddingService(
    private val embeddingClient: GeminiEmbeddingClient,
    private val progressRepository: CollectionProgressRepository,
    private val jdbcTemplate: JdbcTemplate,
    @Value("\${collector.embedding-batch-size:100}") private val batchSize: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val TASK_TYPE = "EMBEDDING"
    }

    @Transactional
    fun startEmbedding(dataType: DocumentType): CollectionProgress {
        val existing = progressRepository.findByTaskTypeAndDataType(TASK_TYPE, dataType)
        if (existing != null && existing.status == CollectionStatus.RUNNING) {
            log.info("Embedding task already running for {}", dataType)
            return existing
        }

        val totalCount = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM law_chunks c
            JOIN law_documents d ON c.document_id = d.id
            WHERE d.type = ?::document_type AND c.embedding IS NULL
            """,
            Int::class.java,
            dataType.name
        ) ?: 0

        if (totalCount == 0) {
            log.info("No chunks to embed for {}", dataType)
            val progress = existing ?: CollectionProgress(
                taskType = TASK_TYPE,
                dataType = dataType
            )
            progress.status = CollectionStatus.COMPLETED
            progress.totalCount = 0
            progress.processedCount = 0
            progress.completedAt = LocalDateTime.now()
            progress.updatedAt = LocalDateTime.now()
            return progressRepository.save(progress)
        }

        val progress = existing ?: CollectionProgress(
            taskType = TASK_TYPE,
            dataType = dataType
        )
        progress.status = CollectionStatus.RUNNING
        progress.totalCount = totalCount
        progress.processedCount = 0
        progress.errorMessage = null
        progress.startedAt = LocalDateTime.now()
        progress.completedAt = null
        progress.updatedAt = LocalDateTime.now()
        return progressRepository.save(progress)
    }

    fun processEmbeddings(dataType: DocumentType) {
        val progress = progressRepository.findByTaskTypeAndDataType(TASK_TYPE, dataType)
            ?: throw IllegalStateException("No embedding task found for $dataType")

        try {
            var processed = progress.processedCount
            while (true) {
                val chunks = fetchChunksWithoutEmbedding(dataType, batchSize)
                if (chunks.isEmpty()) break

                val texts = chunks.map { it.content }
                val embeddings = embeddingClient.embedBatch(texts)

                for ((chunk, embedding) in chunks.zip(embeddings)) {
                    updateEmbedding(chunk.id, embedding)
                }

                processed += chunks.size
                updateProgress(progress.id, processed)
                log.info("Embedded {}/{} chunks for {}", processed, progress.totalCount, dataType)
            }

            completeProgress(progress.id, processed)
            log.info("Embedding completed for {}: {} chunks", dataType, processed)
        } catch (e: Exception) {
            log.error("Embedding failed for {}: {}", dataType, e.message, e)
            failProgress(progress.id, e.message ?: "Unknown error")
            throw e
        }
    }

    fun getProgress(dataType: DocumentType): CollectionProgress? {
        return progressRepository.findByTaskTypeAndDataType(TASK_TYPE, dataType)
    }

    fun getAllProgress(): List<CollectionProgress> {
        return progressRepository.findAll().filter { it.taskType == TASK_TYPE }
    }

    private fun fetchChunksWithoutEmbedding(dataType: DocumentType, limit: Int): List<ChunkRow> {
        return jdbcTemplate.query(
            """
            SELECT c.id, c.content FROM law_chunks c
            JOIN law_documents d ON c.document_id = d.id
            WHERE d.type = ?::document_type AND c.embedding IS NULL
            ORDER BY c.created_at
            LIMIT ?
            """,
            { rs, _ -> ChunkRow(UUID.fromString(rs.getString("id")), rs.getString("content")) },
            dataType.name,
            limit
        )
    }

    private fun updateEmbedding(chunkId: UUID, embedding: List<Float>) {
        val vectorStr = "[${embedding.joinToString(",")}]"
        jdbcTemplate.update(
            "UPDATE law_chunks SET embedding = ?::vector WHERE id = ?",
            vectorStr,
            chunkId
        )
    }

    private fun updateProgress(progressId: UUID, processedCount: Int) {
        jdbcTemplate.update(
            "UPDATE collection_progress SET processed_count = ?, updated_at = now() WHERE id = ?",
            processedCount,
            progressId
        )
    }

    private fun completeProgress(progressId: UUID, processedCount: Int) {
        jdbcTemplate.update(
            "UPDATE collection_progress SET processed_count = ?, status = 'COMPLETED', completed_at = now(), updated_at = now() WHERE id = ?",
            processedCount,
            progressId
        )
    }

    private fun failProgress(progressId: UUID, errorMessage: String) {
        jdbcTemplate.update(
            "UPDATE collection_progress SET status = 'FAILED', error_message = ?, updated_at = now() WHERE id = ?",
            errorMessage.take(1000),
            progressId
        )
    }

    private data class ChunkRow(val id: UUID, val content: String)
}
