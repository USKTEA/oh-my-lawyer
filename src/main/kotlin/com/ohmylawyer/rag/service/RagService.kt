package com.ohmylawyer.rag.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.ohmylawyer.domain.entity.DocumentType
import com.ohmylawyer.llm.GeminiChatClient
import com.ohmylawyer.rag.dto.Citation
import com.ohmylawyer.rag.dto.RagRequest
import com.ohmylawyer.rag.dto.RagResponse
import com.ohmylawyer.rag.dto.RiskLevel
import com.ohmylawyer.search.dto.SearchRequest
import com.ohmylawyer.search.dto.SearchResult
import com.ohmylawyer.search.service.SearchService
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RagService(
    private val chatClient: GeminiChatClient,
    private val searchService: SearchService,
    private val citationVerifier: CitationVerifier
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun ask(request: RagRequest): RagResponse {
        log.info("RAG request: {}", request.question)

        val allChunks = mutableListOf<ContextChunk>()
        var lastResponse: LlmResponse? = null
        var iterations = 0

        // initial search
        val initialResults = searchService.search(
            SearchRequest(query = request.question, documentTypes = request.documentTypes, topK = 10)
        )
        allChunks.addAll(initialResults.results.map { it.toContextChunk() })

        // iterative retrieval (up to MAX_ITERATIONS)
        for (i in 1..MAX_ITERATIONS) {
            iterations = i
            val context = buildContext(allChunks)
            val userMessage = buildUserMessage(request.question, context)

            log.info("RAG iteration {}/{}, context chunks: {}", i, MAX_ITERATIONS, allChunks.size)

            val rawResponse = chatClient.generate(
                systemInstruction = SYSTEM_INSTRUCTION,
                userMessage = userMessage,
                model = GeminiChatClient.Model.CHAT,
                timeoutSeconds = 120
            )

            lastResponse = parseLlmResponse(rawResponse)

            if (lastResponse.additionalQueries.isEmpty() || i == MAX_ITERATIONS) {
                break
            }

            // additional search for next iteration (parallel)
            log.info("Additional queries requested: {}", lastResponse.additionalQueries)
            val newChunks = runBlocking {
                lastResponse.additionalQueries.map { query ->
                    async {
                        searchService.search(
                            SearchRequest(query = query, documentTypes = request.documentTypes, topK = 5)
                        ).results.map { it.toContextChunk() }
                    }
                }.flatMap { it.await() }
            }.filter { new -> allChunks.none { it.content == new.content } }
            allChunks.addAll(newChunks)
        }

        val response = lastResponse ?: return errorResponse(request.question)

        // verify citations
        val verifiedCitations = citationVerifier.verify(response.citations)

        return RagResponse(
            question = request.question,
            riskLevel = response.riskLevel,
            analysis = response.analysis,
            citations = verifiedCitations.filter { it.existsInDb },
            invalidCitations = verifiedCitations.filter { !it.existsInDb }.map { it.source },
            iterations = iterations
        )
    }

    private fun buildUserMessage(question: String, context: String): String {
        return """[사용자 질문]
$question

[검색된 법률 자료]
$context"""
    }

    private fun errorResponse(question: String): RagResponse {
        return RagResponse(
            question = question,
            riskLevel = RiskLevel.MEDIUM,
            analysis = "분석을 완료하지 못했습니다. 다시 시도해 주세요.",
            citations = emptyList(),
            invalidCitations = emptyList(),
            iterations = 0
        )
    }

    private fun SearchResult.toContextChunk() = ContextChunk(
        documentTitle = documentTitle,
        documentType = documentType,
        chunkType = chunkType,
        content = content
    )

    data class LlmResponse(
        val riskLevel: RiskLevel,
        val analysis: String,
        val citations: List<LlmCitation>,
        val additionalQueries: List<String>
    )

    data class LlmCitation(
        val source: String,
        val content: String
    )

    data class ContextChunk(
        val documentTitle: String,
        val documentType: String,
        val chunkType: String,
        val content: String
    )

    companion object {
        private const val MAX_ITERATIONS = 3
        private val objectMapper = ObjectMapper()
        private val CODE_BLOCK_REGEX = Regex("""```(?:json)?\s*\n?(.*?)\n?\s*```""", RegexOption.DOT_MATCHES_ALL)

        private val DOCUMENT_TYPE_LABELS = mapOf(
            "LAW" to "법령",
            "CASE" to "판례",
            "CONSTITUTIONAL" to "헌재결정",
            "INTERPRETATION" to "해석례",
            "ADMINISTRATIVE_RULE" to "행정규칙",
            "LEGAL_OPINION" to "사내 법률자문"
        )

        internal fun parseLlmResponse(json: String): LlmResponse {
            val cleaned = CODE_BLOCK_REGEX.find(json)?.groupValues?.get(1)?.trim() ?: json.trim()

            return try {
                val tree = objectMapper.readTree(cleaned)

                val riskLevel = try {
                    RiskLevel.valueOf(tree.path("riskLevel").asText("MEDIUM"))
                } catch (e: IllegalArgumentException) {
                    RiskLevel.MEDIUM
                }

                val analysis = tree.path("analysis").asText("")

                val citations = tree.path("citations").mapNotNull { node ->
                    val source = node.path("source").asText("")
                    val content = node.path("content").asText("")
                    if (source.isNotBlank()) LlmCitation(source, content) else null
                }

                val additionalQueries = tree.path("additionalQueries").mapNotNull { node ->
                    node.asText().takeIf { it.isNotBlank() }
                }

                LlmResponse(riskLevel, analysis, citations, additionalQueries)
            } catch (e: Exception) {
                LlmResponse(
                    riskLevel = RiskLevel.MEDIUM,
                    analysis = "분석을 완료하지 못했습니다. 원본 응답: ${json.take(200)}",
                    citations = emptyList(),
                    additionalQueries = emptyList()
                )
            }
        }

        internal fun buildContext(chunks: List<ContextChunk>): String {
            return chunks.joinToString("\n\n---\n\n") { chunk ->
                val typeLabel = DOCUMENT_TYPE_LABELS[chunk.documentType] ?: chunk.documentType
                "[${typeLabel}] ${chunk.documentTitle} (${chunk.chunkType})\n${chunk.content}"
            }
        }

        private const val SYSTEM_INSTRUCTION = """너는 대한민국 법률 분석 전문가이다. 아파트 서비스 회사의 PO(Product Owner)가 법적 리스크를 사전 검토할 수 있도록 돕는다.

[역할]
- 제공된 법률 자료(법령, 판례, 해석례)만을 근거로 분석한다.
- 자료에 없는 내용을 추측하거나 지어내지 않는다.
- 근거가 부족하면 additionalQueries로 추가 검색을 요청한다.
- 여러 심급의 판례가 존재할 경우, 반드시 최고재판소(대법원, 헌법재판소)의 판례를 최우선으로 인용하라. 하급심 판례는 대법원 판례가 없을 때만 보충적으로 인용한다.

[출력 규칙]
1. riskLevel: 리스크 등급 (HIGH, MEDIUM, LOW)
   - HIGH: 법률 위반 가능성 높음, 즉시 법률 자문 필요
   - MEDIUM: 해석에 따라 리스크 존재, 주의 필요
   - LOW: 법적 리스크 낮음, 일반적으로 안전
2. analysis: 법률 분석 의견 (한국어, 500자 이내)
   - PO가 이해할 수 있는 쉬운 언어로 작성
   - 구체적 조문/판례를 인용하며 근거 제시
   - 사용자가 명시적인 행동(~하면 되는가?, ~해도 되는가?)을 묻는 경우, 답변 서두에 해당 행동의 가부(필요 없음, 불가함, 가능함 등)를 먼저 명확히 밝힌 후 근거를 설명하라
3. citations: 인용한 법률 근거 목록
   - source: 법령명+조문번호 또는 판례번호 (예: "개인정보보호법 제18조 제2항", "2012다105482")
   - content: 해당 조문/판례의 핵심 내용 요약 (1문장)
   - '사내 법률자문' 자료는 분석의 참고로만 활용하고, citations에는 그 안에 인용된 법령/판례를 직접 인용하라
4. additionalQueries: 근거가 부족하여 추가 검색이 필요한 경우 검색 쿼리 목록 (명사구 형태, 최대 2개)
   - 충분한 근거가 있으면 빈 배열 []로 반환

[출력 형식]
JSON 객체만 반환한다. 부연 설명이나 마크다운 없이 순수 JSON만 반환한다.

{
  "riskLevel": "HIGH|MEDIUM|LOW",
  "analysis": "분석 내용",
  "citations": [{"source": "출처", "content": "요약"}],
  "additionalQueries": ["추가 검색어1", "추가 검색어2"]
}"""
    }
}
