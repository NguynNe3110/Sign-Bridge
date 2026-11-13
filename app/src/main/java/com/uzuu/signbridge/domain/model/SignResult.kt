package com.uzuu.signbridge.domain.model

/** Kết quả nhận diện ký hiệu (`POST /api/sign`). */
data class SignResult(
    val glosses: List<String>,
    val confidence: Double,
    val spokenSentence: String,
    val suggestedChips: List<String>,
) {
    /**
     * Cần người khiếm thính xác nhận bằng thẻ khi có `UNKNOWN`,
     * `confidence` dưới ngưỡng hoặc không có câu để phát loa.
     */
    val needsConfirmation: Boolean
        get() = glosses.contains(UNKNOWN_GLOSS) ||
            confidence < CONFIDENCE_THRESHOLD ||
            spokenSentence.isBlank()

    companion object {
        const val UNKNOWN_GLOSS = "UNKNOWN"

        /** Ngưỡng đề xuất trong docs, team có thể chỉnh. */
        const val CONFIDENCE_THRESHOLD = 0.7
    }
}
