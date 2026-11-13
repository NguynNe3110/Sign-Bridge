package com.uzuu.signbridge.domain.model

/** Kết quả tóm tắt lời nhân viên (`POST /api/summarize`). */
data class SummaryResult(
    val transcript: String,
    val summary: String,
    val highlights: List<Highlight>,
    val iconCode: IconCode,
    val quickReplies: List<String>,
)

data class Highlight(
    val type: HighlightType,
    val text: String,
)

enum class HighlightType {
    PRICE,
    COUNTER,
    DOSAGE,
    TIME,
    QUANTITY,
    OTHER,
}

enum class IconCode {
    PILL,
    MONEY,
    QR_PAYMENT,
    COUNTER,
    TIME,
    DOCUMENT,
    DRINK,
    FOOD,
    CHECK,
    WARNING,
    INFO,
}
