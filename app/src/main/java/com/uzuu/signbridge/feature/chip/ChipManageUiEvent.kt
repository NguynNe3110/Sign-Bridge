package com.uzuu.signbridge.feature.chip

sealed interface ChipManageUiEvent {
    data class NewLabelChanged(val value: String) : ChipManageUiEvent
    data object AddClicked : ChipManageUiEvent
    data class DeleteClicked(val id: Long) : ChipManageUiEvent
}
