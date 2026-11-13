package com.uzuu.signbridge.feature.devicecheck

import com.uzuu.signbridge.domain.model.*

enum class DeviceOperation { IDLE, LOADING, CAPTURING_SIGN, RECORDING, PROCESSING, SPEAKING }

data class DeviceCheckUiState(
    val context: ServiceContext = ServiceContext.CAFE,
    val session: ConversationSession? = null,
    val chips: List<QuickChip> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val history: List<MessageTurn> = emptyList(),
    val operation: DeviceOperation = DeviceOperation.LOADING,
    val cameraReady: Boolean = false,
    val message: String = "Đang mở dữ liệu trên thiết bị…",
    val outgoing: String = "",
    val isOffline: Boolean = false,
    val signResult: SignResult? = null,
    val incoming: SpeechSummary? = null,
    val speechCompletedCount: Int = 0,
) {
    val isIdle: Boolean get() = operation == DeviceOperation.IDLE
}

