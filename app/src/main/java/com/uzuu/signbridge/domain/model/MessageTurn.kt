package com.uzuu.signbridge.domain.model

/** Một lượt hội thoại (docx §11 `MessageTurn`). */
data class MessageTurn(
    val context: ContextType,
    val direction: TurnDirection,
    val text: String,
    val timestampMillis: Long,
)

enum class TurnDirection {
    /** Người khiếm thính -> nhân viên. */
    OUTGOING,

    /** Nhân viên -> người khiếm thính. */
    INCOMING,
}
