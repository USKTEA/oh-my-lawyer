package com.ohmylawyer.collection.parser

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextChunkerTest {

    @Test
    fun `short text returns single chunk`() {
        val text = "짧은 텍스트입니다."
        val chunks = TextChunker.chunkWithOverlap(text)
        assertEquals(1, chunks.size)
        assertEquals(text, chunks[0])
    }

    @Test
    fun `empty text returns empty list`() {
        val chunks = TextChunker.chunkWithOverlap("")
        assertTrue(chunks.isEmpty())
    }

    @Test
    fun `long text splits at sentence boundaries`() {
        val sentence = "이 법은 개인정보의 처리 및 보호에 관한 사항을 정함으로써 국민의 권리를 보호합니다."
        val text = (1..50).joinToString(" ") { sentence }

        val chunks = TextChunker.chunkWithOverlap(text, maxChunkSize = 500, overlapSize = 100)

        assertTrue(chunks.size > 1)
        chunks.forEach { chunk ->
            assertTrue(chunk.length <= 600, "Chunk too large: ${chunk.length}")
        }
    }

    @Test
    fun `chunks have overlap content`() {
        val sentences = (1..20).map { "문장 번호 $it 이것은 테스트 문장입니다." }
        val text = sentences.joinToString(" ")

        val chunks = TextChunker.chunkWithOverlap(text, maxChunkSize = 200, overlapSize = 50)

        assertTrue(chunks.size > 1)
        for (i in 0 until chunks.size - 1) {
            val currentEnd = chunks[i].takeLast(30)
            val nextStart = chunks[i + 1].take(200)
            assertTrue(
                nextStart.contains(currentEnd.take(15)) || chunks[i].last() == '.',
                "No overlap found between chunk $i and ${i + 1}"
            )
        }
    }

    // === Bug reproduction tests ===

    @Test
    fun `force-split without sentence boundaries should not create 1-char-offset duplicates`() {
        // Bug: 문장 경계 없는 긴 텍스트에서 마지막 부분이 overlap보다 작을 때
        // 1글자씩 전진하며 거의 동일한 chunk가 수백 개 생성됨
        val text = "가".repeat(5000)
        val chunks = TextChunker.chunkWithOverlap(text, maxChunkSize = 2500, overlapSize = 500)

        // 5000자 / 2500자 = 최대 3~4개 chunk가 합리적
        assertTrue(chunks.size <= 5, "Too many chunks: ${chunks.size} (expected <=5 for 5000 chars)")
        assertTrue(chunks.isNotEmpty())
    }

    @Test
    fun `should not create tiny chunks like single characters or periods`() {
        // Bug: "다." 나 "." 같은 초소형 chunk가 생성됨
        val text = buildString {
            repeat(10) {
                append("이것은 법률 해석에 관한 긴 문장으로 여러 논점을 다루고 있다.")
                append("관련 판례에 따르면 이 사안은 복잡한 법적 판단이 필요함.")
                append(" ")
            }
        }

        val chunks = TextChunker.chunkWithOverlap(text, maxChunkSize = 200, overlapSize = 50)

        chunks.forEach { chunk ->
            assertTrue(chunk.length >= 20, "Chunk too small (${chunk.length} chars): '$chunk'")
        }
    }

    @Test
    fun `force-split large text without sentence boundaries produces reasonable chunk count`() {
        // 실제 헌재결정 전문 시뮬레이션: 25만 글자, 문장 종결 패턴 없음
        val text = "헌법재판소결정문내용" .repeat(25000) // 250,000자
        val chunks = TextChunker.chunkWithOverlap(text, maxChunkSize = 2500, overlapSize = 500)

        // 250,000 / (2500-500) = 125개가 이론적 최대치
        assertTrue(chunks.size <= 150, "Too many chunks: ${chunks.size} (expected <=150)")
        assertTrue(chunks.size >= 50, "Too few chunks: ${chunks.size}")

        // 모든 chunk가 maxChunkSize를 크게 초과하지 않아야 함
        chunks.forEach { chunk ->
            assertTrue(chunk.length <= 2600, "Chunk exceeds max size: ${chunk.length}")
        }
    }

    @Test
    fun `all original text is covered by chunks`() {
        val text = "이것은 테스트 문장이다." .repeat(100)
        val chunks = TextChunker.chunkWithOverlap(text, maxChunkSize = 300, overlapSize = 50)

        // 원문의 첫/마지막 부분이 chunk에 포함되어야 함
        assertTrue(chunks.first().startsWith("이것은"), "First chunk should start with original text start")
        assertTrue(chunks.last().endsWith("문장이다."), "Last chunk should end with original text end")
    }

    @Test
    fun `legal text with Korean sentence endings splits correctly`() {
        val text = """
            청구인은 다음과 같은 이유로 이 사건 헌법소원의 심판을 청구하였다.
            헌법소원심판은 공권력의 행사 또는 불행사로 인하여 헌법상 보장된 기본권을 침해받은 자에 대한 권리구제를 위한 것이다.
            따라서 이 사건 헌법소원심판의 청구는 부적법한 청구라 할 것이므로 이를 각하하기로 한다.
            이 결정은 관여재판관 전원의 의견일치에 따른 것이다.
        """.trimIndent()

        val chunks = TextChunker.chunkWithOverlap(text, maxChunkSize = 100, overlapSize = 30)

        assertTrue(chunks.size > 1)
        chunks.forEach { chunk ->
            assertTrue(chunk.isNotBlank())
        }
    }
}
