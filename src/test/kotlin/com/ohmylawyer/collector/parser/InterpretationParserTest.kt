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
}
