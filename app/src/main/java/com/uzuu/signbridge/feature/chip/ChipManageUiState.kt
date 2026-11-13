package com.uzuu.signbridge.feature.chip

import com.uzuu.signbridge.domain.model.ContextType
import com.uzuu.signbridge.domain.model.QuickChip

data class ChipManageUiState(
    val context: ContextType,
    val personalChips: List<QuickChip> = emptyList(),
    val presetChips: List<QuickChip> = emptyList(),
    val newLabel: String = "",
    val inputError: ChipInputError? = null,
)

enum class ChipInputError {
    EMPTY,
    TOO_LONG,
    DUPLICATE,
}
