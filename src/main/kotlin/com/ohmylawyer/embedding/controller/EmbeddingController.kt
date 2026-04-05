package com.ohmylawyer.embedding.controller

import com.ohmylawyer.domain.entity.DocumentType
import com.ohmylawyer.embedding.service.EmbeddingService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.concurrent.Executors

@RestController
@RequestMapping("/api/embedding")
class EmbeddingController(
    private val embeddingService: EmbeddingService
) {
    private val executor = Executors.newSingleThreadExecutor()

    @PostMapping("/start/{dataType}")
    fun startEmbedding(@PathVariable dataType: DocumentType): ResponseEntity<EmbeddingResponse> {
        val progress = embeddingService.startEmbedding(dataType)

        if (progress.status.name == "RUNNING") {
            executor.submit { embeddingService.processEmbeddings(dataType) }
        }

        return ResponseEntity.ok(
            EmbeddingResponse(
                dataType = dataType.name,
                status = progress.status.name,
                totalCount = progress.totalCount,
                processedCount = progress.processedCount
            )
        )
    }

    @PostMapping("/start-all")
    fun startAllEmbeddings(): ResponseEntity<List<EmbeddingResponse>> {
        val responses = DocumentType.entries.map { dataType ->
            val progress = embeddingService.startEmbedding(dataType)

            if (progress.status.name == "RUNNING") {
                executor.submit { embeddingService.processEmbeddings(dataType) }
            }

            EmbeddingResponse(
                dataType = dataType.name,
                status = progress.status.name,
                totalCount = progress.totalCount,
                processedCount = progress.processedCount
            )
        }
        return ResponseEntity.ok(responses)
    }

    @GetMapping("/progress")
    fun getAllProgress(): ResponseEntity<List<EmbeddingResponse>> {
        val progressList = embeddingService.getAllProgress()
        return ResponseEntity.ok(progressList.map {
            EmbeddingResponse(
                dataType = it.dataType.name,
                status = it.status.name,
                totalCount = it.totalCount,
                processedCount = it.processedCount,
                errorMessage = it.errorMessage
            )
        })
    }

    @GetMapping("/progress/{dataType}")
    fun getProgress(@PathVariable dataType: DocumentType): ResponseEntity<EmbeddingResponse> {
        val progress = embeddingService.getProgress(dataType)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(
            EmbeddingResponse(
                dataType = progress.dataType.name,
                status = progress.status.name,
                totalCount = progress.totalCount,
                processedCount = progress.processedCount,
                errorMessage = progress.errorMessage
            )
        )
    }

    data class EmbeddingResponse(
        val dataType: String,
        val status: String,
        val totalCount: Int,
        val processedCount: Int,
        val errorMessage: String? = null
    )
}
