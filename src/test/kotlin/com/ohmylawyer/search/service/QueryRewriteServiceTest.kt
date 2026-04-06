package com.ohmylawyer.search.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QueryRewriteServiceTest {

    @Test
    fun `parseQueries - valid JSON array returns list of queries`() {
        val json = """["수사기관 사실조회 개인정보 제3자 제공", "개인정보보호법 수사목적 예외"]"""

        val result = QueryRewriteService.parseQueries(json)

        assertEquals(2, result.size)
        assertEquals("수사기관 사실조회 개인정보 제3자 제공", result[0])
        assertEquals("개인정보보호법 수사목적 예외", result[1])
    }

    @Test
    fun `parseQueries - JSON with markdown code block fences`() {
        val json = """
            ```json
            ["공동주택 관리비 인상 절차", "공동주택관리법 관리비 변경 의결"]
            ```
        """.trimIndent()

        val result = QueryRewriteService.parseQueries(json)

        assertEquals(2, result.size)
        assertEquals("공동주택 관리비 인상 절차", result[0])
    }

    @Test
    fun `parseQueries - empty array returns empty list`() {
        val json = """[]"""

        val result = QueryRewriteService.parseQueries(json)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseQueries - malformed JSON returns empty list`() {
        val json = """this is not json"""

        val result = QueryRewriteService.parseQueries(json)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseQueries - JSON object instead of array returns empty list`() {
        val json = """{"queries": ["test"]}"""

        val result = QueryRewriteService.parseQueries(json)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseQueries - filters out blank strings`() {
        val json = """["valid query", "", "  ", "another query"]"""

        val result = QueryRewriteService.parseQueries(json)

        assertEquals(2, result.size)
        assertEquals("valid query", result[0])
        assertEquals("another query", result[1])
    }

    @Test
    fun `buildPrompt - contains user query`() {
        val prompt = QueryRewriteService.buildPrompt("입주민 정보 경찰에 줘도 되나?")

        assertTrue(prompt.contains("입주민 정보 경찰에 줘도 되나?"))
    }

    @Test
    fun `buildPrompt - contains few-shot examples`() {
        val prompt = QueryRewriteService.buildPrompt("테스트 질문")

        assertTrue(prompt.contains("수사기관"), "should contain legal term example")
        assertTrue(prompt.contains("JSON"), "should instruct JSON output format")
    }
}
