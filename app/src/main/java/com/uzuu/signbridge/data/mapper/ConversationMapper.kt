package com.uzuu.signbridge.data.mapper

import com.uzuu.signbridge.data.local.entity.MessageTurnEntity
import com.uzuu.signbridge.data.local.entity.QuickChipEntity
import com.uzuu.signbridge.domain.model.*

fun QuickChipEntity.toDomain() = QuickChip(id, ServiceContext.valueOf(contextId), label,
    ChipCategory.valueOf(category), usageCount, isPersonal)

fun MessageTurnEntity.toDomain() = MessageTurn(id, sessionId, Direction.valueOf(direction),
    rawInput, processedText, iconCode, isOffline, createdAt)

