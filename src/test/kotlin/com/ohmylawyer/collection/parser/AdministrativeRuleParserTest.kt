package com.ohmylawyer.collection.parser

import com.fasterxml.jackson.databind.ObjectMapper
import com.ohmylawyer.domain.entity.ChunkType
import com.ohmylawyer.domain.entity.DocumentType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdministrativeRuleParserTest {

    private val parser = AdministrativeRuleParser()
    private val mapper = ObjectMapper()

    @Test
    fun `parseTotalCount extracts from AdmRulSearch`() {
        val json = mapper.readTree("""{"AdmRulSearch": {"totalCnt": 23840}}""")
        assertEquals(23840, parser.parseTotalCount(json))
    }

    @Test
    fun `parseSearchItems extracts admrul items`() {
        val json = mapper.readTree("""
            {"AdmRulSearch": {"totalCnt": 1, "admrul": {"행정규칙일련번호": "2100000264562", "행정규칙명": "테스트 규정"}}}
        """)
        val items = parser.parseSearchItems(json)
        assertEquals(1, items.size)
        assertEquals("2100000264562", parser.parseItemId(items[0]))
    }

    @Test
    fun `parseDetail creates document with article chunks`() {
        val searchItem = mapper.readTree("""
            {"행정규칙일련번호": "2100000264562", "행정규칙명": "개인정보 처리 지침",
             "행정규칙종류": "고시", "소관부처명": "개인정보보호위원회",
             "시행일자": "20250924", "행정규칙ID": "93857", "제개정구분명": "일부개정"}
        """)
        val detail = mapper.readTree("""
            {"AdmRulService": {
                "행정규칙기본정보": {"행정규칙명": "개인정보 처리 지침"},
                "조문내용": [
                    {"조문번호": "1", "조문내용": "제1조 목적 내용"},
                    {"조문번호": "2", "조문내용": "제2조 정의 내용"}
                ]
            }}
        """)

        val result = parser.parseDetail(searchItem, detail)

        assertEquals(DocumentType.ADMINISTRATIVE_RULE, result.type)
        assertEquals("개인정보 처리 지침", result.title)
        assertEquals("2100000264562", result.sourceId)
        assertTrue(result.metadata.contains("고시"))
        assertTrue(result.metadata.contains("개인정보보호위원회"))

        assertEquals(2, result.chunks.size)
        assertTrue(result.chunks.all { it.chunkType == ChunkType.ARTICLE })
        assertTrue(result.chunks[0].content.contains("제1조"))
        assertTrue(result.chunks[1].content.contains("제2조"))
    }

    @Test
    fun `parseDetail handles single article as object`() {
        val searchItem = mapper.readTree("""{"행정규칙일련번호": "123", "행정규칙명": "단일 조문 규정", "시행일자": "20250101"}""")
        val detail = mapper.readTree("""
            {"AdmRulService": {
                "행정규칙기본정보": {},
                "조문내용": {"조문번호": "1", "조문내용": "유일한 조문"}
            }}
        """)

        val result = parser.parseDetail(searchItem, detail)

        assertEquals(1, result.chunks.size)
        assertTrue(result.chunks[0].content.contains("유일한 조문"))
    }

    @Test
    fun `parseDetail strips HTML br tags from content`() {
        val searchItem = mapper.readTree("""{"행정규칙일련번호": "456", "행정규칙명": "HTML 테스트"}""")
        val detail = mapper.readTree("""
            {"AdmRulService": {
                "행정규칙기본정보": {},
                "조문내용": [{"조문번호": "1", "조문내용": "첫줄<br/>둘째줄<br>셋째줄"}]
            }}
        """)

        val result = parser.parseDetail(searchItem, detail)

        assertTrue(!result.chunks[0].content.contains("<br"))
        assertTrue(result.chunks[0].content.contains("첫줄\n둘째줄\n셋째줄"))
    }

    @Test
    fun `parseTotalCount returns 0 for empty response`() {
        val json = mapper.readTree("{}")
        assertEquals(0, parser.parseTotalCount(json))
    }

    @Test
    fun `parseDetail handles empty article list`() {
        val searchItem = mapper.readTree("""{"행정규칙일련번호": "789", "행정규칙명": "빈 조문 규정", "시행일자": "20250101"}""")
        val detail = mapper.readTree("""
            {"AdmRulService": {
                "행정규칙기본정보": {},
                "조문내용": []
            }}
        """)

        val result = parser.parseDetail(searchItem, detail)

        assertTrue(result.chunks.isEmpty())
    }

    @Test
    fun `parseDetail handles missing 조문내용 key`() {
        val searchItem = mapper.readTree("""{"행정규칙일련번호": "790", "행정규칙명": "조문없는 규정", "시행일자": "20250101"}""")
        val detail = mapper.readTree("""
            {"AdmRulService": {
                "행정규칙기본정보": {}
            }}
        """)

        val result = parser.parseDetail(searchItem, detail)

        assertTrue(result.chunks.isEmpty())
    }
}
