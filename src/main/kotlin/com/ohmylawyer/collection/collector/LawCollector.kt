package com.ohmylawyer.collection.collector

import com.ohmylawyer.collection.client.LawApiClient
import com.ohmylawyer.collection.client.LawApiProperties

import com.fasterxml.jackson.databind.JsonNode
import com.ohmylawyer.collection.parser.LawApiParser
import com.ohmylawyer.collection.parser.LawParser
import com.ohmylawyer.domain.entity.DocumentType
import com.ohmylawyer.domain.repository.CollectionProgressRepository
import com.ohmylawyer.collection.service.DocumentPersistenceService
import com.ohmylawyer.domain.repository.LawDocumentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LawCollector(
    progressRepository: CollectionProgressRepository,
    lawDocumentRepository: LawDocumentRepository,
    documentPersistenceService: DocumentPersistenceService,
    private val apiClient: LawApiClient,
    props: LawApiProperties,
    private val lawParser: LawParser
) : AbstractCollector(progressRepository, lawDocumentRepository, documentPersistenceService, props) {

    override val log = LoggerFactory.getLogger(javaClass)
    override val dataType = DocumentType.LAW
    override val parser: LawApiParser = lawParser

    override fun doSearch(query: String?, page: Int): JsonNode =
        apiClient.searchLaws(query, page)

    override fun fetchDetail(id: String): JsonNode =
        apiClient.getLawDetailByMst(id)

    override fun filterSearchItems(items: List<JsonNode>): List<JsonNode> {
        return items.filter { item ->
            val status = item.path("현행연혁코드").asText("")
            status == "현행"
        }
    }
}
