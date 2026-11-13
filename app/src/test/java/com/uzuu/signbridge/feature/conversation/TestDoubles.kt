package com.uzuu.signbridge.feature.conversation

import com.uzuu.signbridge.core.result.AppResult
import com.uzuu.signbridge.domain.model.ContextType
import com.uzuu.signbridge.domain.model.MessageTurn
import com.uzuu.signbridge.domain.model.QuickChip
import com.uzuu.signbridge.domain.model.SignResult
import com.uzuu.signbridge.domain.model.SummaryResult
import com.uzuu.signbridge.domain.repository.ChipRepository
import com.uzuu.signbridge.domain.repository.ConversationRepository
import com.uzuu.signbridge.domain.repository.SpeakerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class RecordingConversationRepository : ConversationRepository {
    var signResult: AppResult<SignResult> = AppResult.Success(
        SignResult(listOf("ĐAU", "ĐẦU"), 0.9, "Mình bị đau đầu.", listOf("Đau đầu")),
    )
    var expandResult: AppResult<String> = AppResult.Success("Câu mở rộng.")
    var summaryResult: AppResult<SummaryResult>? = null

    val expandCalls = mutableListOf<Pair<List<String>, String?>>()
    val savedTurns = mutableListOf<MessageTurn>()
    var lastPersonalChips: List<String> = emptyList()
    var startSignCount = 0
    var startVoiceCount = 0

    override suspend fun startSignCapture() {
        startSignCount++
    }

    override suspend fun stopSignCaptureAndRecognize(
        context: ContextType,
        personalChips: List<String>,
        previousIncoming: String?,
    ): AppResult<SignResult> {
        lastPersonalChips = personalChips
        return signResult
    }

    override suspend fun expandChips(
        context: ContextType,
        chips: List<String>,
        previousIncoming: String?,
    ): AppResult<String> {
        expandCalls += chips to previousIncoming
        return expandResult
    }

    override suspend fun startVoiceRecording() {
        startVoiceCount++
    }

    override suspend fun stopVoiceRecordingAndSummarize(
        context: ContextType,
        previousOutgoing: String?,
    ): AppResult<SummaryResult> = requireNotNull(summaryResult)

    override suspend fun saveTurn(turn: MessageTurn) {
        savedTurns += turn
    }
}

class RecordingSpeakerRepository : SpeakerRepository {
    val spoken = mutableListOf<String>()
    var stopped = false

    override suspend fun speak(sentence: String) {
        spoken += sentence
    }

    override fun stop() {
        stopped = true
    }
}

class StubChipRepository(initial: List<QuickChip> = emptyList()) : ChipRepository {
    val chips = MutableStateFlow(initial)
    val increasedIds = mutableListOf<Long>()

    override fun observeChips(context: ContextType): Flow<List<QuickChip>> = chips

    override suspend fun addPersonalChip(context: ContextType, label: String) {
        val id = (chips.value.maxOfOrNull { it.id } ?: 0) + 1
        chips.value = chips.value + QuickChip(id, context, label, isPersonal = true)
    }

    override suspend fun deleteChip(id: Long) {
        chips.value = chips.value.filterNot { it.id == id }
    }

    override suspend fun increaseUsage(ids: List<Long>) {
        increasedIds += ids
    }
}
