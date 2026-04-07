package com.ohmylawyer.embedding.client

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gemini")
data class GeminiEmbeddingProperties(
    val apiKey: String,
    val embeddingModel: String = "text-embedding-004",
    val embeddingDimensions: Int = 768,
    val maxTokens: Int = 8192,
)
