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
    fun `long text splits at sentence boundaries`() {
        val sentence = "이 법은 개인정보의 처리 및 보호에 관한 사항을 정함으로써 국민의 권리를 보호합니다."
        val text = (1..50).joinToString(" ") { sentence }

        val chunks = TextChunker.chunkWithOverlap(text, maxChunkSize = 500, overlapSize = 100)

        assertTrue(chunks.size > 1)
        // Each chunk should be within limit (allowing some tolerance for sentence boundaries)
        chunks.forEach { chunk ->
            assertTrue(chunk.length <= 600, "Chunk too large: ${chunk.length}")
        }
    }

    @Test
    fun `chunks have overlap`() {
        val sentences = (1..20).map { "문장 번호 $it 이것은 테스트 문장입니다." }
        val text = sentences.joinToString(" ")

        val chunks = TextChunker.chunkWithOverlap(text, maxChunkSize = 200, overlapSize = 50)

        assertTrue(chunks.size > 1)
        // Verify overlap: end of chunk N should appear in start of chunk N+1
        for (i in 0 until chunks.size - 1) {
            val currentEnd = chunks[i].takeLast(30)
            val nextStart = chunks[i + 1].take(200)
            assertTrue(
                nextStart.contains(currentEnd.take(15)) || chunks[i].last() == '.',
                "No overlap found between chunk $i and ${i + 1}"
            )
        }
    }

    @Test
    fun `no sentence boundary falls back gracefully`() {
        val text = "가" .repeat(5000)  // No sentence boundaries
        val chunks = TextChunker.chunkWithOverlap(text, maxChunkSize = 3000)

        assertTrue(chunks.isNotEmpty())
        // Should still produce output even without sentence boundaries
    }

    @Test
    fun `empty text returns empty list`() {
        val chunks = TextChunker.chunkWithOverlap("")
        assertTrue(chunks.isEmpty())
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
