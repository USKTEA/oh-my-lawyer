package com.ohmylawyer.collection.parser

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

    @Test
    fun `parseDetail handles empty holding and summary with only full text`() {
        val searchItem = mapper.readTree("""{"헌재결정례일련번호": "999", "사건번호": "2021헌바100", "사건명": "위헌확인"}""")
        val detail = mapper.readTree("""
            {"DetcService": {
                "사건명": "위헌확인",
                "사건번호": "2021헌바100",
                "판시사항": "",
                "결정요지": "",
                "전문": "전문만 있는 헌재 결정 내용"
            }}
        """)

        val result = parser.parseDetail(searchItem, detail)

        // No SUMMARY chunk when holding and summary are empty
        assertTrue(result.chunks.none { it.chunkType == ChunkType.SUMMARY })
        assertEquals(1, result.chunks.size)
        assertEquals(ChunkType.HOLDING, result.chunks[0].chunkType)
        assertTrue(result.chunks[0].content.contains("전문만 있는 헌재 결정 내용"))
    }

    @Test
    fun `parseSearchItems extracts array of items`() {
        val json = mapper.readTree("""
            {"DetcSearch": {"totalCnt": 2, "Detc": [
                {"헌재결정례일련번호": "57476", "사건번호": "2017헌바323", "사건명": "위헌소원"},
                {"헌재결정례일련번호": "57477", "사건번호": "2018헌바100", "사건명": "헌법소원"}
            ]}}
        """)
        val items = parser.parseSearchItems(json)
        assertEquals(2, items.size)
        assertEquals("57476", parser.parseItemId(items[0]))
        assertEquals("57477", parser.parseItemId(items[1]))
    }

    @Test
    fun `parseTotalCount returns 0 for empty response`() {
        val json = mapper.readTree("{}")
        assertEquals(0, parser.parseTotalCount(json))
    }

    @Test
    fun `parseDetail strips br tags from holding and summary`() {
        val searchItem = mapper.readTree("""{"헌재결정례일련번호": "111", "사건번호": "2020헌바1", "사건명": "HTML테스트"}""")
        val detail = mapper.readTree("""
            {"DetcService": {
                "사건명": "HTML테스트",
                "사건번호": "2020헌바1",
                "판시사항": "첫번째 사항<br/>두번째 사항",
                "결정요지": "결정요지 첫줄<br>결정요지 둘째줄",
                "전문": "전문 내용"
            }}
        """)

        val result = parser.parseDetail(searchItem, detail)

        val summaryChunk = result.chunks.first { it.chunkType == ChunkType.SUMMARY }
        assertTrue(!summaryChunk.content.contains("<br"))
        assertTrue(summaryChunk.content.contains("두번째 사항"))
        assertTrue(summaryChunk.content.contains("결정요지 둘째줄"))
    }

    @Test
    fun `parseDetail creates multiple holding chunks when full text exceeds 4000 characters`() {
        val longText = "가".repeat(4001)
        val searchItem = mapper.readTree("""{"헌재결정례일련번호": "222", "사건번호": "2019헌바99", "사건명": "긴전문"}""")
        val detail = mapper.readTree("""
            {"DetcService": {
                "사건명": "긴전문",
                "사건번호": "2019헌바99",
                "판시사항": "",
                "결정요지": "",
                "전문": "$longText"
            }}
        """)

        val result = parser.parseDetail(searchItem, detail)

        val holdingChunks = result.chunks.filter { it.chunkType == ChunkType.HOLDING }
        assertTrue(holdingChunks.size >= 2)
    }
}
