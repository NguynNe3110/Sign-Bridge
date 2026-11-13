package com.uzuu.signbridge.domain.repository

import com.uzuu.signbridge.core.result.AppResult
import com.uzuu.signbridge.domain.model.ContextType
import com.uzuu.signbridge.domain.model.MessageTurn
import com.uzuu.signbridge.domain.model.SignResult
import com.uzuu.signbridge.domain.model.SummaryResult

/**
 * Hợp đồng giữa màn hội thoại (TV1) và phần thiết bị/API/lưu trữ (TV2).
 * Implementation thật: camera, ghi âm, gọi `/api/sign`, `/api/expand`,
 * `/api/summarize`, lưu SQLite, câu mẫu khi offline.
 */
interface ConversationRepository {

    /** Nhấn giữ nút Ký hiệu: bắt đầu lấy frame từ camera. */
    suspend fun startSignCapture()

    /** Thả nút Ký hiệu: dừng camera, gửi frame và trả kết quả nhận diện. */
    suspend fun stopSignCaptureAndRecognize(
        context: ContextType,
        personalChips: List<String>,
        previousIncoming: String?,
    ): AppResult<SignResult>

    /** Thẻ đã chọn -> câu nói. Offline: implementation ghép câu mẫu. */
    suspend fun expandChips(
        context: ContextType,
        chips: List<String>,
        previousIncoming: String?,
    ): AppResult<String>

    /** Nhân viên nhấn giữ Mic: bắt đầu ghi âm. */
    suspend fun startVoiceRecording()

    /** Nhân viên thả Mic: dừng ghi âm, gửi audio và trả tóm tắt. */
    suspend fun stopVoiceRecordingAndSummarize(
        context: ContextType,
        previousOutgoing: String?,
    ): AppResult<SummaryResult>

    suspend fun saveTurn(turn: MessageTurn)
}
