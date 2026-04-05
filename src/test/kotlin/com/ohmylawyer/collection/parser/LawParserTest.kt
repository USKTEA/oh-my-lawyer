package com.ohmylawyer.collection.parser

import com.fasterxml.jackson.databind.ObjectMapper
import com.ohmylawyer.domain.entity.ChunkType
import com.ohmylawyer.domain.entity.DocumentType
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LawParserTest {

    private val parser = LawParser()
    private val mapper = ObjectMapper()

    @Test
    fun `parseTotalCount extracts total from search result`() {
        val json = mapper.readTree("""
            {"LawSearch": {"totalCnt": 64, "law": []}}
        """)
        assertEquals(64, parser.parseTotalCount(json))
    }

    @Test
    fun `parseTotalCount returns 0 for empty response`() {
        val json = mapper.readTree("{}")
        assertEquals(0, parser.parseTotalCount(json))
    }

    @Test
    fun `parseSearchItems extracts array of items`() {
        val json = mapper.readTree("""
            {"LawSearch": {"totalCnt": 2, "law": [
                {"법령ID": "011357", "법령명한글": "개인정보 보호법"},
                {"법령ID": "011468", "법령명한글": "개인정보 보호법 시행령"}
            ]}}
        """)
        val items = parser.parseSearchItems(json)
        assertEquals(2, items.size)
        assertEquals("011357", items[0].path("법령ID").asText())
    }

    @Test
    fun `parseSearchItems handles single item as object`() {
        val json = mapper.readTree("""
            {"LawSearch": {"totalCnt": 1, "law": {"법령ID": "011357", "법령명한글": "개인정보 보호법"}}}
        """)
        val items = parser.parseSearchItems(json)
        assertEquals(1, items.size)
    }

    @Test
    fun `parseItemId extracts 법령일련번호`() {
        val item = mapper.readTree("""{"법령일련번호": "253527", "법령ID": "011357", "법령명한글": "개인정보 보호법"}""")
        assertEquals("253527", parser.parseItemId(item))
    }

    @Test
    fun `parseDetail creates document with articles as chunks`() {
        val searchItem = mapper.readTree("""{"법령일련번호": "253527", "법령ID": "011357", "현행연혁코드": "현행", "법령명한글": "개인정보 보호법"}""")
        val detail = mapper.readTree("""
            {"법령": {
                "기본정보": {
                    "법령명_한글": "개인정보 보호법",
                    "법령명_약칭": "개인정보보호법",
                    "법종구분": "법률",
                    "소관부처명": "개인정보보호위원회",
                    "시행일자": "20240315",
                    "공포일자": "20230914"
                },
                "조문": {"조문단위": [
                    {"조문번호": "1", "조문제목": "목적", "조문내용": "이 법은 개인정보의 처리 및 보호에 관한 사항을 정함으로써..."},
                    {"조문번호": "2", "조문제목": "정의", "조문내용": "이 법에서 사용하는 용어의 뜻은 다음과 같다."}
                ]}
            }}
        """)

        val result = parser.parseDetail(searchItem, detail)

        assertEquals(DocumentType.LAW, result.type)
        assertEquals("개인정보 보호법", result.title)
        assertEquals("253527", result.sourceId)
        assertEquals(LocalDate.of(2024, 3, 15), result.enactedDate)
        assertEquals(LocalDate.of(2023, 9, 14), result.lastAmended)
        assertEquals(2, result.chunks.size)
        assertTrue(result.chunks.all { it.chunkType == ChunkType.ARTICLE })
        assertTrue(result.chunks[0].content.contains("제1조(목적)"))
        assertTrue(result.chunks[1].content.contains("제2조(정의)"))
    }

    @Test
    fun `parseDetail handles articles with paragraphs and sub-items`() {
        val searchItem = mapper.readTree("""{"법령일련번호": "253527", "법령ID": "011357"}""")
        val detail = mapper.readTree("""
            {"법령": {
                "기본정보": {"법령명_한글": "테스트법"},
                "조문": {"조문단위": [
                    {"조문번호": "1", "조문제목": "목적", "조문내용": "이 법은...",
                     "항": [
                        {"항내용": "① 첫째 항", "호": [
                            {"호내용": "1. 첫째 호"},
                            {"호내용": "2. 둘째 호"}
                        ]},
                        {"항내용": "② 둘째 항"}
                     ]}
                ]}
            }}
        """)

        val result = parser.parseDetail(searchItem, detail)

        assertEquals(1, result.chunks.size)
        val content = result.chunks[0].content
        assertTrue(content.contains("① 첫째 항"))
        assertTrue(content.contains("1. 첫째 호"))
        assertTrue(content.contains("② 둘째 항"))
    }

    @Test
    fun `parseDetail handles empty article list`() {
        val searchItem = mapper.readTree("""{"법령ID": "011471"}""")
        val detail = mapper.readTree("""
            {"법령": {
                "기본정보": {"법령명_한글": "개인정보 보호법 시행규칙"},
                "조문": {"조문단위": []}
            }}
        """)

        val result = parser.parseDetail(searchItem, detail)

        assertEquals("개인정보 보호법 시행규칙", result.title)
        assertTrue(result.chunks.isEmpty())
    }

    @Test
    fun `buildArticleContent formats article correctly`() {
        val article = mapper.readTree("""
            {"조문번호": "18", "조문제목": "개인정보의 수집 제한", "조문내용": "개인정보처리자는 정보주체의 동의를 받아..."}
        """)

        val content = parser.buildArticleContent(article)

        assertTrue(content.startsWith("제18조(개인정보의 수집 제한)"))
        assertTrue(content.contains("개인정보처리자는"))
    }
}
