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
        if (chips.map { it.id }.distinct().size != chips.size || chips.any { it.context != session.context }) invalid()
        return send(session, chips.map { it.label }, chips.map { it.id }, previousIncoming)
    }

    /** TV1 may confirm suggested labels that are not saved personal chips. Do not fabricate DB IDs. */
    suspend fun fromLabels(session: ConversationSession, labels: List<String>, previousIncoming: String?): OutgoingMessage {
        validate(labels)
        val stored = conversation.getChips(session.context)
        return send(session, labels, stored.filter { it.label in labels }.map { it.id }, previousIncoming)
    }

    private suspend fun send(
        session: ConversationSession, labels: List<String>, usedIds: List<String>, previousIncoming: String?,
    ): OutgoingMessage {
        validate(labels)
        val result = try {
            OutgoingMessage(translation.expand(session.context, labels, previousIncoming), false)
        } catch (error: BridgeException) {
            if (!error.allowsOfflineChips) throw error
            OutgoingMessage(labels.joinToString(" · "), true)
        }
        conversation.appendTurn(session.id, Direction.OUTGOING, labels.joinToString(" · "),
            result.sentence, isOffline = result.isOffline, usedChipIds = usedIds)
        return result
    }

    private fun validate(labels: List<String>) {
        if (labels.size !in 1..5 || labels.distinct().size != labels.size ||
            labels.any { it.isBlank() || it.length > 50 }) invalid()
    }
    private fun invalid(): Nothing =
        throw BridgeException(BridgeError.INVALID_REQUEST, "Chọn 1–5 thẻ trong ngữ cảnh hiện tại.")
}

