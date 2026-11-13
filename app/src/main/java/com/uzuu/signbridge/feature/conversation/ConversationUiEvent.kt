package com.uzuu.signbridge.feature.conversation

sealed interface ConversationUiEvent {
    // Người khiếm thính
    data object SignPressed : ConversationUiEvent
    data object SignReleased : ConversationUiEvent
    data class ChipToggled(val label: String) : ConversationUiEvent
    data object ClearChipsClicked : ConversationUiEvent
    data object SendChipsClicked : ConversationUiEvent
    data class QuickReplyClicked(val reply: String) : ConversationUiEvent
    data object DeafErrorDismissed : ConversationUiEvent

    // Nhân viên
    data object MicPressed : ConversationUiEvent
    data object MicReleased : ConversationUiEvent
    data object ReplayClicked : ConversationUiEvent
    data object StaffErrorDismissed : ConversationUiEvent
}
