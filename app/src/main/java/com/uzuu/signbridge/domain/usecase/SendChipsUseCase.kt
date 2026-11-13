package com.uzuu.signbridge.domain.usecase

import com.uzuu.signbridge.domain.model.*
import com.uzuu.signbridge.domain.repository.ConversationRepository
import com.uzuu.signbridge.domain.repository.TranslationRepository

class SendChipsUseCase(
    private val conversation: ConversationRepository,
    private val translation: TranslationRepository,
) {
    suspend operator fun invoke(
        session: ConversationSession, chips: List<QuickChip>, previousIncoming: String?,
    ): OutgoingMessage {
        if (chips.size !in 1..3 || chips.map { it.id }.distinct().size != chips.size ||
            chips.any { it.context != session.context || it.label.isBlank() || it.label.length > 50 }) {
            throw BridgeException(BridgeError.INVALID_REQUEST, "Chọn 1–3 thẻ trong ngữ cảnh hiện tại.")
        }
        val result = try {
            OutgoingMessage(translation.expand(session.context, chips.map { it.label }, previousIncoming), false)
        } catch (error: BridgeException) {
            if (!error.allowsOfflineChips) throw error
            // Preserve every label verbatim; no fabricated AI output or medical advice.
            OutgoingMessage(chips.joinToString(" · ") { it.label }, true)
        }
        conversation.appendTurn(
            session.id, Direction.OUTGOING, chips.joinToString(" · ") { it.label },
            result.sentence, isOffline = result.isOffline, usedChipIds = chips.map { it.id },
        )
        return result
    }
}

