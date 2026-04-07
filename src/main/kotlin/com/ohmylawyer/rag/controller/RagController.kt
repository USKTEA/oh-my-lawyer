package com.ohmylawyer.rag.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ohmylawyer.rag.dto.RagRequest
import com.ohmylawyer.rag.dto.RagResponse
import com.ohmylawyer.rag.service.RagService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.Executors

@Controller
class RagController(
    private val ragService: RagService
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = ObjectMapper()
    private val executor = Executors.newFixedThreadPool(10)

    @GetMapping("/")
    fun chatPage(): String {
        return "chat"
    }

    @ResponseBody
    @PostMapping("/api/ask")
    fun ask(@RequestBody request: RagRequest): RagResponse {
        return ragService.ask(request)
    }

    @ResponseBody
    @PostMapping("/api/ask/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun askStream(@RequestBody request: RagRequest): SseEmitter {
        val emitter = SseEmitter(180_000L) // 3 minute timeout

        executor.execute {
            try {
                val response = ragService.askStream(request) { progress ->
                    try {
                        emitter.send(
                            SseEmitter.event()
                                .name("progress")
                                .data(objectMapper.writeValueAsString(mapOf("message" to progress)))
                        )
                    } catch (e: Exception) {
                        log.debug("Failed to send progress event: {}", e.message)
                    }
                }

                emitter.send(
                    SseEmitter.event()
                        .name("complete")
                        .data(objectMapper.writeValueAsString(response))
                )
                emitter.complete()
            } catch (e: Exception) {
                log.error("SSE stream error", e)
                try {
                    emitter.send(
                        SseEmitter.event()
                            .name("error")
                            .data(objectMapper.writeValueAsString(mapOf("message" to "분석 중 오류가 발생했습니다.")))
                    )
                } catch (_: Exception) {}
                emitter.completeWithError(e)
            }
        }

        return emitter
    }
}
