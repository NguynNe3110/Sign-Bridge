package com.uzuu.signbridge.data.mapper

import com.uzuu.signbridge.data.remote.dto.response.*
import com.uzuu.signbridge.domain.model.*
import java.text.Normalizer

fun SignResponseDto.toDomain(context: ServiceContext): SignResult {
    val common = setOf("XIN CHÀO", "CẢM ƠN", "CÓ", "KHÔNG", "THANH TOÁN", "BAO NHIÊU", "MỘT", "HAI", "NGÀY")
    val pharmacy = setOf("ĐAU", "ĐẦU", "BỤNG", "SỐT", "THUỐC", "MUA")
    val cafe = setOf("MUA", "CÀ PHÊ", "ĐÁ", "MANG ĐI")
    val public = setOf("GIẤY TỜ", "Ở ĐÂU")
    val allowed = common + when (context) {
        ServiceContext.CAFE -> cafe
        ServiceContext.PHARMACY -> pharmacy
        ServiceContext.PUBLIC -> public
        ServiceContext.CUSTOM -> pharmacy + cafe + public
    }
    val normalized = glosses.map {
        Normalizer.normalize(it.trim().uppercase(java.util.Locale.ROOT), Normalizer.Form.NFC)
            .let { gloss -> if (gloss in allowed) gloss else "UNKNOWN" }
    }
    val confirmed = confidence >= 0.7 && normalized.isNotEmpty() && "UNKNOWN" !in normalized
    return SignResult(normalized, confidence, if (confirmed) spokenSentence else "", suggestedChips)
}

fun SummaryResponseDto.toDomain() = SpeechSummary(transcript, summary,
    highlights.filter { it.text.isNotBlank() && summary.contains(it.text) }.map { Highlight(it.type, it.text) },
    iconCode, quickReplies)

