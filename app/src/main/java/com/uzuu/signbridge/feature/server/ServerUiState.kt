package com.uzuu.signbridge.feature.server

data class ServerUiState(
    val ipv4: String = "",
    val port: String = "8080",
    val useHttps: Boolean = false,
    val savedUrl: String? = null,
    val isBusy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)
