package com.ohmylawyer.collection.client

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "law-api")
data class LawApiProperties(
    val baseUrl: String = "http://www.law.go.kr/DRF",
    val oc: String = "",
    val requestDelayMs: Long = 100,
    val maxDisplay: Int = 100,
    val maxRetries: Int = 3,
    val retryDelayMs: Long = 5000,
)
