package com.uzuu.signbridge.domain.usecase

import com.uzuu.signbridge.domain.model.*
import com.uzuu.signbridge.domain.repository.ConversationRepository
import com.uzuu.signbridge.domain.repository.TranslationRepository

class RecognizeSignUseCase(
    private val conversation: ConversationRepository,
    private val translation: TranslationRepository,
) {
    suspend operator fun invoke(
        session: ConversationSession, frames: List<SignFrame>, previousIncoming: String?,
    ): SignResult {
        CapturePolicy.validateFrames(frames)
        val personal = conversation.getChips(session.context).filter { it.isPersonal }.take(20).map { it.label }
        val result = translation.recognizeSign(session.context, frames, personal, previousIncoming)
        if (!result.needsConfirmation) {
            conversation.appendTurn(session.id, Direction.OUTGOING, result.glosses.joinToString(" "),
                result.sentence)
        }
        return result
    }
}

