package com.uzuu.signbridge.domain.model

enum class ServiceContext { CAFE, PHARMACY, PUBLIC, CUSTOM }
enum class ChipCategory { SUBJECT, ACTION, DETAIL }
enum class Direction { OUTGOING, INCOMING }

data class QuickChip(
    val id: String,
    val context: ServiceContext,
    val label: String,
    val category: ChipCategory = ChipCategory.DETAIL,
    val usageCount: Int = 0,
    val isPersonal: Boolean = false,
)

data class ConversationSession(
    val id: String,
    val context: ServiceContext,
    val startedAt: Long,
    val status: String = "ACTIVE",
)

data class MessageTurn(
    val id: String,
    val sessionId: String,
    val direction: Direction,
    val rawInput: String,
    val processedText: String,
    val iconCode: String?,
    val isOffline: Boolean,
    val createdAt: Long,
)

data class SignFrame(val jpeg: ByteArray, val width: Int, val height: Int)
data class RecordedAudio(val bytes: ByteArray, val durationMs: Long)
data class OutgoingMessage(val sentence: String, val isOffline: Boolean)
data class Highlight(val type: String, val text: String)
data class SpeechSummary(
    val transcript: String,
    val summary: String,
    val highlights: List<Highlight>,
    val iconCode: String,
    val quickReplies: List<String>,
)

data class SignResult(
    val glosses: List<String>,
    val confidence: Double,
    val sentence: String,
    val suggestedChips: List<String>,
) {
    val needsConfirmation: Boolean
        get() = confidence < 0.7 || glosses.isEmpty() ||
            glosses.any { it == "UNKNOWN" } || sentence.isBlank()

    companion object {
        const val UNKNOWN_GLOSS = "UNKNOWN"
    }
}

