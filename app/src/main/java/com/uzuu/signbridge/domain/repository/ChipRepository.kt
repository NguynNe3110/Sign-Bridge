package com.uzuu.signbridge.domain.repository

import com.uzuu.signbridge.domain.model.ContextType
import com.uzuu.signbridge.domain.model.QuickChip
import kotlinx.coroutines.flow.Flow

/** Thẻ mẫu + thẻ cá nhân theo ngữ cảnh (TV2 cài đặt bằng SQLite). */
interface ChipRepository {

    fun observeChips(context: ContextType): Flow<List<QuickChip>>

    suspend fun addPersonalChip(context: ContextType, label: String)

    suspend fun deleteChip(id: Long)

    suspend fun increaseUsage(ids: List<Long>)
}
