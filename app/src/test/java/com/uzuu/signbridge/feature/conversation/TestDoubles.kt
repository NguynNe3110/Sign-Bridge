package com.uzuu.signbridge.feature.conversation

import com.uzuu.signbridge.core.result.AppResult
import com.uzuu.signbridge.domain.model.*
import com.uzuu.signbridge.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Test doubles only. Production DI uses TV2 implementations. */
class RecordingConversationRepository : TranslationRepository {
    var signResult: AppResult<SignResult> = AppResult.Success(
        SignResult(listOf("ĐAU", "ĐẦU"), 0.9, "Mình bị đau đầu.", listOf("Đau đầu")))
    var expandResult: AppResult<String> = AppResult.Success("Câu mở rộng.")
    var summaryResult: AppResult<SummaryResult>? = null
    val expandCalls = mutableListOf<Pair<List<String>, String?>>()
    var savedTurns = mutableListOf<MessageTurn>()
    var lastPersonalChips: List<String> = emptyList()
    var startSignCount = 0
    var startVoiceCount = 0
    override suspend fun checkHealth() = true
    override suspend fun recognizeSign(context: ServiceContext, frames: List<SignFrame>,
        personalChips: List<String>, previousIncoming: String?): SignResult {
        lastPersonalChips = personalChips
        return signResult.unwrap()
    }
    override suspend fun expand(context: ServiceContext, chips: List<String>, previousIncoming: String?): String {
        expandCalls += chips to previousIncoming
        return expandResult.unwrap()
    }
    override suspend fun summarize(context: ServiceContext, audio: RecordedAudio, previousOutgoing: String?) =
        requireNotNull(summaryResult).unwrap()

    private fun <T> AppResult<T>.unwrap(): T = when (this) {
        is AppResult.Success -> data
        is AppResult.Failure -> throw BridgeException(
            BridgeError.entries.firstOrNull { it.name == error.code.name } ?: BridgeError.DEVICE, "test error")
    }
}

class RecordingSpeakerRepository(private val calls: RecordingConversationRepository? = null) : DeviceRepository {
    val spoken = mutableListOf<String>()
    var stopped = false
    var cancelled = false
    var speechError: Exception? = null
    override fun startSignCapture() { calls?.let { it.startSignCount++ } }
    override fun startRecording() { calls?.let { it.startVoiceCount++ } }
    override suspend fun finishSignCapture() = List(4) { SignFrame(byteArrayOf(1), 640, 480) }
    override suspend fun finishRecording() = RecordedAudio(byteArrayOf(1), 1000)
    override suspend fun speak(sentence: String) { speechError?.let { throw it }; spoken += sentence }
    override fun stopSpeaking() { stopped = true }
    override fun cancelCapture() { cancelled = true }
}

class StubChipRepository(initial: List<QuickChip> = emptyList()) : ConversationRepository {
    val chips = MutableStateFlow(initial)
    val increasedIds = mutableListOf<String>()
    val savedTurns = mutableListOf<MessageTurn>()
    var failStorage = false
    private var nextId = 100
    override fun observeChips(context: ServiceContext): Flow<List<QuickChip>> =
        chips.map { list -> list.filter { it.context == context } }
    override suspend fun getChips(context: ServiceContext) = chips.value.filter { it.context == context }
    override suspend fun createSession(context: ServiceContext) = ConversationSession("session", context, 1000)
    override suspend fun endSession(id: String) = Unit
    override suspend fun addChip(context: ServiceContext, label: String, category: ChipCategory): QuickChip {
        if (failStorage) throw BridgeException(BridgeError.STORAGE, "test storage")
        return QuickChip((nextId++).toString(), context, label, category, isPersonal = true).also {
            chips.value = chips.value + it
        }
    }
    override suspend fun deletePersonalChip(id: String) {
        if (failStorage) throw BridgeException(BridgeError.STORAGE, "test storage")
        chips.value = chips.value.filterNot { it.id == id && it.isPersonal }
    }
    override suspend fun getHistory(limit: Int) = savedTurns.takeLast(limit)
    override suspend fun appendTurn(
        sessionId: String, direction: Direction, rawInput: String, processedText: String,
        iconCode: String?, isOffline: Boolean, usedChipIds: List<String>,
    ): MessageTurn {
        if (failStorage) throw BridgeException(BridgeError.STORAGE, "test storage")
        increasedIds += usedChipIds
        chips.value = chips.value.map { if (it.id in usedChipIds) it.copy(usageCount = it.usageCount + 1) else it }
        return MessageTurn("turn-${savedTurns.size}", sessionId, direction, rawInput, processedText,
            iconCode, isOffline, 1000).also { savedTurns.add(it) }
    }
}
