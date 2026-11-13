package com.uzuu.signbridge.domain.usecase

import com.uzuu.signbridge.domain.model.*
import com.uzuu.signbridge.domain.repository.ConversationRepository
import com.uzuu.signbridge.domain.repository.DeviceRepository

class SpeakQuickReplyUseCase(
    private val conversation: ConversationRepository,
    private val device: DeviceRepository,
) {
    suspend operator fun invoke(session: ConversationSession, reply: String) {
        if (reply.isBlank() || reply.length > 300) {
            throw BridgeException(BridgeError.INVALID_REQUEST, "Câu trả lời không hợp lệ.")
        }
        conversation.appendTurn(session.id, Direction.OUTGOING, reply, reply)
        device.speak(reply)
    }
}

