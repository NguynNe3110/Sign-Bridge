package com.uzuu.signbridge.data.local.datasource

import com.uzuu.signbridge.domain.model.*

/** Dữ liệu mẫu khi server lỗi, chỉ cho thẻ và tóm tắt. Ký hiệu không có mẫu (mục 4.2). */
object MockTranslationFallback {

    fun mockExpand(context: ServiceContext, chips: List<String>): String {
        if (chips.isEmpty()) return ""
        val first = chips.first().trim()
        val isSentenceLike = first.startsWith("Dạ", ignoreCase = true) ||
            first.startsWith("Em", ignoreCase = true) ||
            first.startsWith("Cho em", ignoreCase = true) ||
            first.startsWith("Lấy", ignoreCase = true) ||
            first.endsWith("nhé", ignoreCase = true) ||
            first.endsWith("ạ", ignoreCase = true)

        if (isSentenceLike) {
            if (chips.size == 1) return first
            val base = first.trimEnd('.', '!', '?', ' ')
                .replace(Regex("(?i)\\s+(nhé|ạ|nha|với)+$"), "")
            val rest = chips.drop(1).joinToString(", ") { it.lowercase() }
            return "$base $rest nhé, cảm ơn bạn."
        }

        val joined = chips.joinToString(", ")
        return when (context) {
            ServiceContext.CAFE -> "Cho mình $joined nhé, cảm ơn bạn."
            ServiceContext.PHARMACY -> "Mình cần mua thuốc trị $joined, cảm ơn bạn."
            ServiceContext.PUBLIC -> "Làm ơn cho mình hỏi về $joined ạ."
            ServiceContext.CUSTOM -> "Tôi muốn trao đổi về $joined."
        }
    }

    fun mockSummarize(context: ServiceContext): SpeechSummary {
        return when (context) {
            ServiceContext.CAFE -> SpeechSummary(
                transcript = "Cà phê sữa đá của bạn ba mươi lăm nghìn đồng.",
                summary = "Cà phê sữa đá 35.000đ",
                highlights = listOf(Highlight("PRICE", "35.000đ")),
                iconCode = "DRINK",
                quickReplies = listOf("Chuyển khoản QR", "Tiền mặt", "Mang đi"),
            )
            ServiceContext.PHARMACY -> SpeechSummary(
                transcript = "Thuốc giảm đau này uống sau khi ăn, ngày hai lần sáng và tối nhé.",
                summary = "Uống sau ăn, ngày 2 lần",
                highlights = listOf(Highlight("DOSAGE", "ngày 2 lần")),
                iconCode = "PILL",
                quickReplies = listOf("Đã hiểu", "Hỏi giá", "Cảm ơn"),
            )
            ServiceContext.PUBLIC -> SpeechSummary(
                transcript = "Bạn vui lòng sang quầy số ba để nộp hồ sơ và nộp lệ phí nhé.",
                summary = "Nộp hồ sơ tại Quầy 3",
                highlights = listOf(Highlight("COUNTER", "Quầy 3")),
                iconCode = "COUNTER",
                quickReplies = listOf("Đã hiểu", "Lấy số", "Cảm ơn"),
            )
            ServiceContext.CUSTOM -> SpeechSummary(
                transcript = "Xin chào bạn, tôi có thể giúp gì cho bạn được ạ?",
                summary = "Chào bạn, cần giúp gì?",
                highlights = emptyList(),
                iconCode = "INFO",
                quickReplies = listOf("Cảm ơn", "Chờ một chút", "Viết ra giấy"),
            )
        }
    }
}
