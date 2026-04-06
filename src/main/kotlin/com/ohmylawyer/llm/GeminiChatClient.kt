package com.ohmylawyer.llm

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

@Component
class GeminiChatClient(
    @Value("\${gemini.api-key}") private val apiKey: String,
    @Value("\${gemini.rewrite-model:gemini-2.5-flash}") private val rewriteModel: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = ObjectMapper()

    private val webClient = WebClient.builder()
        .baseUrl("https://generativelanguage.googleapis.com/v1beta")
        .defaultHeader("x-goog-api-key", apiKey)
        .codecs { it.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) }
        .build()

    fun generate(systemInstruction: String, userMessage: String): String {
        val request = mapOf(
            "system_instruction" to mapOf(
                "parts" to listOf(mapOf("text" to systemInstruction))
            ),
            "contents" to listOf(
                mapOf(
                    "role" to "user",
                    "parts" to listOf(mapOf("text" to userMessage))
                )
            ),
            "generationConfig" to mapOf(
                "temperature" to 0.2,
                "responseMimeType" to "application/json"
            )
        )

        val response = webClient.post()
            .uri("/models/${rewriteModel}:generateContent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchangeToMono { clientResponse ->
                clientResponse.bodyToMono(String::class.java).map { body ->
                    if (clientResponse.statusCode().isError) {
                        log.error("Gemini API error ({}): {}", clientResponse.statusCode(), body.take(500))
                        throw IllegalStateException("Gemini API error (${clientResponse.statusCode()}): ${body.take(500)}")
                    }
                    body
                }
            }
            .timeout(Duration.ofSeconds(30))
            .block() ?: throw IllegalStateException("Empty response from Gemini API")

        val tree = objectMapper.readTree(response)
        val text = tree.path("candidates").firstOrNull()
            ?.path("content")?.path("parts")?.firstOrNull()
            ?.path("text")?.asText()
            ?: throw IllegalStateException("Unexpected Gemini response: ${response.take(500)}")

        return text
    }
}
