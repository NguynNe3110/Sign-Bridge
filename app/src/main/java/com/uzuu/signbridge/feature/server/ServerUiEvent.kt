package com.uzuu.signbridge.feature.server

sealed interface ServerUiEvent {
    data class IpChanged(val value: String) : ServerUiEvent
    data class PortChanged(val value: String) : ServerUiEvent
    data class HttpsChanged(val value: Boolean) : ServerUiEvent
    data object CheckConnection : ServerUiEvent
    data object SaveAndConnect : ServerUiEvent
}
