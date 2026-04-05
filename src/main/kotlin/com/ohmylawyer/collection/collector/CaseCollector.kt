package com.ohmylawyer.collection.collector

import com.ohmylawyer.collection.client.LawApiClient
import com.ohmylawyer.collection.client.LawApiProperties

import com.fasterxml.jackson.databind.JsonNode
import com.ohmylawyer.collection.parser.CaseParser
import com.ohmylawyer.collection.parser.LawApiParser
import com.ohmylawyer.domain.entity.DocumentType
import com.ohmylawyer.domain.repository.CollectionProgressRepository
import com.ohmylawyer.domain.repository.LawChunkRepository
import com.ohmylawyer.domain.repository.LawDocumentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class CaseCollector(
    progressRepository: CollectionProgressRepository,
    lawDocumentRepository: LawDocumentRepository,
    lawChunkRepository: LawChunkRepository,
    private val apiClient: LawApiClient,
    props: LawApiProperties,
    private val caseParser: CaseParser
) : AbstractCollector(progressRepository, lawDocumentRepository, lawChunkRepository, props) {

    override val log = LoggerFactory.getLogger(javaClass)
    override val dataType = DocumentType.CASE
    override val parser: LawApiParser = caseParser

    override fun doSearch(query: String?, page: Int): JsonNode =
        apiClient.searchCases(query, page)

    override fun fetchDetail(id: String): JsonNode =
        apiClient.getCaseDetail(id)
}
