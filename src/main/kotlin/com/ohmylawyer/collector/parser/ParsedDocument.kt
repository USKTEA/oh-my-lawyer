package com.ohmylawyer.collector.parser

import com.ohmylawyer.domain.entity.ChunkType
import com.ohmylawyer.domain.entity.DocumentType
import java.time.LocalDate

data class ParsedDocument(
    val type: DocumentType,
    val title: String,
    val fullText: String,
    val sourceUrl: String? = null,
    val sourceId: String,
    val metadata: String = "{}",
    val enactedDate: LocalDate? = null,
    val lastAmended: LocalDate? = null,
    val chunks: List<ParsedChunk> = emptyList()
)

data class ParsedChunk(
    val content: String,
    val chunkType: ChunkType,
    val metadata: String = "{}",
    val chunkIndex: Int = 0
)
