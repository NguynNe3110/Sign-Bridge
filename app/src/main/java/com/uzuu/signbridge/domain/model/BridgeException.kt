package com.uzuu.signbridge.domain.model

enum class BridgeError {
    INVALID_REQUEST, PAYLOAD_TOO_LARGE, UNSUPPORTED_MEDIA_TYPE,
    NO_SIGN_DETECTED, NO_SPEECH_DETECTED, AI_UPSTREAM_ERROR, AI_TIMEOUT,
    NETWORK, NOT_CONFIGURED, DEVICE, STORAGE,
}

class BridgeException(
    val code: BridgeError,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    val allowsOfflineChips: Boolean
        get() = code in setOf(
            BridgeError.NETWORK, BridgeError.NOT_CONFIGURED,
            BridgeError.AI_TIMEOUT, BridgeError.AI_UPSTREAM_ERROR,
        )
}

