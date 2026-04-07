package com.ohmylawyer.embedding.client

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

@Component
class GeminiEmbeddingClient(
    private val props: GeminiEmbeddingProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = ObjectMapper()

    private val webClient =
        WebClient
            .builder()
            .baseUrl("https://generativelanguage.googleapis.com/v1beta")
            .defaultHeader("x-goog-api-key", props.apiKey)
            .codecs { it.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) }
            .build()

    fun embed(
        text: String,
        taskType: TaskType = TaskType.RETRIEVAL_DOCUMENT,
    ): List<Float> {
        val request =
            mapOf(
                "model" to "models/${props.embeddingModel}",
                "content" to mapOf("parts" to listOf(mapOf("text" to text))),
                "taskType" to taskType.name,
                "output_dimensionality" to props.embeddingDimensions,
            )

        val response =
            webClient
                .post()
                .uri("/models/${props.embeddingModel}:embedContent")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String::class.java)
                .timeout(Duration.ofSeconds(30))
                .block() ?: throw IllegalStateException("Empty response from Gemini embedding API")

        val tree = objectMapper.readTree(response)
        val values = tree.path("embedding").path("values")
        if (values.isMissingNode || !values.isArray) {
            throw IllegalStateException("Unexpected embedding response: ${response.take(500)}")
        }

        return values.map { it.floatValue() }
    }

    fun embedBatch(
        texts: List<String>,
        taskType: TaskType = TaskType.RETRIEVAL_DOCUMENT,
    ): List<List<Float>> {
        val requests =
            texts.map { text ->
                mapOf(
                    "model" to "models/${props.embeddingModel}",
                    "content" to mapOf("parts" to listOf(mapOf("text" to text))),
                    "taskType" to taskType.name,
                    "output_dimensionality" to props.embeddingDimensions,
                )
            }

        val body = mapOf("requests" to requests)

        val response =
            webClient
                .post()
                .uri("/models/${props.embeddingModel}:batchEmbedContents")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono { clientResponse ->
                    clientResponse.bodyToMono(String::class.java).map { responseBody ->
                        if (clientResponse.statusCode().isError) {
                            throw IllegalStateException(
                                "Gemini API error (${clientResponse.statusCode()}): ${responseBody.take(500).replace("\n", " ")}",
                            )
                        }
                        responseBody
                    }
                }.timeout(Duration.ofSeconds(60))
                .block() ?: throw IllegalStateException("Empty response from Gemini batch embedding API")

        val tree = objectMapper.readTree(response)
        val embeddings = tree.path("embeddings")
        if (embeddings.isMissingNode || !embeddings.isArray) {
            throw IllegalStateException("Unexpected batch embedding response: ${response.take(500)}")
        }

        return embeddings.map { node ->
            node.path("values").map { it.floatValue() }
        }
    }

    enum class TaskType {
        RETRIEVAL_DOCUMENT,
        RETRIEVAL_QUERY,
    }
}
