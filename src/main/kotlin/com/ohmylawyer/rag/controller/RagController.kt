package com.ohmylawyer.rag.controller

import com.ohmylawyer.rag.dto.RagRequest
import com.ohmylawyer.rag.dto.RagResponse
import com.ohmylawyer.rag.service.RagService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/ask")
class RagController(
    private val ragService: RagService
) {
    @PostMapping
    fun ask(@RequestBody request: RagRequest): RagResponse {
        return ragService.ask(request)
    }
}
