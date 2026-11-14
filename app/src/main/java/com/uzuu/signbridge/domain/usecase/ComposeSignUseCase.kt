package com.uzuu.signbridge.domain.usecase

import com.uzuu.signbridge.domain.model.*
import com.uzuu.signbridge.domain.repository.ConversationRepository
import com.uzuu.signbridge.domain.repository.TranslationRepository
import java.text.Normalizer
import java.util.Locale

/** Luồng Hybrid (mục 4.2a): ký hiệu nhận diện trên máy → server ghép câu; mất mạng → câu dự phòng tại máy. */
class ComposeSignUseCase(
    private val conversation: ConversationRepository,
    private val translation: TranslationRepository,
) {
    suspend operator fun invoke(
        session: ConversationSession, glosses: List<GlossPrediction>, previousIncoming: String?,
    ): SignResult {
        if (glosses.size !in 1..CapturePolicy.MAX_TURN_GLOSSES ||
            glosses.any { it.label.isBlank() || it.label.length > MAX_LABEL || it.confidence !in 0f..1f }) {
            throw BridgeException(BridgeError.INVALID_REQUEST,
                "Mỗi lượt cần 1–${CapturePolicy.MAX_TURN_GLOSSES} ký hiệu hợp lệ.")
        }
        val personal = conversation.getChips(session.context).filter { it.isPersonal }
            .map { it.label.trim() }.filter { it.length in 1..MAX_LABEL }.take(MAX_PERSONAL_CHIPS)
        val result = try {
            translation.composeSignGlosses(session.context, glosses, personal, previousIncoming)
        } catch (error: BridgeException) {
            if (!error.allowsOfflineChips) throw error
            offlineSignResult(glosses)
        }
        if (!result.needsConfirmation) {
            conversation.appendTurn(session.id, Direction.OUTGOING, result.glosses.joinToString(" "),
                result.sentence, isOffline = result.isOffline)
        }
        return result
    }

    private companion object {
        const val MAX_LABEL = 50
        const val MAX_PERSONAL_CHIPS = 20
    }
}

/**
 * Câu dự phòng khi không gọi được server — cùng quy tắc với server (mục 7.3 bước 2, 7.4):
 * chỉ ghép đúng các nhãn, không thêm ý; có nhãn xác suất thấp → không ghép câu, chuyển sang thẻ xác nhận.
 */
fun offlineSignResult(glosses: List<GlossPrediction>): SignResult {
    val minimum = CapturePolicy.VSL_MIN_GLOSS_CONFIDENCE
    val confident = glosses.all { it.confidence >= minimum }
    val labels = glosses.map { it.label.trim() }
    val sentence = if (confident) labels.joinToString(", ").replaceFirstChar { it.uppercase() } + "." else ""
    val doubtful = glosses.filter { it.confidence < minimum }.map { it.label.trim() }
    val lowest = glosses.minOf { it.confidence }.toDouble()
    return SignResult(
        glosses = glosses.map { if (it.confidence >= minimum) normalizeGloss(it.label) else SignResult.UNKNOWN_GLOSS },
        confidence = if (confident) lowest else minOf(lowest, minimum - 0.01),
        sentence = sentence,
        suggestedChips = (doubtful + labels).distinct().take(6),
        isOffline = true,
    )
}

/** Mục 7.4 bước 1: NFC, gộp khoảng trắng, viết hoa — giống `normalize_gloss` của server. */
fun normalizeGloss(label: String): String =
    Normalizer.normalize(label.trim(), Normalizer.Form.NFC).replace(Regex("\\s+"), " ").uppercase(Locale.ROOT)
