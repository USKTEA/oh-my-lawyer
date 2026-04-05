package com.ohmylawyer.embedding.controller

import com.ohmylawyer.embedding.service.EmbeddingService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/embedding")
class EmbeddingController(
    private val embeddingService: EmbeddingService
) {
    @GetMapping("/status")
    fun getStatus(): ResponseEntity<EmbeddingService.EmbeddingStatus> {
        return ResponseEntity.ok(embeddingService.getStatus())
    }
}
