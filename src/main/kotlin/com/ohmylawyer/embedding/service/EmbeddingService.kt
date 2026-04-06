package com.ohmylawyer.embedding.service

import com.ohmylawyer.embedding.client.GeminiEmbeddingClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@Service
class EmbeddingService(
    private val embeddingClient: GeminiEmbeddingClient,
    private val jdbcTemplate: JdbcTemplate,
    @Value("\${collector.embedding-batch-size:150}") private val batchSize: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val runningAsc = AtomicBoolean(false)
    private val runningDesc = AtomicBoolean(false)
    private val totalProcessed = AtomicLong(0)

    @Scheduled(fixedDelayString = "\${collector.embedding-interval-ms:3500}")
    fun processBatchAsc() {
        processBatch(runningAsc, "ASC")
    }

    @Scheduled(fixedDelayString = "\${collector.embedding-interval-ms:3500}", initialDelay = 1750)
    fun processBatchDesc() {
        processBatch(runningDesc, "DESC")
    }

    private fun processBatch(guard: AtomicBoolean, order: String) {
        if (!guard.compareAndSet(false, true)) return

        try {
            val chunks = fetchChunksWithoutEmbedding(batchSize, order)
            if (chunks.isEmpty()) return

            val texts = chunks.map { it.content }
            val embeddings = embeddingClient.embedBatch(texts)

            for ((chunk, embedding) in chunks.zip(embeddings)) {
                updateEmbedding(chunk.id, embedding)
            }

            val processed = totalProcessed.addAndGet(chunks.size.toLong())
            if (processed % 1000 == 0L || chunks.size < batchSize) {
                val remaining = countRemainingChunks()
                log.info("Embedding progress [{}]: {} processed, {} remaining", order, processed, remaining)
            }
        } catch (e: Exception) {
            log.error("Embedding batch [{}] failed: {}", order, e.message, e)
        } finally {
            guard.set(false)
        }
    }

    fun getStatus(): EmbeddingStatus {
        val remaining = countRemainingChunks()
        val total = countTotalChunks()
        val embedded = total - remaining
        return EmbeddingStatus(
            totalChunks = total,
            embeddedChunks = embedded,
            remainingChunks = remaining,
            sessionProcessed = totalProcessed.get(),
            isRunning = runningAsc.get() || runningDesc.get()
        )
    }

    private fun fetchChunksWithoutEmbedding(limit: Int, order: String): List<ChunkRow> {
        return jdbcTemplate.query(
            "SELECT id, content FROM law_chunks WHERE embedding IS NULL ORDER BY created_at $order LIMIT ?",
            { rs, _ -> ChunkRow(UUID.fromString(rs.getString("id")), rs.getString("content")) },
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

    private fun countRemainingChunks(): Long {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM law_chunks WHERE embedding IS NULL",
            Long::class.java
        ) ?: 0
    }

    private fun countTotalChunks(): Long {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM law_chunks",
            Long::class.java
        ) ?: 0
    }

    private data class ChunkRow(val id: UUID, val content: String)

    data class EmbeddingStatus(
        val totalChunks: Long,
        val embeddedChunks: Long,
        val remainingChunks: Long,
        val sessionProcessed: Long,
        val isRunning: Boolean
    )
}
