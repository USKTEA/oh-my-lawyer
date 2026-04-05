package com.ohmylawyer.collector

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/collect")
class CollectionController(
    private val collectionService: CollectionService
) {

    @PostMapping("/{taskType}")
    fun collect(
        @PathVariable taskType: String,
        @RequestParam query: String? = null
    ): ResponseEntity<Map<String, String>> {
        collectionService.startCollection(resolveTaskType(taskType), query)
        return ResponseEntity.accepted().body(mapOf("status" to "STARTED", "task" to taskType))
    }

    @PostMapping("/all")
    fun collectAll(@RequestParam query: String? = null): ResponseEntity<Map<String, String>> {
        collectionService.startAll(query)
        return ResponseEntity.accepted().body(mapOf("status" to "STARTED", "task" to "ALL"))
    }

    @GetMapping("/status")
    fun getStatus(): ResponseEntity<List<Map<String, Any?>>> {
        return ResponseEntity.ok(collectionService.getStatus())
    }

    @PostMapping("/reset/{taskType}")
    fun resetProgress(@PathVariable taskType: String): ResponseEntity<Map<String, String>> {
        collectionService.reset(resolveTaskType(taskType))
        return ResponseEntity.ok(mapOf("status" to "RESET", "task" to taskType))
    }

    private fun resolveTaskType(pathVar: String): String {
        if (pathVar == "all") return "all"
        // Map URL-friendly names to task types: "laws" -> "COLLECT_LAW"
        val prefix = "COLLECT_"
        val normalized = pathVar.uppercase().removeSuffix("S").replace("-", "_")
        return prefix + normalized
    }
}
