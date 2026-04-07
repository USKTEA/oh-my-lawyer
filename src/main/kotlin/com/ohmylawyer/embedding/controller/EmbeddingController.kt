package com.ohmylawyer.embedding.controller

import com.ohmylawyer.embedding.service.EmbeddingService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/embedding")
class EmbeddingController(
    private val embeddingService: EmbeddingService,
) {
    @GetMapping("/status")
    fun getStatus(): ResponseEntity<EmbeddingService.EmbeddingStatus> = ResponseEntity.ok(embeddingService.getStatus())
}
