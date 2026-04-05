package com.ohmylawyer.collector.parser

import com.fasterxml.jackson.databind.ObjectMapper
import com.ohmylawyer.domain.entity.ChunkType
import com.ohmylawyer.domain.entity.DocumentType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConstitutionalParserTest {

    private val parser = ConstitutionalParser()
    private val mapper = ObjectMapper()

    @Test
    fun `parseTotalCount extracts from DetcSearch`() {
        val json = mapper.readTree("""{"DetcSearch": {"totalCnt": 37577}}""")
        assertEquals(37577, parser.parseTotalCount(json))
    }

    @Test
    fun `parseSearchItems extracts Detc items`() {
        val json = mapper.readTree("""
            {"DetcSearch": {"totalCnt": 1, "Detc": {"헌재결정례일련번호": "57476", "사건번호": "2017헌바323", "사건명": "테스트"}}}
        """)
        val items = parser.parseSearchItems(json)
        assertEquals(1, items.size)
        assertEquals("57476", parser.parseItemId(items[0]))
    }

    @Test
    fun `parseDetail creates document with summary and holding chunks`() {
        val searchItem = mapper.readTree("""{"헌재결정례일련번호": "57476", "사건번호": "2017헌바323", "사건명": "위헌소원"}""")
        val detail = mapper.readTree("""
            {"DetcService": {
                "사건명": "위헌소원",
                "사건번호": "2017헌바323",
                "사건종류명": "헌법소원",
                "종국일자": "20180830",
                "판시사항": "헌재 판시사항",
                "결정요지": "헌재 결정요지",
                "전문": "헌재결정 전문 내용",
                "참조조문": "헌법 제10조",
                "참조판례": "",
                "심판대상조문": "형사소송법 제199조"
            }}
        """)

        val result = parser.parseDetail(searchItem, detail)

        assertEquals(DocumentType.CONSTITUTIONAL, result.type)
        assertEquals("위헌소원 (2017헌바323)", result.title)
        assertEquals("57476", result.sourceId)

        val summaryChunk = result.chunks.first { it.chunkType == ChunkType.SUMMARY }
        assertTrue(summaryChunk.content.contains("[판시사항]"))
        assertTrue(summaryChunk.content.contains("[결정요지]"))

        val holdingChunk = result.chunks.first { it.chunkType == ChunkType.HOLDING }
        assertTrue(holdingChunk.content.contains("헌재결정 전문 내용"))

        assertTrue(result.metadata.contains("2017헌바323"))
        assertTrue(result.metadata.contains("형사소송법 제199조"))
    }
}
