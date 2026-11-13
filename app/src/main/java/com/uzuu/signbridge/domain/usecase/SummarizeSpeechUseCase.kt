package com.uzuu.signbridge.domain.usecase

import com.uzuu.signbridge.domain.model.*
import com.uzuu.signbridge.domain.repository.ConversationRepository
import com.uzuu.signbridge.domain.repository.TranslationRepository

class SummarizeSpeechUseCase(
    private val conversation: ConversationRepository,
    private val translation: TranslationRepository,
) {
    suspend operator fun invoke(
        session: ConversationSession, audio: RecordedAudio, previousOutgoing: String?,
    ): SpeechSummary {
        CapturePolicy.validateAudio(audio)
        val result = translation.summarize(session.context, audio, previousOutgoing)
        conversation.appendTurn(session.id, Direction.INCOMING, result.transcript, result.summary, result.iconCode)
        return result
    }
}

