package com.uzuu.signbridge.feature.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzuu.signbridge.core.result.AppError
import com.uzuu.signbridge.core.result.AppResult
import com.uzuu.signbridge.core.result.ErrorCode
import com.uzuu.signbridge.core.ui.HapticPattern
import com.uzuu.signbridge.domain.model.ContextType
import com.uzuu.signbridge.domain.model.MessageTurn
import com.uzuu.signbridge.domain.model.TurnDirection
import com.uzuu.signbridge.domain.repository.ChipRepository
import com.uzuu.signbridge.domain.repository.ConversationRepository
import com.uzuu.signbridge.domain.repository.SpeakerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConversationViewModel(
    private val context: ContextType,
    private val conversationRepository: ConversationRepository,
    private val speakerRepository: SpeakerRepository,
    private val chipRepository: ChipRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationUiState(context = context))
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    // Rung chỉ có ý nghĩa tức thời: không có UI đang collect (app ở nền) thì bỏ qua,
    // không phát lại khi UI quay lại.
    private val _effects = MutableSharedFlow<ConversationUiEffect>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val effects: SharedFlow<ConversationUiEffect> = _effects.asSharedFlow()

    private var signStartJob: Job? = null
    private var voiceStartJob: Job? = null

    init {
        chipRepository.observeChips(context)
            .onEach { chips -> _uiState.update { it.copy(chips = chips) } }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: ConversationUiEvent) {
        when (event) {
            ConversationUiEvent.SignPressed -> startSign()
            ConversationUiEvent.SignReleased -> finishSign()
            is ConversationUiEvent.ChipToggled -> toggleChip(event.label)
            ConversationUiEvent.ClearChipsClicked -> _uiState.update { it.copy(selectedChips = emptyList()) }
            ConversationUiEvent.SendChipsClicked -> sendChips()
            is ConversationUiEvent.QuickReplyClicked -> speakOutgoing(event.reply)
            ConversationUiEvent.DeafErrorDismissed -> _uiState.update { it.copy(deafError = null) }
            ConversationUiEvent.MicPressed -> startVoice()
            ConversationUiEvent.MicReleased -> finishVoice()
            ConversationUiEvent.ReplayClicked -> replay()
            ConversationUiEvent.StaffErrorDismissed -> _uiState.update { it.copy(staffError = null) }
        }
    }

    // region Chiều đi: ký hiệu / thẻ -> câu nói

    private fun startSign() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isSigning = true, deafError = null) }
        signStartJob = viewModelScope.launch { conversationRepository.startSignCapture() }
    }

    private fun finishSign() {
        if (!_uiState.value.isSigning) return
        _uiState.update { it.copy(isSigning = false, isRecognizing = true) }
        viewModelScope.launch {
            signStartJob?.join()
            val state = _uiState.value
            val result = safeCall {
                conversationRepository.stopSignCaptureAndRecognize(
                    context = context,
                    personalChips = state.chips.filter { it.isPersonal }.map { it.label }.take(MAX_PERSONAL_CHIPS),
                    previousIncoming = state.summary?.summary,
                )
            }
            when (result) {
                is AppResult.Success -> {
                    val sign = result.data
                    if (sign.needsConfirmation) {
                        _uiState.update {
                            it.copy(
                                isRecognizing = false,
                                recognizedGlosses = sign.glosses,
                                pendingSign = sign,
                                selectedChips = emptyList(),
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(isRecognizing = false, recognizedGlosses = sign.glosses, pendingSign = null)
                        }
                        speakOutgoing(sign.spokenSentence)
                    }
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isRecognizing = false, deafError = result.error.code)
                }
            }
        }
    }

    private fun toggleChip(label: String) {
        _uiState.update { state ->
            when {
                label in state.selectedChips -> state.copy(selectedChips = state.selectedChips - label)
                state.canSelectMoreChips -> state.copy(selectedChips = state.selectedChips + label)
                else -> state
            }
        }
    }

    private fun sendChips() {
        val state = _uiState.value
        if (state.selectedChips.isEmpty() || state.isBusy) return
        _uiState.update { it.copy(isExpanding = true, deafError = null) }
        viewModelScope.launch {
            val usedIds = state.chips.filter { it.label in state.selectedChips }.map { it.id }
            if (usedIds.isNotEmpty()) chipRepository.increaseUsage(usedIds)

            val result = safeCall {
                conversationRepository.expandChips(context, state.selectedChips, state.summary?.summary)
            }
            _uiState.update { it.copy(isExpanding = false) }
            when (result) {
                is AppResult.Success -> speakOutgoing(result.data)
                is AppResult.Failure -> _uiState.update { it.copy(deafError = result.error.code) }
            }
        }
    }

    /** Hiện câu cho nhân viên, lưu lượt OUTGOING, phát loa rồi rung báo đã đọc xong. */
    private fun speakOutgoing(sentence: String) {
        if (sentence.isBlank() || _uiState.value.isSpeaking) return
        _uiState.update {
            it.copy(
                spokenSentence = sentence,
                isSpeaking = true,
                selectedChips = emptyList(),
                pendingSign = null,
                deafError = null,
            )
        }
        viewModelScope.launch {
            conversationRepository.saveTurn(
                MessageTurn(context, TurnDirection.OUTGOING, sentence, clock()),
            )
            speak(sentence)
        }
    }

    private fun replay() {
        val state = _uiState.value
        if (state.spokenSentence.isBlank() || state.isBusy) return
        _uiState.update { it.copy(isSpeaking = true) }
        viewModelScope.launch { speak(state.spokenSentence) }
    }

    private suspend fun speak(sentence: String) {
        try {
            speakerRepository.speak(sentence)
        } finally {
            _uiState.update { it.copy(isSpeaking = false) }
        }
        _effects.tryEmit(ConversationUiEffect.Vibrate(HapticPattern.SPEECH_FINISHED))
    }

    // endregion

    // region Chiều về: giọng nhân viên -> tóm tắt

    private fun startVoice() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isStaffRecording = true, staffError = null) }
        _effects.tryEmit(ConversationUiEffect.Vibrate(HapticPattern.STAFF_STARTED_SPEAKING))
        voiceStartJob = viewModelScope.launch { conversationRepository.startVoiceRecording() }
    }

    private fun finishVoice() {
        if (!_uiState.value.isStaffRecording) return
        _uiState.update { it.copy(isStaffRecording = false, isSummarizing = true) }
        viewModelScope.launch {
            voiceStartJob?.join()
            val previousOutgoing = _uiState.value.spokenSentence.ifBlank { null }
            val result = safeCall {
                conversationRepository.stopVoiceRecordingAndSummarize(context, previousOutgoing)
            }
            when (result) {
                is AppResult.Success -> {
                    val summary = result.data
                    _uiState.update {
                        it.copy(
                            isSummarizing = false,
                            summary = summary,
                            pendingSign = null,
                            recognizedGlosses = emptyList(),
                        )
                    }
                    conversationRepository.saveTurn(
                        MessageTurn(context, TurnDirection.INCOMING, summary.summary, clock()),
                    )
                }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isSummarizing = false, staffError = result.error.code)
                }
            }
        }
    }

    // endregion

    /** Implementation thật có thể ném lỗi; đổi thành Failure nhưng không nuốt cancellation. */
    private suspend fun <T> safeCall(block: suspend () -> AppResult<T>): AppResult<T> =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppResult.Failure(AppError(ErrorCode.UNKNOWN, e.message))
        }

    override fun onCleared() {
        speakerRepository.stop()
        super.onCleared()
    }

    companion object {
        /** `/api/sign` nhận tối đa 20 `personalChips`. */
        private const val MAX_PERSONAL_CHIPS = 20
    }
}
