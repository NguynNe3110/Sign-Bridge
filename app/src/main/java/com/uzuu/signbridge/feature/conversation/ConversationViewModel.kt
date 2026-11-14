package com.uzuu.signbridge.feature.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzuu.signbridge.core.result.ErrorCode
import com.uzuu.signbridge.core.ui.HapticPattern
import com.uzuu.signbridge.domain.model.*
import com.uzuu.signbridge.domain.repository.ConversationRepository
import com.uzuu.signbridge.domain.repository.DeviceRepository
import com.uzuu.signbridge.domain.usecase.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Integration only: TV1 renders state; TV2 usecases own API, persistence and usage updates. */
class ConversationViewModel(
    private val context: ContextType,
    private val conversationRepository: ConversationRepository,
    private val deviceRepository: DeviceRepository,
    private val sendChips: SendChipsUseCase,
    private val recognizeSign: RecognizeSignUseCase,
    private val summarizeSpeech: SummarizeSpeechUseCase,
    @Suppress("unused") private val speakReply: SpeakQuickReplyUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConversationUiState(context = context, isInitializing = true))
    val uiState = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<ConversationUiEffect>(
        extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val effects = _effects.asSharedFlow()
    private var session: ConversationSession? = null
    private var task: Job? = null
    private var timer: Job? = null
    private var version = 0

    init {
        viewModelScope.launch {
            try {
                conversationRepository.observeChips(context).collect { chips ->
                    _uiState.update { state ->
                        val selected = state.selectedChips.mapNotNull { label ->
                            val previous = state.chips.firstOrNull { it.label == label }
                            if (previous != null) chips.firstOrNull { it.id == previous.id }?.label
                            else label.takeIf { candidate ->
                                chips.any { it.label == candidate } ||
                                    state.pendingSign?.suggestedChips?.contains(candidate) == true ||
                                    state.summary?.quickReplies?.contains(candidate) == true
                            }
                        }.distinct()
                        state.copy(chips = chips, selectedChips = selected)
                    }
                }
            } catch (cancel: CancellationException) { throw cancel
            } catch (error: Exception) { showError(error, false) }
        }
        initialize()
    }

    private fun initialize() {
        _uiState.update { it.copy(isInitializing = true) }
        runTask(false) {
            session = conversationRepository.createSession(context)
        }
    }

    fun onEvent(event: ConversationUiEvent) {
        when (event) {
            ConversationUiEvent.ScreenStopped -> cancel()
            ConversationUiEvent.CameraUnavailable -> _uiState.update {
                it.copy(deafError = ErrorCode.DEVICE_ERROR, deafErrorMessage = null)
            }
            ConversationUiEvent.CaptureCancelled -> if (uiState.value.isSigning || uiState.value.isStaffRecording) cancel()
            is ConversationUiEvent.PermissionDenied -> _uiState.update {
                if (event.camera) it.copy(deafError = ErrorCode.CAMERA_PERMISSION_REQUIRED, deafErrorMessage = null)
                else it.copy(staffError = ErrorCode.MIC_PERMISSION_REQUIRED, staffErrorMessage = null,
                    showMicReadyHint = false)
            }
            ConversationUiEvent.MicPermissionGranted -> _uiState.update {
                it.copy(showMicReadyHint = true, staffError = null, staffErrorMessage = null)
            }
            ConversationUiEvent.DeafErrorDismissed -> _uiState.update { it.copy(deafError = null, deafErrorMessage = null) }
            ConversationUiEvent.StaffErrorDismissed -> _uiState.update { it.copy(staffError = null, staffErrorMessage = null) }
            ConversationUiEvent.SignReleased -> finishSign()
            ConversationUiEvent.MicReleased -> finishVoice()
            ConversationUiEvent.MicPressed -> if (uiState.value.canStartMic) {
                if (session == null) { initialize(); return }
                // Cancel the speech job too: stopping the TTS engine alone may leave its waiter active.
                if (uiState.value.isSpeaking) cancel()
                startCapture(false)
            }
            else -> if (!uiState.value.isBusy) {
                if (session == null) { initialize(); return }
                when (event) {
                    ConversationUiEvent.SignPressed -> startCapture(true)
                    is ConversationUiEvent.ChipToggled -> toggleChip(event.label)
                    ConversationUiEvent.ClearChipsClicked -> _uiState.update { it.copy(selectedChips = emptyList()) }
                    ConversationUiEvent.SendChipsClicked -> sendSelectedChips()
                    is ConversationUiEvent.QuickReplyClicked -> toggleQuickReply(event.reply)
                    ConversationUiEvent.ReplayClicked -> {
                        val sentence = uiState.value.spokenSentence
                        if (sentence.isNotBlank()) {
                            _uiState.update { it.copy(isSpeaking = true) }
                            runTask(false) { deviceRepository.speak(sentence); speechFinished() }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun toggleQuickReply(reply: String) {
        val current = uiState.value
        val quickReplies = current.summary?.quickReplies.orEmpty()
        _uiState.update { state ->
            when {
                reply in state.selectedChips -> state.copy(selectedChips = state.selectedChips - reply)
                quickReplies.isNotEmpty() && quickReplies.any { it in state.selectedChips } -> {
                    val updated = state.selectedChips.map { if (it in quickReplies) reply else it }
                    state.copy(selectedChips = updated)
                }
                state.canSelectMoreChips -> state.copy(selectedChips = state.selectedChips + reply)
                else -> state
            }
        }
    }

    private fun toggleChip(label: String) {
        val current = uiState.value
        val quickReplies = current.summary?.quickReplies.orEmpty()
        if (label in quickReplies) {
            toggleQuickReply(label)
            return
        }
        if (label !in current.chips.map { it.label } && label !in current.pendingSign?.suggestedChips.orEmpty()) return
        _uiState.update { state ->
            when {
                label in state.selectedChips -> state.copy(selectedChips = state.selectedChips - label)
                state.canSelectMoreChips -> state.copy(selectedChips = state.selectedChips + label)
                else -> state
            }
        }
    }

    private fun startCapture(sign: Boolean) {
        try {
            deviceRepository.stopSpeaking()
            if (sign) deviceRepository.startSignCapture() else deviceRepository.startRecording()
            _uiState.update {
                if (sign) it.copy(isSigning = true, deafError = null, deafErrorMessage = null)
                else it.copy(isStaffRecording = true, staffError = null, staffErrorMessage = null,
                    showMicReadyHint = false)
            }
            if (!sign) _effects.tryEmit(ConversationUiEffect.Vibrate(HapticPattern.STAFF_STARTED_SPEAKING))
            timer = viewModelScope.launch {
                delay(if (sign) CapturePolicy.MAX_SIGN_DURATION_MS else CapturePolicy.MAX_AUDIO_DURATION_MS)
                if (sign) finishSign() else finishVoice()
            }
        } catch (error: Exception) { deviceRepository.cancelCapture(); showError(error, !sign) }
    }

    private fun finishSign() {
        if (!uiState.value.isSigning) return
        val currentSession = session ?: return
        timer?.cancel()
        _uiState.update { it.copy(isSigning = false, isRecognizing = true) }
        runTask(false) {
            val frames = deviceRepository.finishSignCapture()
            val result = recognizeSign(currentSession, frames, uiState.value.summary?.summary)
            _uiState.update { it.copy(recognizedGlosses = result.glosses,
                pendingSign = result.takeIf { it.needsConfirmation }, selectedChips = emptyList()) }
            if (!result.needsConfirmation) displayAndSpeak(result.sentence, false)
        }
    }

    private fun sendSelectedChips() {
        val state = uiState.value
        val currentSession = session ?: return
        if (state.selectedChips.isEmpty()) return
        _uiState.update { it.copy(isExpanding = true, deafError = null, deafErrorMessage = null) }
        runTask(false) {
            val incoming = state.summary?.summary?.ifBlank { null } ?: state.summary?.transcript?.ifBlank { null }
            val result = sendChips.fromLabels(currentSession, state.selectedChips, incoming)
            displayAndSpeak(result.sentence, result.isOffline)
        }
    }

    private suspend fun displayAndSpeak(sentence: String, offline: Boolean) {
        _uiState.update { it.copy(spokenSentence = sentence, isOffline = offline,
            isRecognizing = false, isExpanding = false, pendingSign = null,
            selectedChips = emptyList(), isSpeaking = !offline) }
        if (!offline) { deviceRepository.speak(sentence); speechFinished() }
    }

    private fun finishVoice() {
        if (!uiState.value.isStaffRecording) return
        val currentSession = session ?: return
        timer?.cancel()
        _uiState.update { it.copy(isStaffRecording = false, isSummarizing = true) }
        runTask(true) {
            val audio = deviceRepository.finishRecording()
            val result = summarizeSpeech(currentSession, audio, uiState.value.spokenSentence.ifBlank { null })
            _uiState.update { it.copy(summary = result, pendingSign = null, recognizedGlosses = emptyList()) }
        }
    }

    private fun speechFinished() {
        _effects.tryEmit(ConversationUiEffect.Vibrate(HapticPattern.SPEECH_FINISHED))
    }

    private fun runTask(staff: Boolean, block: suspend () -> Unit) {
        val currentVersion = ++version
        task = viewModelScope.launch {
            try { block()
            } catch (cancel: CancellationException) { throw cancel
            } catch (error: Exception) { showError(error, staff || uiState.value.isSpeaking)
            } finally {
                if (version == currentVersion) clearBusy()
            }
        }
    }

    private fun clearBusy() {
        _uiState.update { it.copy(isInitializing = false, isSigning = false, isRecognizing = false,
            isExpanding = false, isStaffRecording = false, isSummarizing = false, isSpeaking = false) }
    }

    private fun showError(error: Exception, staff: Boolean) {
        val code = if (error is BridgeException) when (error.code) {
            BridgeError.NETWORK -> ErrorCode.NETWORK_UNAVAILABLE
            BridgeError.NOT_CONFIGURED -> ErrorCode.NOT_CONFIGURED
            BridgeError.DEVICE -> ErrorCode.DEVICE_ERROR
            BridgeError.STORAGE -> ErrorCode.STORAGE_ERROR
            else -> ErrorCode.valueOf(error.code.name)
        } else ErrorCode.UNKNOWN
        val message = (error as? BridgeException)?.message?.takeIf { it.isNotBlank() }
        _uiState.update {
            if (staff) it.copy(staffError = code, staffErrorMessage = message)
            else it.copy(deafError = code, deafErrorMessage = message)
        }
    }

    private fun cancel() {
        version++
        timer?.cancel()
        task?.cancel()
        deviceRepository.cancelCapture()
        deviceRepository.stopSpeaking()
        clearBusy()
    }

    override fun onCleared() { cancel() }

    companion object { const val MAX_PERSONAL_CHIPS = 20 }
}
