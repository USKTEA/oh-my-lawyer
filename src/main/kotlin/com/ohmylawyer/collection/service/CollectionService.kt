package com.ohmylawyer.collection.service

import com.ohmylawyer.collection.collector.AbstractCollector

import com.ohmylawyer.collection.dto.CollectionCommandResponse
import com.ohmylawyer.collection.dto.CollectionStatusResponse
import com.ohmylawyer.domain.entity.CollectionProgress
import com.ohmylawyer.domain.entity.CollectionStatus
import com.ohmylawyer.domain.entity.DocumentType
import com.ohmylawyer.domain.repository.CollectionProgressRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class CollectionService(
    private val collectors: List<AbstractCollector>,
    private val progressRepository: CollectionProgressRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val collectorMap: Map<DocumentType, AbstractCollector> by lazy {
        collectors.associateBy { it.dataType }
    }

    fun enqueue(dataType: DocumentType): CollectionCommandResponse {
        val collector = resolveCollector(dataType)
        val progress = progressRepository.findByTaskTypeAndDataType(collector.taskType, dataType)

        if (progress != null && progress.status == CollectionStatus.QUEUED) {
            log.info("Task {} is already queued", dataType)
            return CollectionCommandResponse(CollectionStatus.QUEUED, dataType)
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

    @Scheduled(fixedDelay = 5000)
    fun processQueue() {
        val queued = progressRepository.findAll()
            .filter { it.status == CollectionStatus.QUEUED }
            .sortedBy { it.updatedAt }

        if (queued.isEmpty()) return

        val next = queued.first()
        val collector = collectorMap.values.find { it.taskType == next.taskType }
        if (collector == null) {
            log.warn("No collector found for task type: {}", next.taskType)
            next.status = CollectionStatus.FAILED
            next.errorMessage = "No collector registered for ${next.taskType}"
            progressRepository.save(next)
            return
        }

        log.info("Processing queued task: {}", next.taskType)
        collector.collect()
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
