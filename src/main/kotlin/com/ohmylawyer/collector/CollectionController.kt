package com.ohmylawyer.collector

import com.ohmylawyer.domain.repository.CollectionProgressRepository
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@RestController
@RequestMapping("/api/collect")
class CollectionController(
    private val lawCollector: LawCollector,
    private val caseCollector: CaseCollector,
    private val constitutionalCollector: ConstitutionalCollector,
    private val interpretationCollector: InterpretationCollector,
    private val administrativeRuleCollector: AdministrativeRuleCollector,
    private val progressRepository: CollectionProgressRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val executor: Executor = Executors.newSingleThreadExecutor()

    @PostMapping("/laws")
    fun collectLaws(@RequestParam query: String? = null): ResponseEntity<Map<String, String>> {
        executor.execute {
            log.info("Starting law collection (query={})", query)
            lawCollector.collect(query)
        }
        return ResponseEntity.accepted().body(mapOf("status" to "STARTED", "task" to "COLLECT_LAW"))
    }

    @PostMapping("/cases")
    fun collectCases(@RequestParam query: String? = null): ResponseEntity<Map<String, String>> {
        executor.execute {
            log.info("Starting case collection (query={})", query)
            caseCollector.collect(query)
        }
        return ResponseEntity.accepted().body(mapOf("status" to "STARTED", "task" to "COLLECT_CASE"))
    }

    @PostMapping("/constitutional")
    fun collectConstitutional(@RequestParam query: String? = null): ResponseEntity<Map<String, String>> {
        executor.execute {
            log.info("Starting constitutional collection (query={})", query)
            constitutionalCollector.collect(query)
        }
        return ResponseEntity.accepted().body(mapOf("status" to "STARTED", "task" to "COLLECT_CONSTITUTIONAL"))
    }

    @PostMapping("/interpretations")
    fun collectInterpretations(@RequestParam query: String? = null): ResponseEntity<Map<String, String>> {
        executor.execute {
            log.info("Starting interpretation collection (query={})", query)
            interpretationCollector.collect(query)
        }
        return ResponseEntity.accepted().body(mapOf("status" to "STARTED", "task" to "COLLECT_INTERPRETATION"))
    }

    @PostMapping("/administrative-rules")
    fun collectAdministrativeRules(@RequestParam query: String? = null): ResponseEntity<Map<String, String>> {
        executor.execute {
            log.info("Starting administrative rule collection (query={})", query)
            administrativeRuleCollector.collect(query)
        }
        return ResponseEntity.accepted().body(mapOf("status" to "STARTED", "task" to "COLLECT_ADMINISTRATIVE_RULE"))
    }

    @PostMapping("/all")
    fun collectAll(@RequestParam query: String? = null): ResponseEntity<Map<String, String>> {
        executor.execute {
            log.info("Starting full collection (query={})", query)
            lawCollector.collect(query)
            caseCollector.collect(query)
            constitutionalCollector.collect(query)
            interpretationCollector.collect(query)
            administrativeRuleCollector.collect(query)
            log.info("Full collection completed")
        }
        return ResponseEntity.accepted().body(mapOf("status" to "STARTED", "task" to "COLLECT_ALL"))
    }

    @GetMapping("/status")
    fun getStatus(): ResponseEntity<List<Map<String, Any?>>> {
        val allProgress = progressRepository.findAll().map { p ->
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
        return ResponseEntity.ok(allProgress)
    }

    @PostMapping("/reset/{taskType}")
    fun resetProgress(@PathVariable taskType: String): ResponseEntity<Map<String, String>> {
        when (taskType) {
            "laws" -> lawCollector.resetProgress()
            "cases" -> caseCollector.resetProgress()
            "constitutional" -> constitutionalCollector.resetProgress()
            "interpretations" -> interpretationCollector.resetProgress()
            "administrative-rules" -> administrativeRuleCollector.resetProgress()
            "all" -> {
                lawCollector.resetProgress()
                caseCollector.resetProgress()
                constitutionalCollector.resetProgress()
                interpretationCollector.resetProgress()
                administrativeRuleCollector.resetProgress()
            }
            else -> return ResponseEntity.badRequest().body(mapOf("error" to "Unknown task: $taskType"))
        }
        return ResponseEntity.ok(mapOf("status" to "RESET", "task" to taskType))
    }
}
