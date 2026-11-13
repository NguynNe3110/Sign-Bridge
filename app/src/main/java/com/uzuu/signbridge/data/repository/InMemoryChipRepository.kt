package com.uzuu.signbridge.data.repository

import com.uzuu.signbridge.domain.model.ContextType
import com.uzuu.signbridge.domain.model.QuickChip
import com.uzuu.signbridge.domain.repository.ChipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * Bản giả lưu trong bộ nhớ (mất khi đóng app). TV2 thay bằng SQLite.
 * Thẻ của nhóm CUSTOM ("Chung") hiện ở mọi ngữ cảnh.
 */
class InMemoryChipRepository : ChipRepository {

    private val nextId = AtomicLong(1)
    private val chips = MutableStateFlow(seedChips())

    override fun observeChips(context: ContextType): Flow<List<QuickChip>> =
        chips.map { all ->
            all.filter { it.context == context || it.context == ContextType.CUSTOM }
                .sortedWith(
                    compareByDescending<QuickChip> { it.isPersonal }
                        .thenByDescending { it.usageCount },
                )
        }

    override suspend fun addPersonalChip(context: ContextType, label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        chips.update { it + QuickChip(nextId.getAndIncrement(), context, trimmed, isPersonal = true) }
    }

    override suspend fun deleteChip(id: Long) {
        chips.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun increaseUsage(ids: List<Long>) {
        chips.update { list ->
            list.map { if (it.id in ids) it.copy(usageCount = it.usageCount + 1) else it }
        }
    }

    private fun seedChips(): List<QuickChip> {
        val seeds = mapOf(
            ContextType.CUSTOM to listOf("Xin chào", "Cảm ơn", "Có", "Không", "Thanh toán", "Bao nhiêu tiền?"),
            ContextType.CAFE to listOf("Cà phê sữa", "Ít đá", "Mang đi", "Uống tại chỗ"),
            ContextType.PHARMACY to listOf("Đau đầu", "Đau bụng", "Sốt", "Không buồn ngủ", "Uống sau ăn"),
            ContextType.PUBLIC to listOf("Nộp hồ sơ", "Quầy nào?", "Cần giấy tờ gì?", "Chờ bao lâu?"),
        )
        return seeds.flatMap { (context, labels) ->
            labels.map { QuickChip(nextId.getAndIncrement(), context, it, isPersonal = false) }
        }
    }
}
