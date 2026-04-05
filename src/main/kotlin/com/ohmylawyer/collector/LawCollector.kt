package com.ohmylawyer.collector

import com.fasterxml.jackson.databind.JsonNode
import com.ohmylawyer.collector.parser.LawApiParser
import com.ohmylawyer.collector.parser.LawParser
import com.ohmylawyer.domain.entity.DocumentType
import com.ohmylawyer.domain.repository.CollectionProgressRepository
import com.ohmylawyer.domain.repository.LawChunkRepository
import com.ohmylawyer.domain.repository.LawDocumentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LawCollector(
    progressRepository: CollectionProgressRepository,
    lawDocumentRepository: LawDocumentRepository,
    lawChunkRepository: LawChunkRepository,
    private val apiClient: LawApiClient,
    props: LawApiProperties,
    private val lawParser: LawParser
) : AbstractCollector(progressRepository, lawDocumentRepository, lawChunkRepository, props) {

    override val log = LoggerFactory.getLogger(javaClass)
    override val taskType = "COLLECT_LAW"
    override val dataType = DocumentType.LAW
    override val parser: LawApiParser = lawParser

    override fun doSearch(query: String?, page: Int): JsonNode =
        apiClient.searchLaws(query, page)

    override fun fetchDetail(id: String): JsonNode =
        apiClient.getLawDetail(id)
}
