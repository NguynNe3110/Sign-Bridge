package com.uzuu.signbridge.feature.conversation

import com.uzuu.signbridge.core.result.ErrorCode
import com.uzuu.signbridge.domain.model.ContextType
import com.uzuu.signbridge.domain.model.QuickChip
import com.uzuu.signbridge.domain.model.SignResult
import com.uzuu.signbridge.domain.model.SummaryResult

data class ConversationUiState(
    val context: ContextType,

    // Nửa trên (nhân viên)
    /** Câu của người khiếm thính đang hiển thị cho nhân viên. */
    val spokenSentence: String = "",
    val isInitializing: Boolean = false,
    val isOffline: Boolean = false,
    val isSpeaking: Boolean = false,
    val isStaffRecording: Boolean = false,
    val isSummarizing: Boolean = false,
    val staffError: ErrorCode? = null,

    // Nửa dưới (người khiếm thính)
    val isSigning: Boolean = false,
    val isRecognizing: Boolean = false,
    val isExpanding: Boolean = false,
    /** Gloss của lượt ký hiệu gần nhất (đã hiểu hoặc đang chờ xác nhận). */
    val recognizedGlosses: List<String> = emptyList(),
    /** Khác null khi `confidence` thấp: hiện gloss đoán + thẻ gợi ý để sửa. */
    val pendingSign: SignResult? = null,
    val chips: List<QuickChip> = emptyList(),
    /** Nhãn thẻ đã chọn, đúng thứ tự chạm. */
    val selectedChips: List<String> = emptyList(),
    val summary: SummaryResult? = null,
    val deafError: ErrorCode? = null,
) {
    val isBusy: Boolean
        get() = isInitializing || isSigning || isRecognizing || isExpanding ||
            isStaffRecording || isSummarizing || isSpeaking

    val canSelectMoreChips: Boolean
        get() = selectedChips.size < MAX_SELECTED_CHIPS

    companion object {
        const val MAX_SELECTED_CHIPS = 3
    }
}
