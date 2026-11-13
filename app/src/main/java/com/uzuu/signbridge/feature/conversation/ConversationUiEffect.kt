package com.uzuu.signbridge.feature.conversation

import com.uzuu.signbridge.core.ui.HapticPattern

sealed interface ConversationUiEffect {
    data class Vibrate(val pattern: HapticPattern) : ConversationUiEffect
}
