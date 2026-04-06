package com.ohmylawyer.collection.service

import com.ohmylawyer.collection.collector.AbstractCollector
import com.ohmylawyer.collection.dto.CollectionCommandResponse
import com.ohmylawyer.collection.dto.CollectionStatusResponse
import com.ohmylawyer.domain.entity.CollectionProgress
import com.ohmylawyer.domain.entity.CollectionStatus
import com.ohmylawyer.domain.entity.DocumentType
import com.ohmylawyer.domain.repository.CollectionProgressRepository
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Service
class CollectionService(
    private val collectors: List<AbstractCollector>,
    private val progressRepository: CollectionProgressRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val maxConcurrentCollections = 3
    private val runningCollections = ConcurrentHashMap.newKeySet<String>()
    private val collectionExecutor: ExecutorService = Executors.newFixedThreadPool(maxConcurrentCollections)

    private val collectorMap: Map<DocumentType, AbstractCollector> by lazy {
        collectors.associateBy { it.dataType }
    }

    @PostConstruct
    fun recoverInterruptedTasks() {
        val interrupted = progressRepository.findAll()
            .filter {
                it.status == CollectionStatus.RUNNING ||
                (it.status == CollectionStatus.FAILED && it.errorMessage == null)
            }
        if (interrupted.isNotEmpty()) {
            log.info("Recovering {} interrupted tasks to QUEUED", interrupted.size)
            interrupted.forEach {
                log.info("  {} → QUEUED (was {})", it.dataType, it.status)
                it.status = CollectionStatus.QUEUED
                it.errorMessage = null
                progressRepository.save(it)
            }
        }
    }

    @PreDestroy
    fun shutdown() {
        log.info("Shutting down collection service — {} running tasks", runningCollections.size)

        collectionExecutor.shutdownNow()
        val terminated = collectionExecutor.awaitTermination(10, TimeUnit.SECONDS)
        log.info("Executor terminated: {}", terminated)

        progressRepository.findAll()
            .filter { it.status == CollectionStatus.RUNNING }
            .forEach {
                it.status = CollectionStatus.QUEUED
                progressRepository.save(it)
                log.info("  {} → QUEUED (graceful shutdown)", it.dataType)
            }
    }

    fun enqueue(dataType: DocumentType): CollectionCommandResponse {
        val collector = resolveCollector(dataType)
        val progress = progressRepository.findByTaskTypeAndDataType(collector.taskType, dataType)

        if (progress != null && progress.status in listOf(CollectionStatus.QUEUED, CollectionStatus.RUNNING)) {
            log.info("Task {} is already {} — skipping enqueue", dataType, progress.status)
            return CollectionCommandResponse(progress.status, dataType)
        }

        if (progress != null) {
            progress.status = CollectionStatus.QUEUED
            progressRepository.save(progress)
        } else {
            progressRepository.save(
                CollectionProgress(
                    taskType = collector.taskType,
                    dataType = dataType,
                    status = CollectionStatus.QUEUED
                )
            )
        }
        log.info("Enqueued task: {}", dataType)
        return CollectionCommandResponse(CollectionStatus.QUEUED, dataType)
    }

    fun enqueueAll(): List<CollectionCommandResponse> {
        return collectors.map { enqueue(it.dataType) }
    }

    @Scheduled(fixedDelay = 60_000) // 1분마다 FAILED 태스크 복구
    fun recoverFailedTasks() {
        val failed = progressRepository.findAll()
            .filter { it.status == CollectionStatus.FAILED && it.processedCount < it.totalCount }
        for (task in failed) {
            log.info("Auto-recovering FAILED task: {} (processed {}/{})", task.dataType, task.processedCount, task.totalCount)
            task.status = CollectionStatus.QUEUED
            task.errorMessage = null
            progressRepository.save(task)
        }
    }

    @Scheduled(fixedDelay = 5000)
    fun processQueue() {
        val available = maxConcurrentCollections - runningCollections.size
        if (available <= 0) return

        val queued = progressRepository.findAll()
            .filter { it.status == CollectionStatus.QUEUED }
            .sortedBy { it.updatedAt }
            .take(available)

        for (next in queued) {
            val collector = collectorMap.values.find { it.taskType == next.taskType }
            if (collector == null) {
                log.warn("No collector found for task type: {}", next.taskType)
                next.status = CollectionStatus.FAILED
                next.errorMessage = "No collector registered for ${next.taskType}"
                progressRepository.save(next)
                continue
            }

            if (!runningCollections.add(next.taskType)) continue

            log.info("Processing queued task: {} ({}/{})", next.taskType, runningCollections.size, maxConcurrentCollections)
            collectionExecutor.execute {
                try {
                    collector.collect()
                } finally {
                    runningCollections.remove(next.taskType)
                }
            }
        }
    }

    fun getStatus(): List<CollectionStatusResponse> {
        return progressRepository.findAll().map { p ->
            CollectionStatusResponse(
                dataType = p.dataType,
                status = p.status,
                totalCount = p.totalCount,
                processedCount = p.processedCount,
                lastCursor = p.lastCursor,
                errorMessage = p.errorMessage,
                startedAt = p.startedAt,
                completedAt = p.completedAt
            )
        }
    }

    fun reset(dataType: DocumentType): CollectionCommandResponse {
        resolveCollector(dataType).resetProgress()
        return CollectionCommandResponse(CollectionStatus.PENDING, dataType)
    }

    fun resetAll(): List<CollectionCommandResponse> {
        return collectors.map { reset(it.dataType) }
    }

    private fun resolveCollector(dataType: DocumentType): AbstractCollector {
        return collectorMap[dataType]
            ?: throw IllegalArgumentException("Unknown data type: $dataType. Available: ${collectorMap.keys}")
    }
}
