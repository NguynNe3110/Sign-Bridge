package com.uzuu.signbridge.data.remote.dto.request

data class CreateChipRequestDto(
    val text: String,
    val context: String,
    val userId: String? = null,
)
