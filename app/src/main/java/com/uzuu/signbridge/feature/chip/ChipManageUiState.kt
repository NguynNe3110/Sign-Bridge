package com.uzuu.signbridge.feature.chip

import com.uzuu.signbridge.domain.model.ContextType
import com.uzuu.signbridge.domain.model.QuickChip

data class ChipManageUiState(
    val context: ContextType,
    val personalChips: List<QuickChip> = emptyList(),
    val presetChips: List<QuickChip> = emptyList(),
    val newLabel: String = "",
    val editingChipId: String? = null,
    val isSaving: Boolean = false,
    val inputError: ChipInputError? = null,
)

enum class ChipInputError {
    EMPTY,
    TOO_LONG,
    INVALID_CHARACTERS,
    DUPLICATE,
    SAVE_FAILED,
}
