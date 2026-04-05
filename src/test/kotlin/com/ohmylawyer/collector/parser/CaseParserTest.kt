package com.ohmylawyer.collector.parser

import com.fasterxml.jackson.databind.ObjectMapper
import com.ohmylawyer.domain.entity.ChunkType
import com.ohmylawyer.domain.entity.DocumentType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CaseParserTest {

    private val parser = CaseParser()
    private val mapper = ObjectMapper()

    @Test
    fun `parseTotalCount extracts total from search result`() {
        val json = mapper.readTree("""{"PrecSearch": {"totalCnt": 93, "prec": []}}""")
        assertEquals(93, parser.parseTotalCount(json))
    }

    @Test
    fun `parseSearchItems handles single item as object`() {
        val json = mapper.readTree("""
            {"PrecSearch": {"totalCnt": 1, "prec": {"판례일련번호": "154389", "사건명": "테스트"}}}
        """)
        assertEquals(1, parser.parseSearchItems(json).size)
    }

    @Test
    fun `parseItemId extracts 판례일련번호`() {
        val item = mapper.readTree("""{"판례일련번호": "154389", "사건명": "테스트"}""")
        assertEquals("154389", parser.parseItemId(item))
    }

    @Test
    fun `parseDetail creates document with summary and holding chunks`() {
        val searchItem = mapper.readTree("""{"판례일련번호": "154389", "사건명": "정보통신망위반", "사건번호": "2011도1960"}""")
        val detail = mapper.readTree("""
            {"PrecService": {
                "사건명": "정보통신망위반",
                "사건번호": "2011도1960",
                "법원명": "대법원",
                "선고일자": "20110714",
                "사건종류명": "형사",
                "판결유형": "판결",
                "판시사항": "개인정보 보호에 관한 판시사항<br/>두번째 줄",
                "판결요지": "판결요지 내용",
                "참조조문": "개인정보보호법 제18조",
                "참조판례": "",
                "판례내용": "전문 내용입니다<br/>줄바꿈 포함"
            }}
        """)

        val result = parser.parseDetail(searchItem, detail)

        assertEquals(DocumentType.CASE, result.type)
        assertEquals("정보통신망위반 (2011도1960)", result.title)
        assertEquals("154389", result.sourceId)
        assertTrue(result.metadata.contains("대법원"))

        // SUMMARY chunk with holding + summary
        val summaryChunk = result.chunks.first { it.chunkType == ChunkType.SUMMARY }
        assertTrue(summaryChunk.content.contains("[판시사항]"))
        assertTrue(summaryChunk.content.contains("[판결요지]"))

        // HTML <br/> should be stripped
        assertTrue(!summaryChunk.content.contains("<br/>"))
        assertTrue(summaryChunk.content.contains("두번째 줄"))

        // HOLDING chunk with full text
        val holdingChunk = result.chunks.first { it.chunkType == ChunkType.HOLDING }
        assertTrue(holdingChunk.content.contains("전문 내용입니다"))
        assertTrue(!holdingChunk.content.contains("<br/>"))
    }

    @Test
    fun `parseDetail handles empty holding and summary`() {
        val searchItem = mapper.readTree("""{"판례일련번호": "999", "사건번호": "2020도123"}""")
        val detail = mapper.readTree("""
            {"PrecService": {
                "사건명": "테스트",
                "사건번호": "2020도123",
                "법원명": "대법원",
                "판시사항": "",
                "판결요지": "",
                "판례내용": "전문만 있는 판례"
            }}
        """)

        val result = parser.parseDetail(searchItem, detail)

        // No SUMMARY chunk when holding and summary are empty
        assertTrue(result.chunks.none { it.chunkType == ChunkType.SUMMARY })
        assertEquals(1, result.chunks.size)
        assertEquals(ChunkType.HOLDING, result.chunks[0].chunkType)
    }

    @Test
    fun `splitCaseText splits long text by sections when exceeding max size`() {
        val text = "서론【이유】이유 내용【주문】주문"

        val sections = parser.splitCaseText(text, maxChunkSize = 5)

        assertTrue(sections.size >= 3)
        assertTrue(sections.any { it.contains("서론") })
        assertTrue(sections.any { it.contains("【이유】") })
        assertTrue(sections.any { it.contains("【주문】") })
    }

    @Test
    fun `splitCaseText returns single item for short text`() {
        val text = "짧은 판례 내용"
        assertEquals(listOf(text), parser.splitCaseText(text))
    }
}
