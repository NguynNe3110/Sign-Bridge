package com.uzuu.signbridge.feature.devicecheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzuu.signbridge.domain.model.*
import com.uzuu.signbridge.domain.repository.*
import com.uzuu.signbridge.domain.usecase.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DeviceCheckViewModel(
    private val conversation: ConversationRepository,
    private val translation: TranslationRepository,
    private val device: DeviceRepository,
    private val sendChips: SendChipsUseCase,
    private val recognizeSign: RecognizeSignUseCase,
    private val summarizeSpeech: SummarizeSpeechUseCase,
    private val speakReply: SpeakQuickReplyUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DeviceCheckUiState())
    val state = mutableState.asStateFlow()
    private var work: Job? = null
    private var captureTimer: Job? = null
    private var workVersion = 0

    init { loadContext(ServiceContext.CAFE) }

    fun onEvent(event: DeviceCheckUiEvent) {
        when (event) {
            is DeviceCheckUiEvent.ShowMessage -> mutableState.update { it.copy(message = event.message) }
            is DeviceCheckUiEvent.CameraReady -> mutableState.update { it.copy(cameraReady = event.ready) }
            DeviceCheckUiEvent.Backgrounded -> cancelWork()
            DeviceCheckUiEvent.CancelCapture -> {
                if (state.value.operation in setOf(DeviceOperation.CAPTURING_SIGN, DeviceOperation.RECORDING)) {
                    cancelWork()
                    mutableState.update { it.copy(message = "Đã hủy thu. Giữ nút tại chỗ để ghi, hoặc cuộn để xem phần khác.") }
                }
            }
            DeviceCheckUiEvent.FinishSign -> finishSign()
            DeviceCheckUiEvent.FinishRecording -> finishRecording()
            else -> if (state.value.isIdle) handleIdleEvent(event)
        }
    }

    private fun handleIdleEvent(event: DeviceCheckUiEvent) {
        val current = state.value
        when (event) {
            is DeviceCheckUiEvent.ContextSelected -> if (event.context != current.context) loadContext(event.context)
            DeviceCheckUiEvent.RetryLoad -> loadContext(current.context)
            is DeviceCheckUiEvent.ChipToggled -> {
                if (current.chips.none { it.id == event.id }) return
                val selected = current.selectedIds.toMutableSet()
                if (!selected.remove(event.id)) {
                    if (selected.size >= 3) {
                        mutableState.update { it.copy(message = "Chọn tối đa 3 thẻ, theo thứ tự muốn nói.") }
                        return
                    }
                    selected.add(event.id)
                }
                mutableState.update { it.copy(selectedIds = selected) }
            }
            is DeviceCheckUiEvent.AddChip -> runWork {
                conversation.addChip(current.context, event.label, ChipCategory.DETAIL)
                refreshLocal()
                mutableState.update { it.copy(message = "Đã lưu thẻ cá nhân trên thiết bị.") }
            }
            is DeviceCheckUiEvent.DeleteChip -> runWork {
                conversation.deletePersonalChip(event.id)
                mutableState.update { it.copy(selectedIds = it.selectedIds - event.id) }
                refreshLocal()
                mutableState.update { it.copy(message = "Đã xóa thẻ cá nhân.") }
            }
            DeviceCheckUiEvent.SendChips -> current.session?.let { session ->
                val selected = current.selectedIds.mapNotNull { id -> current.chips.find { it.id == id } }
                runWork {
                    val result = sendChips(session, selected, current.incoming?.summary)
                    mutableState.update { it.copy(outgoing = result.sentence, isOffline = result.isOffline,
                        signResult = null, selectedIds = emptySet(),
                        message = if (result.isOffline) "Offline: hiển thị nguyên văn các thẻ để nhân viên đọc." else "Đã tạo câu và lưu lịch sử.") }
                    refreshLocal()
                    // Offline spec calls for large text; speaking is optional via Replay.
                    if (!result.isOffline) speak(result.sentence)
                }
            }
            DeviceCheckUiEvent.StartSign -> startCapture(sign = true)
            DeviceCheckUiEvent.StartRecording -> startCapture(sign = false)
            DeviceCheckUiEvent.CheckHealth -> runWork {
                val up = translation.checkHealth()
                mutableState.update { it.copy(message = if (up) "Server sẵn sàng (UP)." else "Server chưa sẵn sàng. Thẻ offline vẫn dùng được.") }
            }
            DeviceCheckUiEvent.Replay -> if (current.outgoing.isNotBlank()) runWork { speak(current.outgoing) }
            is DeviceCheckUiEvent.ReplySelected -> {
                if (event.reply !in current.incoming?.quickReplies.orEmpty()) return
                current.session?.let { session ->
                    runWork {
                        mutableState.update { it.copy(outgoing = event.reply, isOffline = false, operation = DeviceOperation.SPEAKING) }
                        try { speakReply(session, event.reply); speechCompleted()
                        } finally { refreshLocal() }
                    }
                }
            }
            else -> Unit
        }
    }

    private fun startCapture(sign: Boolean) {
        if (state.value.session == null) return
        try {
            device.stopSpeaking()
            if (sign) device.startSignCapture() else device.startRecording()
            mutableState.update { it.copy(
                operation = if (sign) DeviceOperation.CAPTURING_SIGN else DeviceOperation.RECORDING,
                message = if (sign) "Đang lấy khung hình. Giữ ít nhất 1,5 giây, tối đa 6 giây."
                    else "Đang thu lời nhân viên. Thả nút để gửi, tối đa 60 giây.",
            ) }
            captureTimer = viewModelScope.launch {
                delay(if (sign) CapturePolicy.MAX_SIGN_DURATION_MS else CapturePolicy.MAX_AUDIO_DURATION_MS)
                if (sign) finishSign() else finishRecording()
            }
        } catch (error: Exception) {
            device.cancelCapture()
            showError(error)
        }
    }

    private fun finishSign() {
        val current = state.value
        if (current.operation != DeviceOperation.CAPTURING_SIGN) return
        captureTimer?.cancel()
        val session = current.session ?: return
        runWork {
            val frames = device.finishSignCapture()
            val result = recognizeSign(session, frames, current.incoming?.summary)
            mutableState.update { it.copy(signResult = result, message = if (result.needsConfirmation)
                "Chưa chắc ký hiệu. Chọn thẻ xác nhận bên dưới rồi gửi; chưa phát loa."
                else "Đã nhận ký hiệu và lưu câu.", outgoing = if (result.needsConfirmation) it.outgoing else result.sentence,
                isOffline = false) }
            refreshLocal()
            if (!result.needsConfirmation) speak(result.sentence)
        }
    }

    private fun finishRecording() {
        val current = state.value
        if (current.operation != DeviceOperation.RECORDING) return
        captureTimer?.cancel()
        val session = current.session ?: return
        runWork {
            val audio = device.finishRecording()
            val result = summarizeSpeech(session, audio, current.outgoing.takeIf { it.isNotBlank() })
            mutableState.update { it.copy(incoming = result, message = "Đã nhận tóm tắt. Có thể chọn câu trả lời nhanh.") }
            refreshLocal()
        }
    }

    private fun loadContext(context: ServiceContext) {
        // Never leave a stale, already closed session usable after a failed context switch.
        mutableState.update { it.copy(context = context, session = null, chips = emptyList(),
            selectedIds = emptySet(), outgoing = "", incoming = null, signResult = null) }
        runWork(DeviceOperation.LOADING) {
            val session = conversation.createSession(context)
            val chips = conversation.getChips(context)
            val history = conversation.getHistory()
            mutableState.value = DeviceCheckUiState(context = context, session = session, chips = chips,
                history = history, operation = DeviceOperation.LOADING, cameraReady = state.value.cameraReady,
                speechCompletedCount = state.value.speechCompletedCount,
                message = "Sẵn sàng. Thêm thẻ hoặc chọn 1–3 thẻ để thử lưu và gửi.")
        }
    }

    private suspend fun refreshLocal() {
        val current = state.value
        val chips = conversation.getChips(current.context)
        val history = conversation.getHistory()
        mutableState.update { it.copy(chips = chips, history = history) }
    }

    private suspend fun speak(sentence: String) {
        mutableState.update { it.copy(operation = DeviceOperation.SPEAKING) }
        device.speak(sentence)
        speechCompleted()
    }

    private fun speechCompleted() {
        mutableState.update { it.copy(message = "Đã phát xong câu nói.", speechCompletedCount = it.speechCompletedCount + 1) }
    }

    private fun runWork(operation: DeviceOperation = DeviceOperation.PROCESSING, block: suspend () -> Unit) {
        val version = ++workVersion
        mutableState.update { it.copy(operation = operation) }
        work = viewModelScope.launch {
            try { block()
            } catch (cancel: CancellationException) { throw cancel
            } catch (error: Exception) { showError(error)
            } finally {
                if (version == workVersion) mutableState.update { it.copy(operation = DeviceOperation.IDLE) }
            }
        }
    }

    private fun showError(error: Exception) {
        mutableState.update { it.copy(operation = DeviceOperation.IDLE,
            message = if (error is BridgeException) error.message.orEmpty() else "Thao tác bị gián đoạn. Vui lòng thử lại.") }
    }

    private fun cancelWork() {
        workVersion++
        captureTimer?.cancel()
        work?.cancel()
        device.cancelCapture()
        device.stopSpeaking()
        mutableState.update { it.copy(operation = DeviceOperation.IDLE,
            message = "Đã dừng tác vụ khi rời màn hình. Dữ liệu đã lưu vẫn còn trên thiết bị.") }
    }

    override fun onCleared() {
        captureTimer?.cancel()
        device.cancelCapture()
        device.stopSpeaking()
    }
}

