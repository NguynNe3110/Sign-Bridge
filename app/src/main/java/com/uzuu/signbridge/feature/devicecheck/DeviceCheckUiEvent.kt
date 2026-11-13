package com.uzuu.signbridge.feature.devicecheck

import com.uzuu.signbridge.domain.model.ServiceContext

sealed interface DeviceCheckUiEvent {
    data class ContextSelected(val context: ServiceContext) : DeviceCheckUiEvent
    data class ChipToggled(val id: String) : DeviceCheckUiEvent
    data class AddChip(val label: String) : DeviceCheckUiEvent
    data class DeleteChip(val id: String) : DeviceCheckUiEvent
    data class CameraReady(val ready: Boolean) : DeviceCheckUiEvent
    data class ShowMessage(val message: String) : DeviceCheckUiEvent
    data class ReplySelected(val reply: String) : DeviceCheckUiEvent
    data object SendChips : DeviceCheckUiEvent
    data object StartSign : DeviceCheckUiEvent
    data object FinishSign : DeviceCheckUiEvent
    data object StartRecording : DeviceCheckUiEvent
    data object FinishRecording : DeviceCheckUiEvent
    data object CheckHealth : DeviceCheckUiEvent
    data object Replay : DeviceCheckUiEvent
    data object RetryLoad : DeviceCheckUiEvent
    data object Backgrounded : DeviceCheckUiEvent
    data object CancelCapture : DeviceCheckUiEvent
}

