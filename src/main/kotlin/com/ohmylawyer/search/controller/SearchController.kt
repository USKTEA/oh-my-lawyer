package com.ohmylawyer.search.controller

import com.ohmylawyer.domain.entity.DocumentType
import com.ohmylawyer.search.dto.SearchRequest
import com.ohmylawyer.search.dto.SearchResponse
import com.ohmylawyer.search.service.SearchService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/search")
class SearchController(
    private val searchService: SearchService
) {
    @GetMapping
    fun search(
        @RequestParam query: String,
        @RequestParam(required = false) types: List<DocumentType>?,
        @RequestParam(required = false, defaultValue = "10") topK: Int
    ): ResponseEntity<SearchResponse> {
        val request = SearchRequest(
            query = query,
            documentTypes = types,
            topK = topK
        )
        return ResponseEntity.ok(searchService.search(request))
    }

    @PostMapping
    fun searchPost(@RequestBody request: SearchRequest): ResponseEntity<SearchResponse> {
        return ResponseEntity.ok(searchService.search(request))
    }
}
