package com.uzuu.signbridge.feature.chip

sealed interface ChipManageUiEvent {
    data class NewLabelChanged(val value: String) : ChipManageUiEvent
    data object AddClicked : ChipManageUiEvent
    data class EditClicked(val id: String) : ChipManageUiEvent
    data object SaveEditClicked : ChipManageUiEvent
    data object CancelEditClicked : ChipManageUiEvent
    data class DeleteClicked(val id: String) : ChipManageUiEvent
}
