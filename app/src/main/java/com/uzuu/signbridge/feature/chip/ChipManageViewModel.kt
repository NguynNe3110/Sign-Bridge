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
import java.text.Normalizer

class ChipManageViewModel(
    private val context: ContextType,
    private val chipRepository: ConversationRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChipManageUiState(context = context))
    val uiState = _uiState.asStateFlow()

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
        if (uiState.value.isSaving) return
        when (event) {
            is ChipManageUiEvent.NewLabelChanged ->
                _uiState.update { it.copy(newLabel = event.value, inputError = null) }
            ChipManageUiEvent.AddClicked -> if (uiState.value.editingChipId == null) saveChip()
            ChipManageUiEvent.SaveEditClicked -> if (uiState.value.editingChipId != null) saveChip()
            is ChipManageUiEvent.EditClicked -> {
                val chip = (uiState.value.personalChips + uiState.value.presetChips)
                    .firstOrNull { it.id == event.id } ?: return
                _uiState.update { it.copy(editingChipId = chip.id, newLabel = chip.label, inputError = null) }
            }
            ChipManageUiEvent.CancelEditClicked ->
                _uiState.update { it.copy(editingChipId = null, newLabel = "", inputError = null) }
            is ChipManageUiEvent.DeleteClicked -> mutate {
                chipRepository.deletePersonalChip(event.id)
                _uiState.update {
                    if (it.editingChipId == event.id) it.copy(editingChipId = null, newLabel = "", inputError = null)
                    else it
                }
            }
        }
    }

    private fun saveChip() {
        val state = uiState.value
        val label = Normalizer.normalize(state.newLabel.trim(), Normalizer.Form.NFC)
        val error = when {
            label.isEmpty() -> ChipInputError.EMPTY
            label.length > MAX_LABEL_LENGTH -> ChipInputError.TOO_LONG
            label.any { it.isISOControl() } -> ChipInputError.INVALID_CHARACTERS
            (state.personalChips + state.presetChips).any {
                it.id != state.editingChipId && it.label.equals(label, ignoreCase = true)
            } ->
                ChipInputError.DUPLICATE
            else -> null
        }
        if (error != null) { _uiState.update { it.copy(inputError = error) }; return }
        mutate {
            if (state.editingChipId == null) chipRepository.addChip(context, label, ChipCategory.DETAIL)
            else chipRepository.updateChip(state.editingChipId, label)
            _uiState.update { it.copy(newLabel = "", editingChipId = null, inputError = null) }
        }
    }

    private fun mutate(block: suspend () -> Unit) {
        if (uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try { block()
            } catch (cancel: CancellationException) { throw cancel
            } catch (_: Exception) { _uiState.update { it.copy(inputError = ChipInputError.SAVE_FAILED) }
            } finally { _uiState.update { it.copy(isSaving = false) } }
        }
    }

    companion object { const val MAX_LABEL_LENGTH = 50 }
}
