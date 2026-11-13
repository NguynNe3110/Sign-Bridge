package com.uzuu.signbridge.feature.chip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzuu.signbridge.domain.model.ContextType
import com.uzuu.signbridge.domain.repository.ChipRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChipManageViewModel(
    private val context: ContextType,
    private val chipRepository: ChipRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChipManageUiState(context = context))
    val uiState: StateFlow<ChipManageUiState> = _uiState.asStateFlow()

    init {
        chipRepository.observeChips(context)
            .onEach { chips ->
                val (personal, preset) = chips.partition { it.isPersonal }
                _uiState.update { it.copy(personalChips = personal, presetChips = preset) }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: ChipManageUiEvent) {
        when (event) {
            is ChipManageUiEvent.NewLabelChanged ->
                _uiState.update { it.copy(newLabel = event.value, inputError = null) }
            ChipManageUiEvent.AddClicked -> addChip()
            is ChipManageUiEvent.DeleteClicked ->
                viewModelScope.launch { chipRepository.deleteChip(event.id) }
        }
    }

    private fun addChip() {
        val state = _uiState.value
        val label = state.newLabel.trim()
        val error = when {
            label.isEmpty() -> ChipInputError.EMPTY
            label.length > MAX_LABEL_LENGTH -> ChipInputError.TOO_LONG
            (state.personalChips + state.presetChips).any { it.label.equals(label, ignoreCase = true) } ->
                ChipInputError.DUPLICATE
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(inputError = error) }
            return
        }
        _uiState.update { it.copy(newLabel = "", inputError = null) }
        viewModelScope.launch { chipRepository.addPersonalChip(context, label) }
    }

    companion object {
        /** `/api/expand`: mỗi thẻ ≤ 50 ký tự. */
        const val MAX_LABEL_LENGTH = 50
    }
}
