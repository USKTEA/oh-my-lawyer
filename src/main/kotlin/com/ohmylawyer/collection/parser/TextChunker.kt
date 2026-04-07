package com.ohmylawyer.collection.parser

object TextChunker {
    private const val DEFAULT_MAX_CHUNK_SIZE = 2500
    private const val DEFAULT_OVERLAP_SIZE = 300
    private const val MIN_CHUNK_SIZE = 20

    private val SENTENCE_END_PATTERN = Regex("""(?<=[다됨음임요죠함])\.(?:\s|$)""")

    fun chunkWithOverlap(
        text: String,
        maxChunkSize: Int = DEFAULT_MAX_CHUNK_SIZE,
        overlapSize: Int = DEFAULT_OVERLAP_SIZE,
    ): List<String> {
        if (text.isBlank()) return emptyList()
        if (text.length <= maxChunkSize) return listOf(text)

        val sentences = splitSentences(text)
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()
        var overlapBuffer = mutableListOf<String>()

        for (sentence in sentences) {
            if (sentence.length > maxChunkSize) {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString().trim())
                    currentChunk = StringBuilder()
                    overlapBuffer = mutableListOf()
                }
                forceSplit(sentence, maxChunkSize, overlapSize, chunks)
                continue
            }

            if (currentChunk.length + sentence.length > maxChunkSize && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString().trim())

                currentChunk = StringBuilder()
                val newOverlapBuffer = mutableListOf<String>()
                var overlapLen = 0
                for (prev in overlapBuffer.reversed()) {
                    if (overlapLen + prev.length > overlapSize) break
                    currentChunk.insert(0, prev)
                    newOverlapBuffer.add(0, prev)
                    overlapLen += prev.length
                }
                overlapBuffer = newOverlapBuffer
            }

            currentChunk.append(sentence)
            overlapBuffer.add(sentence)

            while (overlapBuffer.sumOf { it.length } > overlapSize * 2) {
                overlapBuffer.removeFirst()
            }
        }

        if (currentChunk.isNotEmpty()) {
            val remaining = currentChunk.toString().trim()
            if (remaining.length >= MIN_CHUNK_SIZE) {
                chunks.add(remaining)
            } else if (chunks.isNotEmpty()) {
                chunks[chunks.lastIndex] = chunks.last() + " " + remaining
            }
        }

        return chunks.filter { it.length >= MIN_CHUNK_SIZE }
    }

    private fun forceSplit(
        text: String,
        maxChunkSize: Int,
        overlapSize: Int,
        chunks: MutableList<String>,
    ) {
        val step = maxChunkSize - overlapSize
        var start = 0
        while (start < text.length) {
            val end = (start + maxChunkSize).coerceAtMost(text.length)
            val chunk = text.substring(start, end).trim()
            if (chunk.length >= MIN_CHUNK_SIZE) {
                chunks.add(chunk)
            }
            start += step
        }
    }

    private fun splitSentences(text: String): List<String> {
        val sentences = mutableListOf<String>()
        var lastEnd = 0

        for (match in SENTENCE_END_PATTERN.findAll(text)) {
            val end = match.range.last + 1
            sentences.add(text.substring(lastEnd, end))
            lastEnd = end
        }

        if (lastEnd < text.length) {
            sentences.add(text.substring(lastEnd))
        }

        return sentences
    }
}
