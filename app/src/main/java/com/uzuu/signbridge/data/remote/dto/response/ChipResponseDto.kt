package com.uzuu.signbridge.data.remote.dto.response

data class ChipResponseDto(
    val id: String,
    val text: String,
    val context: String,
    val isSystem: Boolean,
    val userId: String?,
    val usageCount: Int = 0,
)
