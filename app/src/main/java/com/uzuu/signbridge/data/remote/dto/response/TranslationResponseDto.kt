package com.uzuu.signbridge.data.remote.dto.response

data class SignResponseDto(
    val glosses: List<String>, val confidence: Double,
    val spokenSentence: String, val suggestedChips: List<String>,
)
data class HighlightResponseDto(val type: String, val text: String)
data class SummaryResponseDto(
    val transcript: String, val summary: String, val highlights: List<HighlightResponseDto>,
    val iconCode: String, val quickReplies: List<String>,
)

