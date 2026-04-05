package com.ohmylawyer.collection.controller

import com.ohmylawyer.collection.service.CollectionService

import com.ohmylawyer.collection.dto.CollectionCommandResponse
import com.ohmylawyer.collection.dto.CollectionStatusResponse
import com.ohmylawyer.domain.entity.DocumentType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/collect")
class CollectionController(
    private val collectionService: CollectionService
) {

    @PostMapping("/{dataType}")
    fun collect(@PathVariable dataType: String): ResponseEntity<CollectionCommandResponse> {
        return ResponseEntity.accepted().body(collectionService.enqueue(resolveDataType(dataType)))
    }

    @PostMapping("/all")
    fun collectAll(): ResponseEntity<List<CollectionCommandResponse>> {
        return ResponseEntity.accepted().body(collectionService.enqueueAll())
    }

    @GetMapping("/status")
    fun getStatus(): ResponseEntity<List<CollectionStatusResponse>> {
        return ResponseEntity.ok(collectionService.getStatus())
    }

    @PostMapping("/reset/{dataType}")
    fun resetProgress(@PathVariable dataType: String): ResponseEntity<CollectionCommandResponse> {
        return if (dataType == "all") {
            ResponseEntity.ok(collectionService.resetAll().first())
        } else {
            ResponseEntity.ok(collectionService.reset(resolveDataType(dataType)))
        }
    }

    private fun resolveDataType(pathVar: String): DocumentType {
        val normalized = pathVar.uppercase().removeSuffix("S").replace("-", "_")
        return DocumentType.entries.find { it.name == normalized }
            ?: throw IllegalArgumentException("Unknown data type: $pathVar. Available: ${DocumentType.entries.map { it.name }}")
    }
}
