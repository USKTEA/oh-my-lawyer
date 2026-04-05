package com.ohmylawyer.collector

import com.ohmylawyer.domain.repository.CollectionProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Service
class CollectionService(
    private val collectors: List<AbstractCollector>,
    private val progressRepository: CollectionProgressRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val executor: Executor = Executors.newSingleThreadExecutor()

    private val collectorMap: Map<String, AbstractCollector> by lazy {
        collectors.associateBy { it.taskType }
    }

    fun startCollection(taskType: String, query: String? = null) {
        val collector = collectorMap[taskType]
            ?: throw IllegalArgumentException("Unknown task type: $taskType. Available: ${collectorMap.keys}")

        executor.execute {
            log.info("Starting collection: {} (query={})", taskType, query)
            collector.collect(query)
        }
    }

    fun startAll(query: String? = null) {
        executor.execute {
            log.info("Starting full collection (query={})", query)
            collectors.forEach { it.collect(query) }
            log.info("Full collection completed")
        }
    }

    fun getStatus(): List<Map<String, Any?>> {
        return progressRepository.findAll().map { p ->
            mapOf(
                "taskType" to p.taskType,
                "dataType" to p.dataType.name,
                "status" to p.status,
                "totalCount" to p.totalCount,
                "processedCount" to p.processedCount,
                "lastCursor" to p.lastCursor,
                "errorMessage" to p.errorMessage,
                "startedAt" to p.startedAt?.toString(),
                "completedAt" to p.completedAt?.toString()
            )
        }
    }

    fun reset(taskType: String) {
        if (taskType == "all") {
            collectors.forEach { it.resetProgress() }
        } else {
            val collector = collectorMap[taskType]
                ?: throw IllegalArgumentException("Unknown task type: $taskType. Available: ${collectorMap.keys}")
            collector.resetProgress()
        }
    }

    fun availableTaskTypes(): Set<String> = collectorMap.keys
}
