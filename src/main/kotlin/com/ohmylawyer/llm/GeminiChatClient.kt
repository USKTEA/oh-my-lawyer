package com.ohmylawyer.llm

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class GeminiChatClient(
    @param:Value("\${gemini.api-key}") private val apiKey: String,
    @param:Value("\${gemini.chat-model:gemini-2.5-pro}") private val chatModel: String,
    @param:Value("\${gemini.rewrite-model:gemini-2.5-flash}") private val rewriteModel: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = ObjectMapper()

    private val webClient = WebClient.builder()
        .baseUrl("https://generativelanguage.googleapis.com/v1beta")
        .defaultHeader("x-goog-api-key", apiKey)
        .codecs { it.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) }
        .build()

    fun generate(
        systemInstruction: String,
        userMessage: String,
        model: Model = Model.REWRITE,
        jsonResponse: Boolean = true,
        timeoutSeconds: Long = 30
    ): String {
        val modelName = when (model) {
            Model.CHAT -> chatModel
            Model.REWRITE -> rewriteModel
        }

        val generationConfig = mutableMapOf<String, Any>(
            "temperature" to 0.2
        )
        if (jsonResponse) {
            generationConfig["responseMimeType"] = "application/json"
        }

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
            "generationConfig" to generationConfig
        )

        val response = webClient.post()
            .uri("/models/${modelName}:generateContent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchangeToMono { clientResponse ->
                clientResponse.bodyToMono<String>().map { body ->
                    if (clientResponse.statusCode().isError) {
                        log.error("Gemini API error ({}): {}", clientResponse.statusCode(), body.take(500))
                        throw IllegalStateException("Gemini API error (${clientResponse.statusCode()}): ${body.take(500)}")
                    }
                    body
                }
            }
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .block() ?: throw IllegalStateException("Empty response from Gemini API")

        val tree = objectMapper.readTree(response)
        val text = tree.path("candidates").firstOrNull()
            ?.path("content")?.path("parts")?.firstOrNull()
            ?.path("text")?.asText()
            ?: throw IllegalStateException("Unexpected Gemini response: ${response.take(500)}")

        return text
    }

    enum class Model {
        CHAT,
        REWRITE
    }
}
