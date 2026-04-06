package com.ohmylawyer.rag.dto

data class RagResponse(
    val question: String,
    val riskLevel: RiskLevel,
    val analysis: String,
    val citations: List<Citation>,
    val invalidCitations: List<String>,
    val iterations: Int,
    val disclaimer: String = DISCLAIMER
) {
    companion object {
        const val DISCLAIMER = "본 분석은 AI가 생성한 참고 자료이며, 법률적 조언이 아닙니다. 정확한 법률 판단은 반드시 전문 변호사와 상담하시기 바랍니다."
    }
}

enum class RiskLevel {
    HIGH, MEDIUM, LOW
}

data class Citation(
    val source: String,
    val content: String,
    val existsInDb: Boolean
)
