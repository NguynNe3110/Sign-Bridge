package com.uzuu.signbridge.feature.chip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzuu.signbridge.domain.model.ChipCategory
import com.uzuu.signbridge.domain.model.ContextType
import com.uzuu.signbridge.domain.repository.ConversationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChipManageViewModel(
    private val context: ContextType,
    private val chipRepository: ConversationRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChipManageUiState(context = context))
    val uiState = _uiState.asStateFlow()
    private var saving = false

    init {
        viewModelScope.launch {
            try {
                chipRepository.observeChips(context).collect { chips ->
                    val (personal, preset) = chips.partition { it.isPersonal }
                    _uiState.update { it.copy(personalChips = personal, presetChips = preset) }
                }
            } catch (cancel: CancellationException) { throw cancel
            } catch (_: Exception) { _uiState.update { it.copy(inputError = ChipInputError.SAVE_FAILED) } }
        }
    }

    fun onEvent(event: ChipManageUiEvent) {
        when (event) {
            is ChipManageUiEvent.NewLabelChanged ->
                _uiState.update { it.copy(newLabel = event.value, inputError = null) }
            ChipManageUiEvent.AddClicked -> addChip()
            is ChipManageUiEvent.DeleteClicked -> mutate {
                chipRepository.deletePersonalChip(event.id)
            }
        }
    }

    private fun addChip() {
        if (saving) return
        val state = uiState.value
        val label = state.newLabel.trim()
        val error = when {
            label.isEmpty() -> ChipInputError.EMPTY
            label.length > MAX_LABEL_LENGTH -> ChipInputError.TOO_LONG
            (state.personalChips + state.presetChips).any { it.label.equals(label, ignoreCase = true) } ->
                ChipInputError.DUPLICATE
            else -> null
        }
        if (error != null) { _uiState.update { it.copy(inputError = error) }; return }
        mutate {
            chipRepository.addChip(context, label, ChipCategory.DETAIL)
            _uiState.update { it.copy(newLabel = if (it.newLabel == state.newLabel) "" else it.newLabel, inputError = null) }
        }
    }

    private fun mutate(block: suspend () -> Unit) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            try { block()
            } catch (cancel: CancellationException) { throw cancel
            } catch (_: Exception) { _uiState.update { it.copy(inputError = ChipInputError.SAVE_FAILED) }
            } finally { saving = false }
        }
    }

    companion object { const val MAX_LABEL_LENGTH = 50 }
}
