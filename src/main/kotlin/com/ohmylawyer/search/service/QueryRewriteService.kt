package com.ohmylawyer.search.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.ohmylawyer.llm.GeminiChatClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class QueryRewriteService(
    private val chatClient: GeminiChatClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun rewrite(userQuery: String): List<String> =
        try {
            val response = chatClient.generate(SYSTEM_INSTRUCTION, userQuery)
            val queries = parseQueries(response)
            if (queries.isEmpty()) {
                log.warn("Query rewrite returned empty result for: {}, using original", userQuery)
                listOf(userQuery)
            } else {
                log.info("Rewrote query '{}' → {}", userQuery, queries)
                queries
            }
        } catch (e: Exception) {
            log.error("Query rewrite failed for: {}, using original", userQuery, e)
            listOf(userQuery)
        }

    companion object {
        private val objectMapper = ObjectMapper()

        private val CODE_BLOCK_REGEX = Regex("""```(?:json)?\s*\n?(.*?)\n?\s*```""", RegexOption.DOT_MATCHES_ALL)

        internal fun parseQueries(json: String): List<String> {
            val cleaned =
                CODE_BLOCK_REGEX
                    .find(json)
                    ?.groupValues
                    ?.get(1)
                    ?.trim() ?: json.trim()

            return try {
                val tree = objectMapper.readTree(cleaned)
                if (!tree.isArray) return emptyList()
                tree.mapNotNull { node ->
                    node.asText().takeIf { it.isNotBlank() }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        internal fun buildPrompt(userQuery: String): String =
            """$SYSTEM_INSTRUCTION

사용자 질문: $userQuery"""

        private const val SYSTEM_INSTRUCTION = """너는 대한민국 법률 및 아파트 서비스 도메인에 특화된 전문 법률 검색 쿼리 변환기(Query Rewriter)이다.
사용자의 일상어 및 실무 용어 기반 질의를 분석하여, 데이터베이스(키워드 및 벡터 검색 결합 엔진) 검색에 최적화된 법률 검색어로 변환하라.

[변환 규칙]
1. 용어 치환: 사용자의 일상어를 정확한 법률 용어로 치환한다. (예: 경찰에 정보 주기 -> 수사기관 사실조회 및 개인정보 제3자 제공)
2. 법령명 명시: 질의와 관련된 핵심 법령명(예: 개인정보보호법, 공동주택관리법, 형사소송법 등)을 쿼리에 포함한다.
3. 형태: 완전한 문장이 아닌, 검색 엔진에 적합한 '명사구' 형태로 작성한다.
4. 개수 및 다양성: 문맥을 커버할 수 있도록 상호 보완적인 쿼리를 최소 2개, 최대 3개 생성한다.
5. 출력 형식: 부연 설명이나 마크다운 백틱 없이 순수한 JSON String Array 포맷만 반환한다.

[입출력 예시]
User: "경찰이 영장 없이 199조 2항이라며 유저 정보 달라고 하는데, 그냥 줘도 리스크 없어?"
Assistant: ["형사소송법 제199조 제2항 임의수사 사실조회", "개인정보보호법 제18조 제2항 개인정보 제3자 제공 예외", "수사기관 영장 없는 정보제공 의무"]

User: "입주민이 관리비 너무 많이 나왔다고 장부 다 까보라는데 법적으로 무조건 보여줘야 해?"
Assistant: ["공동주택관리법 관리비 등 내역 공개", "입주민 장부 및 증빙서류 열람 청구권", "관리주체 정보공개 의무 및 한계"]"""
    }
}
