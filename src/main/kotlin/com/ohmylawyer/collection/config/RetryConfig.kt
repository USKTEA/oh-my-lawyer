package com.ohmylawyer.collection.config

import com.ohmylawyer.collection.client.LawApiProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.retry.RetryContext
import org.springframework.retry.annotation.EnableRetry
import org.springframework.retry.backoff.ExponentialBackOffPolicy
import org.springframework.retry.interceptor.RetryInterceptorBuilder
import org.springframework.retry.interceptor.RetryOperationsInterceptor
import org.springframework.retry.policy.SimpleRetryPolicy
import org.springframework.web.reactive.function.client.WebClientResponseException

@Configuration
@EnableRetry
class RetryConfig(
    private val props: LawApiProperties,
) {
    @Bean
    fun lawApiRetryInterceptor(): RetryOperationsInterceptor =
        RetryInterceptorBuilder
            .stateless()
            .retryPolicy(LawApiRetryPolicy(props.maxRetries))
            .backOffPolicy(
                ExponentialBackOffPolicy().apply {
                    initialInterval = props.retryDelayMs
                    multiplier = 2.0
                    maxInterval = 10_000L
                },
            ).build()
}

class LawApiRetryPolicy(
    private val maxRetries: Int,
) : SimpleRetryPolicy() {
    override fun canRetry(context: RetryContext): Boolean {
        if (context.retryCount >= maxRetries) return false
        val t = context.lastThrowable ?: return true
        if (t is WebClientResponseException) {
            val status = t.statusCode.value()
            return status == 429 || status >= 500
        }
        // Network errors, timeouts etc. — retry
        return true
    }
}
