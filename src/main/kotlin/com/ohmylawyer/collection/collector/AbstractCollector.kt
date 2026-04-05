package com.ohmylawyer.collection.collector

import com.ohmylawyer.collection.client.LawApiClient
import com.ohmylawyer.collection.client.LawApiProperties

import com.fasterxml.jackson.databind.JsonNode
import com.ohmylawyer.collection.parser.LawApiParser
import com.ohmylawyer.domain.entity.CollectionStatus
import com.ohmylawyer.domain.entity.*
import com.ohmylawyer.domain.repository.CollectionProgressRepository
import com.ohmylawyer.domain.repository.LawDocumentRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.Logger
import java.time.LocalDateTime

/**
 * Base class for data collectors. Handles pagination, progress tracking, and error recovery.
 * Uses coroutines with Semaphore for concurrent detail fetching.
 * Delegates all parsing to LawApiParser implementations.
 */
abstract class AbstractCollector(
    private val progressRepository: CollectionProgressRepository,
    private val lawDocumentRepository: LawDocumentRepository,
    private val documentPersistenceService: com.ohmylawyer.collection.service.DocumentPersistenceService,
    private val props: LawApiProperties
) {
    abstract val log: Logger
    abstract val dataType: DocumentType
    abstract val parser: LawApiParser

    val taskType: String get() = "COLLECT_${dataType.name}"

    private val concurrency = 3
    private val semaphore = Semaphore(concurrency)

    abstract fun doSearch(query: String?, page: Int): JsonNode
    abstract fun fetchDetail(id: String): JsonNode

    open fun collect(query: String? = null) {
        val progress = getOrCreateProgress()
        if (progress.status == CollectionStatus.COMPLETED) {
            log.info("[{}] Already completed. Skipping.", taskType)
            return
        }

        progress.status = CollectionStatus.RUNNING
        progress.startedAt = progress.startedAt ?: LocalDateTime.now()
        progress.updatedAt = LocalDateTime.now()
        progressRepository.save(progress)

        try {
            val startPage = if (progress.lastCursor != null) progress.lastCursor!!.toInt() else 1
            runBlocking(Dispatchers.IO) {
                collectPages(query, startPage, progress)
            }

            progress.status = CollectionStatus.COMPLETED
            progress.completedAt = LocalDateTime.now()
            log.info("[{}] Completed. Total processed: {}/{}", taskType, progress.processedCount, progress.totalCount)
        } catch (e: Exception) {
            progress.status = CollectionStatus.FAILED
            progress.errorMessage = e.message?.take(1000)
            log.error("[{}] Failed at page {}. Processed {}/{}", taskType, progress.lastCursor, progress.processedCount, progress.totalCount, e)
        } finally {
            progress.updatedAt = LocalDateTime.now()
            progressRepository.save(progress)
        }
    }

    private suspend fun collectPages(query: String?, startPage: Int, progress: CollectionProgress) {
        var page = startPage
        var totalCount: Int? = null

        while (true) {
            log.info("[{}] Fetching page {} ...", taskType, page)
            val searchResult = withContext(Dispatchers.IO) { doSearch(query, page) }

            if (totalCount == null) {
                totalCount = parser.parseTotalCount(searchResult)
                progress.totalCount = totalCount
                log.info("[{}] Total count: {}", taskType, totalCount)
            }

            val items = parser.parseSearchItems(searchResult)
            if (items.isEmpty()) {
                log.info("[{}] No more items at page {}", taskType, page)
                break
            }

            val results = supervisorScope {
                items.map { item ->
                    async {
                        semaphore.withPermit {
                            withContext(Dispatchers.IO) { processItem(item) }
                        }
                    }
                }.awaitAll()
            }

            progress.processedCount = withContext(Dispatchers.IO) {
                lawDocumentRepository.countByType(dataType).toInt()
            }

            progress.lastCursor = page.toString()
            progress.updatedAt = LocalDateTime.now()
            withContext(Dispatchers.IO) { progressRepository.save(progress) }

            if (progress.processedCount >= progress.totalCount) break
            page++

            delay(props.requestDelayMs)
        }
    }

    private fun processItem(item: JsonNode): Boolean {
        val id = parser.parseItemId(item) ?: return false

        if (lawDocumentRepository.findByTypeAndSourceId(dataType, id) != null) {
            log.debug("[{}] Already exists: {}", taskType, id)
            return false
        }

        return try {
            log.info("[{}] Fetching detail: {}", taskType, id)
            val detail = fetchDetail(id)
            val parsed = parser.parseDetail(item, detail)

            documentPersistenceService.saveDocument(parsed)

            log.info("[{}] Saved: {} with {} chunks", taskType, parsed.title, parsed.chunks.size)
            true
        } catch (e: Exception) {
            log.warn("[{}] Failed to process item {}: {}", taskType, id, e.message)
            false
        }
    }

    private fun getOrCreateProgress(): CollectionProgress {
        return progressRepository.findByTaskTypeAndDataType(taskType, dataType)
            ?: progressRepository.save(CollectionProgress(taskType = taskType, dataType = dataType))
    }

    open fun resetProgress() {
        progressRepository.findByTaskTypeAndDataType(taskType, dataType)?.let {
            it.status = CollectionStatus.PENDING
            it.processedCount = 0
            it.totalCount = 0
            it.lastCursor = null
            it.errorMessage = null
            it.startedAt = null
            it.completedAt = null
            it.updatedAt = LocalDateTime.now()
            progressRepository.save(it)
        }
    }
}
