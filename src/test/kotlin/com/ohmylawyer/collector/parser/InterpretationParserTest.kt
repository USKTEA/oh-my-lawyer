package com.ohmylawyer.collector.parser

import com.fasterxml.jackson.databind.ObjectMapper
import com.ohmylawyer.domain.entity.ChunkType
import com.ohmylawyer.domain.entity.DocumentType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InterpretationParserTest {

    private val parser = InterpretationParser()
    private val mapper = ObjectMapper()

    @Test
    fun `parseTotalCount extracts from Expc`() {
        val json = mapper.readTree("""{"Expc": {"totalCnt": 8675}}""")
        assertEquals(8675, parser.parseTotalCount(json))
    }

    @Test
    fun `parseSearchItems extracts expc items`() {
        val json = mapper.readTree("""
            {"Expc": {"totalCnt": 1, "expc": {"법령해석례일련번호": "313107", "안건명": "테스트 해석례"}}}
        """)
        val items = parser.parseSearchItems(json)
        assertEquals(1, items.size)
        assertEquals("313107", parser.parseItemId(items[0]))
    }

    @Test
    fun `parseDetail creates document with interpretation body chunk`() {
        val searchItem = mapper.readTree("""{"법령해석례일련번호": "313107", "안건명": "퇴직급여금 지급", "안건번호": "05-0096"}""")
        val detail = mapper.readTree("""
            {"ExpcService": {
                "안건명": "퇴직급여금 지급",
                "안건번호": "05-0096",
                "해석일자": "20051223",
                "해석기관명": "법제처",
                "질의기관명": "국방부",
                "질의요지": "퇴직급여금 지급 관련 질의",
                "회답": "해당 규정에 따라 지급 가능",
                "이유": "관련 법령 검토 결과..."
            }}
        """)

        val result = parser.parseDetail(searchItem, detail)

        assertEquals(DocumentType.INTERPRETATION, result.type)
        assertEquals("퇴직급여금 지급 (05-0096)", result.title)
        assertEquals("313107", result.sourceId)
        assertTrue(result.metadata.contains("법제처"))
        assertTrue(result.metadata.contains("국방부"))

        assertEquals(1, result.chunks.size)
        val chunk = result.chunks[0]
        assertEquals(ChunkType.INTERPRETATION_BODY, chunk.chunkType)
        assertTrue(chunk.content.contains("[질의요지]"))
        assertTrue(chunk.content.contains("[회답]"))
        assertTrue(chunk.content.contains("[이유]"))
    }

    @Test
    fun `parseDetail produces empty chunks when all fields are blank`() {
        val searchItem = mapper.readTree("""{"법령해석례일련번호": "000", "안건명": "빈 해석례", "안건번호": "00-0000"}""")
        val detail = mapper.readTree("""
            {"ExpcService": {
                "안건명": "빈 해석례",
                "안건번호": "00-0000",
                "질의요지": "",
                "회답": "",
                "이유": ""
            }}
        """)

        val result = parser.parseDetail(searchItem, detail)

        assertTrue(result.chunks.isEmpty())
    }

    @Test
    fun `parseSearchItems extracts array of items`() {
        val json = mapper.readTree("""
            {"Expc": {"totalCnt": 2, "expc": [
                {"법령해석례일련번호": "313107", "안건명": "퇴직급여금 지급"},
                {"법령해석례일련번호": "313108", "안건명": "복직 관련 해석"}
            ]}}
        """)
        val items = parser.parseSearchItems(json)
        assertEquals(2, items.size)
        assertEquals("313107", parser.parseItemId(items[0]))
        assertEquals("313108", parser.parseItemId(items[1]))
    }

    @Test
    fun `parseTotalCount returns 0 for empty response`() {
        val json = mapper.readTree("{}")
        assertEquals(0, parser.parseTotalCount(json))
    }

    @Test
    fun `parseDetail strips br tags from question answer and reason`() {
        val searchItem = mapper.readTree("""{"법령해석례일련번호": "444", "안건명": "HTML 테스트", "안건번호": "06-0001"}""")
        val detail = mapper.readTree("""
            {"ExpcService": {
                "안건명": "HTML 테스트",
                "안건번호": "06-0001",
                "질의요지": "질의 첫줄<br/>질의 둘째줄",
                "회답": "회답 내용<br>회답 둘째줄",
                "이유": "이유 내용"
            }}
        """)

        val result = parser.parseDetail(searchItem, detail)

        assertEquals(1, result.chunks.size)
        val content = result.chunks[0].content
        assertTrue(!content.contains("<br"))
        assertTrue(content.contains("질의 둘째줄"))
        assertTrue(content.contains("회답 둘째줄"))
    }
}
