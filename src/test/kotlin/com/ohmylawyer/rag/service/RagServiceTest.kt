package com.ohmylawyer.rag.service

import com.ohmylawyer.rag.dto.RiskLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RagServiceTest {
    @Test
    fun `parseLlmResponse - valid JSON with all fields`() {
        val json =
            """
            {
                "riskLevel": "HIGH",
                "analysis": "개인정보보호법 제18조에 따라 수사기관 요청이라도 영장 없이 제공 시 위험합니다.",
                "citations": [
                    {"source": "개인정보보호법 제18조", "content": "개인정보의 목적 외 이용·제공 제한"},
                    {"source": "형사소송법 제199조", "content": "수사에 필요한 조사를 할 수 있다"}
                ],
                "additionalQueries": []
            }
            """.trimIndent()

        val result = RagService.parseLlmResponse(json)

        assertEquals(RiskLevel.HIGH, result.riskLevel)
        assertTrue(result.analysis.contains("개인정보보호법"))
        assertEquals(2, result.citations.size)
        assertEquals("개인정보보호법 제18조", result.citations[0].source)
        assertTrue(result.additionalQueries.isEmpty())
    }

    @Test
    fun `parseLlmResponse - with additional queries for iterative retrieval`() {
        val json =
            """
            {
                "riskLevel": "MEDIUM",
                "analysis": "추가 검토가 필요합니다.",
                "citations": [],
                "additionalQueries": ["공동주택관리법 관리비 공개 의무", "입주자대표회의 장부 열람권"]
            }
            """.trimIndent()

        val result = RagService.parseLlmResponse(json)

        assertEquals(RiskLevel.MEDIUM, result.riskLevel)
        assertEquals(2, result.additionalQueries.size)
    }

    @Test
    fun `parseLlmResponse - malformed JSON returns fallback`() {
        val json = "this is not json at all"

        val result = RagService.parseLlmResponse(json)

        assertEquals(RiskLevel.MEDIUM, result.riskLevel)
        assertTrue(result.analysis.contains("분석을 완료하지 못했습니다"))
    }

    @Test
    fun `parseLlmResponse - unknown riskLevel defaults to MEDIUM`() {
        val json =
            """
            {
                "riskLevel": "UNKNOWN",
                "analysis": "분석 내용",
                "citations": [],
                "additionalQueries": []
            }
            """.trimIndent()

        val result = RagService.parseLlmResponse(json)

        assertEquals(RiskLevel.MEDIUM, result.riskLevel)
    }

    @Test
    fun `parseLlmResponse - JSON with markdown code block fences`() {
        val json =
            """
            ```json
            {
                "riskLevel": "LOW",
                "analysis": "리스크가 낮습니다.",
                "citations": [],
                "additionalQueries": []
            }
            ```
            """.trimIndent()

        val result = RagService.parseLlmResponse(json)

        assertEquals(RiskLevel.LOW, result.riskLevel)
    }

    @Test
    fun `buildContext - formats search results for LLM`() {
        val chunks =
            listOf(
                RagService.ContextChunk(
                    documentTitle = "개인정보 보호법",
                    documentType = "LAW",
                    chunkType = "ARTICLE",
                    content = "제18조(개인정보의 목적 외 이용·제공 제한)",
                ),
                RagService.ContextChunk(
                    documentTitle = "2012다105482",
                    documentType = "CASE",
                    chunkType = "HOLDING",
                    content = "통신자료 제공 관련 손해배상",
                ),
            )

        val context = RagService.buildContext(chunks)

        assertTrue(context.contains("[법령] 개인정보 보호법"))
        assertTrue(context.contains("[판례] 2012다105482"))
        assertTrue(context.contains("제18조"))
    }
}
