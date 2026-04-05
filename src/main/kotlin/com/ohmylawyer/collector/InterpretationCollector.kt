package com.ohmylawyer.collector

import com.fasterxml.jackson.databind.JsonNode
import com.ohmylawyer.collector.parser.InterpretationParser
import com.ohmylawyer.collector.parser.LawApiParser
import com.ohmylawyer.domain.entity.DocumentType
import com.ohmylawyer.domain.repository.CollectionProgressRepository
import com.ohmylawyer.domain.repository.LawChunkRepository
import com.ohmylawyer.domain.repository.LawDocumentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class InterpretationCollector(
    progressRepository: CollectionProgressRepository,
    lawDocumentRepository: LawDocumentRepository,
    lawChunkRepository: LawChunkRepository,
    private val apiClient: LawApiClient,
    props: LawApiProperties,
    private val interpretationParser: InterpretationParser
) : AbstractCollector(progressRepository, lawDocumentRepository, lawChunkRepository, props) {

    override val log = LoggerFactory.getLogger(javaClass)
    override val dataType = DocumentType.INTERPRETATION
    override val parser: LawApiParser = interpretationParser

    override fun doSearch(query: String?, page: Int): JsonNode =
        apiClient.searchInterpretations(query, page)

    override fun fetchDetail(id: String): JsonNode =
        apiClient.getInterpretationDetail(id)
}
