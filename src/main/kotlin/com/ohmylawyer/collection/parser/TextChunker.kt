package com.ohmylawyer.collection.parser

object TextChunker {

    private const val DEFAULT_MAX_CHUNK_SIZE = 2500
    private const val DEFAULT_OVERLAP_SIZE = 500

    private val SENTENCE_END_PATTERN = Regex("""(?<=[다됨음임요죠함])\.(?:\s|$)""")

    fun chunkWithOverlap(
        text: String,
        maxChunkSize: Int = DEFAULT_MAX_CHUNK_SIZE,
        overlapSize: Int = DEFAULT_OVERLAP_SIZE
    ): List<String> {
        if (text.isBlank()) return emptyList()
        if (text.length <= maxChunkSize) return listOf(text)

        val sentences = splitSentences(text)
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()
        var overlapBuffer = mutableListOf<String>()

        for (sentence in sentences) {
            // Fallback: if a single sentence exceeds maxChunkSize, force-split it
            if (sentence.length > maxChunkSize) {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString().trim())
                    currentChunk = StringBuilder()
                    overlapBuffer = mutableListOf()
                }
                var start = 0
                while (start < sentence.length) {
                    val end = (start + maxChunkSize).coerceAtMost(sentence.length)
                    chunks.add(sentence.substring(start, end).trim())
                    start = (end - overlapSize).coerceAtLeast(start + 1)
                }
                continue
            }

            if (currentChunk.length + sentence.length > maxChunkSize && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString().trim())

                // Build overlap from recent sentences
                currentChunk = StringBuilder()
                var overlapLen = 0
                for (prev in overlapBuffer.reversed()) {
                    if (overlapLen + prev.length > overlapSize) break
                    currentChunk.insert(0, prev)
                    overlapLen += prev.length
                }
                overlapBuffer = mutableListOf()
            }

            currentChunk.append(sentence)
            overlapBuffer.add(sentence)

            // Keep overlap buffer from growing too large
            while (overlapBuffer.sumOf { it.length } > overlapSize * 2) {
                overlapBuffer.removeFirst()
            }
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }

        return chunks.filter { it.isNotBlank() }
    }

    private fun splitSentences(text: String): List<String> {
        val sentences = mutableListOf<String>()
        var lastEnd = 0

        for (match in SENTENCE_END_PATTERN.findAll(text)) {
            val end = match.range.last + 1
            sentences.add(text.substring(lastEnd, end))
            lastEnd = end
        }

        // Remaining text
        if (lastEnd < text.length) {
            sentences.add(text.substring(lastEnd))
        }

        return sentences
    }
}
