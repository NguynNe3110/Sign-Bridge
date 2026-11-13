package com.uzuu.signbridge.domain.repository

import com.uzuu.signbridge.domain.model.*

interface ConversationRepository {
    suspend fun createSession(context: ServiceContext): ConversationSession
    suspend fun endSession(id: String)
    suspend fun getChips(context: ServiceContext): List<QuickChip>
    suspend fun addChip(context: ServiceContext, label: String, category: ChipCategory): QuickChip
    suspend fun deletePersonalChip(id: String)
    suspend fun getHistory(limit: Int = 100): List<MessageTurn>
    suspend fun appendTurn(
        sessionId: String,
        direction: Direction,
        rawInput: String,
        processedText: String,
        iconCode: String? = null,
        isOffline: Boolean = false,
        usedChipIds: List<String> = emptyList(),
    ): MessageTurn
}

