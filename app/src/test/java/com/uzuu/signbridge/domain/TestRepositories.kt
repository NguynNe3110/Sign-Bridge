package com.uzuu.signbridge.domain

import com.uzuu.signbridge.domain.model.*
import com.uzuu.signbridge.domain.repository.*

class MemoryConversation : ConversationRepository {
    val turns = mutableListOf<MessageTurn>()
    val chips = mutableListOf(QuickChip("coffee", ServiceContext.CAFE, "Cà phê sữa", ChipCategory.SUBJECT))
    var usage = 0
    override suspend fun createSession(context: ServiceContext) = ConversationSession("session", context, 1)
    override suspend fun endSession(id: String) = Unit
    override suspend fun getChips(context: ServiceContext) = chips.filter { it.context == context }
    override suspend fun addChip(context: ServiceContext, label: String, category: ChipCategory) =
        QuickChip("custom", context, label, category, isPersonal = true).also { chips.add(it) }
    override suspend fun deletePersonalChip(id: String) { chips.removeAll { it.id == id && it.isPersonal } }
    override suspend fun updateChip(id: String, label: String) {
        val index = chips.indexOfFirst { it.id == id }
        chips[index] = chips[index].copy(label = label)
    }
    override suspend fun getHistory(limit: Int) = turns.takeLast(limit)
    override suspend fun appendTurn(
        sessionId: String, direction: Direction, rawInput: String, processedText: String,
        iconCode: String?, isOffline: Boolean, usedChipIds: List<String>,
    ): MessageTurn {
        usage += usedChipIds.size
        return MessageTurn("turn-${turns.size}", sessionId, direction, rawInput, processedText,
            iconCode, isOffline, 1).also { turns.add(it) }
    }
}

class StubTranslation : TranslationRepository {
    var expandAction: suspend () -> String = { "Cho mình cà phê sữa." }
    var sign = SignResult(listOf("CẢM ƠN"), 0.9, "Cảm ơn bạn.", listOf("Cảm ơn", "Có", "Không"))
    var calls = 0
    override suspend fun checkHealth() = true
    override suspend fun expand(context: ServiceContext, chips: List<String>, previousIncoming: String?): String {
        calls++
        return expandAction()
    }
    override suspend fun recognizeSign(context: ServiceContext, frames: List<SignFrame>,
        personalChips: List<String>, previousIncoming: String?) = sign
    override suspend fun summarize(context: ServiceContext, audio: RecordedAudio, previousOutgoing: String?) =
        SpeechSummary("Giá 35.000đ.", "Giá 35.000đ.", emptyList(), "MONEY", listOf("Đồng ý", "Cảm ơn", "Không"))
}

class StubDevice : DeviceRepository {
    var spoken = ""
    var cancelled = false
    override fun startSignCapture() = Unit
    override suspend fun finishSignCapture() = List(4) { SignFrame(byteArrayOf(1), 640, 480) }
    override fun startRecording() = Unit
    override suspend fun finishRecording() = RecordedAudio(byteArrayOf(1), 1_000)
    override suspend fun speak(sentence: String) { spoken = sentence }
    override fun cancelCapture() { cancelled = true }
    override fun stopSpeaking() = Unit
}

