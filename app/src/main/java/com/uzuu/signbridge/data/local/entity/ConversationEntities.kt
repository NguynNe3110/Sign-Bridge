package com.uzuu.signbridge.data.local.entity

data class QuickChipEntity(
    val id: String, val contextId: String, val label: String, val category: String,
    val usageCount: Int, val isPersonal: Boolean,
)

data class MessageTurnEntity(
    val id: String, val sessionId: String, val direction: String, val rawInput: String,
    val processedText: String, val iconCode: String?, val isOffline: Boolean, val createdAt: Long,
)

