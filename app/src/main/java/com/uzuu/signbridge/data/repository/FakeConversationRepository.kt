package com.uzuu.signbridge.data.repository

import com.uzuu.signbridge.core.result.AppResult
import com.uzuu.signbridge.domain.model.ContextType
import com.uzuu.signbridge.domain.model.Highlight
import com.uzuu.signbridge.domain.model.HighlightType
import com.uzuu.signbridge.domain.model.IconCode
import com.uzuu.signbridge.domain.model.MessageTurn
import com.uzuu.signbridge.domain.model.SignResult
import com.uzuu.signbridge.domain.model.SummaryResult
import com.uzuu.signbridge.domain.repository.ConversationRepository
import kotlinx.coroutines.delay

/**
 * Bản giả để phát triển UI khi chưa có camera/ghi âm/API thật (TV2).
 * Trả JSON mẫu theo docs/luong-xu-ly-chinh.md §4.2–4.4.
 * Lượt ký hiệu xen kẽ kết quả rõ / không chắc để thử cả 2 nhánh UI.
 */
class FakeConversationRepository(
    private val latencyMillis: Long = 800,
) : ConversationRepository {

    private var signCount = 0
    private val turns = mutableListOf<MessageTurn>()

    override suspend fun startSignCapture() = Unit

    override suspend fun stopSignCaptureAndRecognize(
        context: ContextType,
        personalChips: List<String>,
        previousIncoming: String?,
    ): AppResult<SignResult> {
        delay(latencyMillis)
        signCount++
        val result = if (signCount % 2 == 1) confidentSign(context) else unsureSign(context)
        return AppResult.Success(result)
    }

    override suspend fun expandChips(
        context: ContextType,
        chips: List<String>,
        previousIncoming: String?,
    ): AppResult<String> {
        delay(latencyMillis)
        val body = chips.joinToString(", ") { it.lowercase() }
        return AppResult.Success("Chào bạn, mình muốn: $body. Cảm ơn bạn!")
    }

    override suspend fun startVoiceRecording() = Unit

    override suspend fun stopVoiceRecordingAndSummarize(
        context: ContextType,
        previousOutgoing: String?,
    ): AppResult<SummaryResult> {
        delay(latencyMillis)
        return AppResult.Success(sampleSummary(context))
    }

    override suspend fun saveTurn(turn: MessageTurn) {
        turns += turn
    }

    private fun confidentSign(context: ContextType): SignResult = when (context) {
        ContextType.CAFE -> SignResult(
            glosses = listOf("CÀ PHÊ", "ĐÁ", "MANG ĐI"),
            confidence = 0.84,
            spokenSentence = "Cho mình một cà phê đá mang đi nhé, cảm ơn bạn.",
            suggestedChips = listOf("Cà phê đá", "Mang đi", "Thanh toán"),
        )
        ContextType.PUBLIC -> SignResult(
            glosses = listOf("GIẤY TỜ", "Ở ĐÂU"),
            confidence = 0.8,
            spokenSentence = "Chào anh chị, cho mình hỏi nộp giấy tờ ở quầy nào ạ?",
            suggestedChips = listOf("Nộp giấy tờ", "Quầy nào", "Cảm ơn"),
        )
        else -> SignResult(
            glosses = listOf("ĐAU", "ĐẦU", "MUA", "THUỐC"),
            confidence = 0.86,
            spokenSentence = "Chào bạn, mình bị đau đầu, muốn mua thuốc.",
            suggestedChips = listOf("Đau đầu", "Mua thuốc", "Không buồn ngủ"),
        )
    }

    private fun unsureSign(context: ContextType): SignResult = SignResult(
        glosses = listOf(SignResult.UNKNOWN_GLOSS, "NGÀY"),
        confidence = 0.41,
        spokenSentence = "",
        suggestedChips = when (context) {
            ContextType.CAFE -> listOf("Cà phê", "Đá", "Mang đi", "Bao nhiêu")
            ContextType.PUBLIC -> listOf("Giấy tờ", "Ở đâu", "Một ngày", "Hai ngày")
            else -> listOf("Một ngày", "Hai ngày", "Một tuần", "Lâu rồi")
        },
    )

    private fun sampleSummary(context: ContextType): SummaryResult = when (context) {
        ContextType.CAFE -> SummaryResult(
            transcript = "Dạ cà phê đá mang đi của bạn hết hai mươi lăm nghìn, bạn chờ khoảng năm phút nha.",
            summary = "Cà phê đá • 25.000đ • Chờ 5 phút",
            highlights = listOf(
                Highlight(HighlightType.PRICE, "25.000đ"),
                Highlight(HighlightType.TIME, "5 phút"),
            ),
            iconCode = IconCode.DRINK,
            quickReplies = listOf("Chuyển khoản QR", "Trả tiền mặt", "Cảm ơn bạn"),
        )
        ContextType.PUBLIC -> SummaryResult(
            transcript = "Bạn qua quầy số ba nộp hồ sơ nhé, nhớ mang theo căn cước bản gốc.",
            summary = "Quầy số 3 • Mang căn cước bản gốc",
            highlights = listOf(Highlight(HighlightType.COUNTER, "Quầy số 3")),
            iconCode = IconCode.COUNTER,
            quickReplies = listOf("Cảm ơn", "Quầy ở đâu?", "Cần giấy tờ gì nữa?"),
        )
        else -> SummaryResult(
            transcript = "Dạ bạn dùng Panadol xanh nha, uống một viên sau ăn, ngày hai lần, hộp này giá ba mươi lăm nghìn.",
            summary = "Panadol Xanh • 1 viên sau ăn • Ngày 2 lần • 35.000đ",
            highlights = listOf(
                Highlight(HighlightType.DOSAGE, "1 viên"),
                Highlight(HighlightType.QUANTITY, "Ngày 2 lần"),
                Highlight(HighlightType.PRICE, "35.000đ"),
            ),
            iconCode = IconCode.PILL,
            quickReplies = listOf("Lấy 1 hộp", "Chuyển khoản QR", "Có loại rẻ hơn không?"),
        )
    }
}
