package com.uzuu.signbridge.feature.conversation

sealed interface ConversationUiEvent {
    data object ScreenStopped : ConversationUiEvent
    data object CaptureCancelled : ConversationUiEvent
    data class PermissionDenied(val camera: Boolean) : ConversationUiEvent
    data object MicPermissionGranted : ConversationUiEvent
    data object CameraUnavailable : ConversationUiEvent
    // Người khiếm thính
    data object SignPressed : ConversationUiEvent
    data object SignReleased : ConversationUiEvent
    data object SendSignDraftClicked : ConversationUiEvent
    data object ClearSignDraftClicked : ConversationUiEvent
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
