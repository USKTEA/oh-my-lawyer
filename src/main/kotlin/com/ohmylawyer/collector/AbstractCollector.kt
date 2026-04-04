package com.ohmylawyer.collector

import com.ohmylawyer.domain.entity.CollectionProgress
import com.ohmylawyer.domain.entity.DocumentType
import com.ohmylawyer.domain.repository.CollectionProgressRepository
import org.slf4j.Logger
import java.time.LocalDateTime

/**
 * Base class for data collectors. Handles pagination, progress tracking, and error recovery.
 */
abstract class AbstractCollector(
    private val progressRepository: CollectionProgressRepository,
    private val apiClient: LawApiClient,
    private val props: LawApiProperties
) {
    abstract val log: Logger
    abstract val taskType: String
    abstract val dataType: DocumentType

    open fun collect(query: String? = null) {
        val progress = getOrCreateProgress()
        if (progress.status == "COMPLETED") {
            log.info("[{}] Already completed. Skipping.", taskType)
            return
        }

        progress.status = "RUNNING"
        progress.startedAt = progress.startedAt ?: LocalDateTime.now()
        progress.updatedAt = LocalDateTime.now()
        progressRepository.save(progress)

        try {
            val startPage = if (progress.lastCursor != null) progress.lastCursor!!.toInt() else 1
            collectPages(query, startPage, progress)

            progress.status = "COMPLETED"
            progress.completedAt = LocalDateTime.now()
            log.info("[{}] Completed. Total processed: {}/{}", taskType, progress.processedCount, progress.totalCount)
        } catch (e: Exception) {
            progress.status = "FAILED"
            progress.errorMessage = e.message?.take(1000)
            log.error("[{}] Failed at page {}. Processed {}/{}", taskType, progress.lastCursor, progress.processedCount, progress.totalCount, e)
        } finally {
            progress.updatedAt = LocalDateTime.now()
            progressRepository.save(progress)
        }
    }

    private fun collectPages(query: String?, startPage: Int, progress: CollectionProgress) {
        var page = startPage
        var totalCount: Int? = null

        while (true) {
            log.info("[{}] Fetching page {} ...", taskType, page)
            val searchResult = doSearch(query, page)

            if (totalCount == null) {
                totalCount = parseTotalCount(searchResult)
                progress.totalCount = totalCount
                log.info("[{}] Total count: {}", taskType, totalCount)
            }

            val items = parseSearchItems(searchResult)
            if (items.isEmpty()) {
                log.info("[{}] No more items at page {}", taskType, page)
                break
            }

            for (item in items) {
                try {
                    processItem(item)
                    progress.processedCount++
                } catch (e: Exception) {
                    log.warn("[{}] Failed to process item: {}", taskType, e.message)
                }
            }

            progress.lastCursor = page.toString()
            progress.updatedAt = LocalDateTime.now()
            progressRepository.save(progress)

            if (progress.processedCount >= progress.totalCount) break
            page++

            Thread.sleep(props.requestDelayMs)
        }
    }

    private fun getOrCreateProgress(): CollectionProgress {
        return progressRepository.findByTaskTypeAndDataType(taskType, dataType)
            ?: progressRepository.save(
                CollectionProgress(
                    taskType = taskType,
                    dataType = dataType
                )
            )
    }

    open fun resetProgress() {
        progressRepository.findByTaskTypeAndDataType(taskType, dataType)?.let {
            it.status = "PENDING"
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

    protected abstract fun doSearch(query: String?, page: Int): com.fasterxml.jackson.databind.JsonNode
    protected abstract fun parseTotalCount(searchResult: com.fasterxml.jackson.databind.JsonNode): Int
    protected abstract fun parseSearchItems(searchResult: com.fasterxml.jackson.databind.JsonNode): List<com.fasterxml.jackson.databind.JsonNode>
    protected abstract fun processItem(item: com.fasterxml.jackson.databind.JsonNode)
}
