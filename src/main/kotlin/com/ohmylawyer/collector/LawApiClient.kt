package com.ohmylawyer.collector

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

/**
 * 법제처 국가법령정보센터 Open API HTTP client.
 *
 * Endpoints:
 *   - lawSearch.do : 목록 조회 (법령/판례/헌재결정/해석례)
 *   - lawService.do: 본문 조회
 *
 * target values: eflaw(법령), prec(판례), detc(헌재결정), expc(해석례)
 */
@Component
class LawApiClient(
    private val props: LawApiProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = ObjectMapper()

    private val webClient = WebClient.builder()
        .baseUrl(props.baseUrl)
        .codecs { it.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) } // 10MB
        .build()

    // -- 목록 조회 (lawSearch.do) --

    fun search(target: String, query: String? = null, page: Int = 1, display: Int = 100, extraParams: Map<String, String> = emptyMap()): JsonNode {
        return withRetry("lawSearch.do(target=$target, page=$page)") {
            val response = webClient.get()
                .uri { builder ->
                    builder.path("/lawSearch.do")
                        .queryParam("OC", props.oc)
                        .queryParam("target", target)
                        .queryParam("type", "JSON")
                        .queryParam("display", display.coerceAtMost(props.maxDisplay))
                        .queryParam("page", page)
                    query?.let { builder.queryParam("query", it) }
                    extraParams.forEach { (k, v) -> builder.queryParam(k, v) }
                    builder.build()
                }
                .retrieve()
                .bodyToMono(String::class.java)
                .block() ?: throw IllegalStateException("Empty response from lawSearch.do (target=$target)")

            log.debug("Search response (target={}, page={}): {}...", target, page, response.take(200))
            objectMapper.readTree(response)
        }
    }

    // -- 본문 조회 (lawService.do) --

    fun getDetail(target: String, id: String, extraParams: Map<String, String> = emptyMap()): JsonNode {
        return withRetry("lawService.do(target=$target, id=$id)") {
            val response = webClient.get()
                .uri { builder ->
                    builder.path("/lawService.do")
                        .queryParam("OC", props.oc)
                        .queryParam("target", target)
                        .queryParam("type", "JSON")
                        .queryParam("ID", id)
                    extraParams.forEach { (k, v) -> builder.queryParam(k, v) }
                    builder.build()
                }
                .retrieve()
                .bodyToMono(String::class.java)
                .block() ?: throw IllegalStateException("Empty response from lawService.do (target=$target, id=$id)")

            log.debug("Detail response (target={}, id={}): {}...", target, id, response.take(200))
            objectMapper.readTree(response)
        }
    }

    private fun <T> withRetry(context: String, action: () -> T): T {
        var lastException: Exception? = null
        for (attempt in 1..props.maxRetries) {
            try {
                return action()
            } catch (e: WebClientResponseException) {
                lastException = e
                val status = e.statusCode.value()
                if (status == 429 || status >= 500) {
                    val delay = props.retryDelayMs * attempt
                    log.warn("[Retry {}/{}] {} - HTTP {} — waiting {}ms", attempt, props.maxRetries, context, status, delay)
                    Thread.sleep(delay)
                } else {
                    throw e
                }
            } catch (e: Exception) {
                lastException = e
                val delay = props.retryDelayMs * attempt
                log.warn("[Retry {}/{}] {} - {} — waiting {}ms", attempt, props.maxRetries, context, e.message, delay)
                Thread.sleep(delay)
            }
        }
        throw lastException!!
    }

    // -- Convenience methods --

    fun searchLaws(query: String? = null, page: Int = 1, display: Int = 100) =
        search("eflaw", query, page, display)

    fun getLawDetail(lawId: String) =
        getDetail("eflaw", lawId)

    fun searchCases(query: String? = null, page: Int = 1, display: Int = 100, extraParams: Map<String, String> = emptyMap()) =
        search("prec", query, page, display, extraParams)

    fun getCaseDetail(caseId: String) =
        getDetail("prec", caseId)

    fun searchConstitutional(query: String? = null, page: Int = 1, display: Int = 100) =
        search("detc", query, page, display)

    fun getConstitutionalDetail(id: String) =
        getDetail("detc", id)

    fun searchInterpretations(query: String? = null, page: Int = 1, display: Int = 100) =
        search("expc", query, page, display)

    fun getInterpretationDetail(id: String) =
        getDetail("expc", id)
}
