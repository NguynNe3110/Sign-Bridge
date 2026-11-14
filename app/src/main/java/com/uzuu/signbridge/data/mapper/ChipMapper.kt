package com.uzuu.signbridge.data.mapper

import com.uzuu.signbridge.data.local.entity.QuickChipEntity
import com.uzuu.signbridge.data.remote.dto.response.ChipResponseDto
import com.uzuu.signbridge.domain.model.ChipCategory
import com.uzuu.signbridge.domain.model.QuickChip
import com.uzuu.signbridge.domain.model.ServiceContext
import org.json.JSONObject

fun JSONObject.toChipResponseDto(): ChipResponseDto = ChipResponseDto(
    id = getString("id"),
    text = getString("text"),
    context = getString("context"),
    isSystem = optBoolean("is_system", false),
    userId = if (isNull("user_id")) null else optString("user_id").takeIf { it.isNotBlank() && it != "null" },
    usageCount = optInt("usage_count", 0),
)

fun ChipResponseDto.toDomain(): QuickChip = QuickChip(
    id = id,
    context = runCatching { ServiceContext.valueOf(context) }.getOrDefault(ServiceContext.CUSTOM),
    label = text,
    category = ChipCategory.DETAIL,
    usageCount = usageCount,
    isPersonal = !isSystem,
)

fun ChipResponseDto.toEntity(): QuickChipEntity = QuickChipEntity(
    id = id,
    contextId = context,
    label = text,
    category = "DETAIL",
    usageCount = usageCount,
    isPersonal = !isSystem,
)
