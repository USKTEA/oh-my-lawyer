package com.ohmylawyer.collection.collector

import com.ohmylawyer.collection.client.LawApiClient
import com.ohmylawyer.collection.client.LawApiProperties

import com.fasterxml.jackson.databind.JsonNode
import com.ohmylawyer.collection.parser.InterpretationParser
import com.ohmylawyer.collection.parser.LawApiParser
import com.ohmylawyer.domain.entity.DocumentType
import com.ohmylawyer.domain.repository.CollectionProgressRepository
import com.ohmylawyer.collection.service.DocumentPersistenceService
import com.ohmylawyer.domain.repository.LawDocumentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class InterpretationCollector(
    progressRepository: CollectionProgressRepository,
    lawDocumentRepository: LawDocumentRepository,
    documentPersistenceService: DocumentPersistenceService,
    private val apiClient: LawApiClient,
    props: LawApiProperties,
    private val interpretationParser: InterpretationParser
) : AbstractCollector(progressRepository, lawDocumentRepository, documentPersistenceService, props) {

    override val log = LoggerFactory.getLogger(javaClass)
    override val dataType = DocumentType.INTERPRETATION
    override val parser: LawApiParser = interpretationParser

    override fun doSearch(query: String?, page: Int): JsonNode =
        apiClient.searchInterpretations(query, page)

    override fun fetchDetail(id: String): JsonNode =
        apiClient.getInterpretationDetail(id)
}
