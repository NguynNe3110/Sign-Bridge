package com.uzuu.signbridge.feature.conversation

import com.uzuu.signbridge.MainDispatcherRule
import com.uzuu.signbridge.core.result.AppError
import com.uzuu.signbridge.core.result.AppResult
import com.uzuu.signbridge.core.result.ErrorCode
import com.uzuu.signbridge.core.ui.HapticPattern
import com.uzuu.signbridge.domain.model.ContextType
import com.uzuu.signbridge.domain.model.Highlight
import com.uzuu.signbridge.domain.model.HighlightType
import com.uzuu.signbridge.domain.model.IconCode
import com.uzuu.signbridge.domain.model.QuickChip
import com.uzuu.signbridge.domain.model.SignResult
import com.uzuu.signbridge.domain.model.SummaryResult
import com.uzuu.signbridge.domain.model.TurnDirection
import com.uzuu.signbridge.domain.usecase.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import com.uzuu.signbridge.domain.model.BridgeError
import com.uzuu.signbridge.domain.model.BridgeException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ConversationViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val conversation = RecordingConversationRepository()
    private val speaker = RecordingSpeakerRepository(conversation)
    private val chips = StubChipRepository(
        listOf(
            QuickChip("1", ContextType.PHARMACY, "Đau đầu", isPersonal = false),
            QuickChip("2", ContextType.PHARMACY, "Sốt", isPersonal = false),
            QuickChip("3", ContextType.PHARMACY, "Ho", isPersonal = false),
            QuickChip("4", ContextType.PHARMACY, "Dị ứng penicillin", isPersonal = true),
        ),
    )

    private fun createViewModel(): ConversationViewModel {
        conversation.savedTurns = chips.savedTurns
        return ConversationViewModel(
        context = ContextType.PHARMACY,
        conversationRepository = chips,
        deviceRepository = speaker,
        sendChips = SendChipsUseCase(chips, conversation),
        recognizeSign = RecognizeSignUseCase(chips, conversation),
        summarizeSpeech = SummarizeSpeechUseCase(chips, conversation),
        speakReply = SpeakQuickReplyUseCase(chips, speaker),
    )
    }

    private val summary = SummaryResult(
        transcript = "Uống một viên, giá ba mươi lăm nghìn.",
        summary = "1 viên • 35.000đ",
        highlights = listOf(Highlight(HighlightType.PRICE, "35.000đ")),
        iconCode = IconCode.PILL,
        quickReplies = listOf("Lấy 1 hộp", "Chuyển khoản QR", "Cảm ơn"),
    )

    @Test
    fun `chips load from repository`() {
        val vm = createViewModel()
        assertEquals(4, vm.uiState.value.chips.size)
    }

    @Test
    fun `select at most 3 chips in tap order and toggle off`() {
        val vm = createViewModel()
        listOf("Sốt", "Đau đầu", "Ho", "Dị ứng penicillin").forEach {
            vm.onEvent(ConversationUiEvent.ChipToggled(it))
        }
        assertEquals(listOf("Sốt", "Đau đầu", "Ho"), vm.uiState.value.selectedChips)

        vm.onEvent(ConversationUiEvent.ChipToggled("Đau đầu"))
        assertEquals(listOf("Sốt", "Ho"), vm.uiState.value.selectedChips)

        vm.onEvent(ConversationUiEvent.ClearChipsClicked)
        assertTrue(vm.uiState.value.selectedChips.isEmpty())
    }

    @Test
    fun `confident sign speaks, saves outgoing turn and vibrates when done`() = runTest(mainRule.dispatcher) {
        val vm = createViewModel()
        val effects = mutableListOf<ConversationUiEffect>()
        backgroundScope.launch { vm.effects.collect { effects += it } }

        vm.onEvent(ConversationUiEvent.SignPressed)
        assertTrue(vm.uiState.value.isSigning)
        vm.onEvent(ConversationUiEvent.SignReleased)

        val state = vm.uiState.value
        assertEquals(1, conversation.startSignCount)
        assertEquals(listOf("Dị ứng penicillin"), conversation.lastPersonalChips)
        assertEquals("Mình bị đau đầu.", state.spokenSentence)
        assertEquals(listOf("ĐAU", "ĐẦU"), state.recognizedGlosses)
        assertNull(state.pendingSign)
        assertFalse(state.isBusy)
        assertEquals(listOf("Mình bị đau đầu."), speaker.spoken)
        assertEquals(TurnDirection.OUTGOING, conversation.savedTurns.single().direction)
        assertEquals(listOf(ConversationUiEffect.Vibrate(HapticPattern.SPEECH_FINISHED)), effects)
    }

    @Test
    fun `low confidence shows suggestions then chips expand into sentence`() {
        val unsure = SignResult(listOf(SignResult.UNKNOWN_GLOSS, "NGÀY"), 0.4, "", listOf("Một ngày", "Hai ngày"))
        conversation.signResult = AppResult.Success(unsure)
        val vm = createViewModel()

        vm.onEvent(ConversationUiEvent.SignPressed)
        vm.onEvent(ConversationUiEvent.SignReleased)

        assertEquals(unsure, vm.uiState.value.pendingSign)
        assertTrue(speaker.spoken.isEmpty())

        vm.onEvent(ConversationUiEvent.ChipToggled("Hai ngày"))
        vm.onEvent(ConversationUiEvent.SendChipsClicked)

        assertEquals(listOf(listOf("Hai ngày") to null), conversation.expandCalls)
        assertEquals("Câu mở rộng.", vm.uiState.value.spokenSentence)
        assertNull(vm.uiState.value.pendingSign)
        assertTrue(vm.uiState.value.selectedChips.isEmpty())
        assertEquals(listOf("Câu mở rộng."), speaker.spoken)
    }

    @Test
    fun `sending chips increases usage and passes previous summary`() {
        conversation.summaryResult = AppResult.Success(summary)
        val vm = createViewModel()
        vm.onEvent(ConversationUiEvent.MicPressed)
        vm.onEvent(ConversationUiEvent.MicReleased)

        vm.onEvent(ConversationUiEvent.ChipToggled("Sốt"))
        vm.onEvent(ConversationUiEvent.SendChipsClicked)

        assertEquals(listOf("2"), chips.increasedIds)
        assertEquals(listOf(listOf("Sốt") to "1 viên • 35.000đ"), conversation.expandCalls)
    }

    @Test
    fun `sign failure shows deaf error and does not speak`() {
        conversation.signResult = AppResult.Failure(AppError(ErrorCode.NO_SIGN_DETECTED))
        val vm = createViewModel()

        vm.onEvent(ConversationUiEvent.SignPressed)
        vm.onEvent(ConversationUiEvent.SignReleased)

        assertEquals(ErrorCode.NO_SIGN_DETECTED, vm.uiState.value.deafError)
        assertFalse(vm.uiState.value.isRecognizing)
        assertTrue(speaker.spoken.isEmpty())

        vm.onEvent(ConversationUiEvent.DeafErrorDismissed)
        assertNull(vm.uiState.value.deafError)
    }

    @Test
    fun `staff mic vibrates on start and shows summary on release`() = runTest(mainRule.dispatcher) {
        conversation.summaryResult = AppResult.Success(summary)
        val vm = createViewModel()
        val effects = mutableListOf<ConversationUiEffect>()
        backgroundScope.launch { vm.effects.collect { effects += it } }

        vm.onEvent(ConversationUiEvent.MicPressed)
        assertTrue(vm.uiState.value.isStaffRecording)
        assertEquals(listOf(ConversationUiEffect.Vibrate(HapticPattern.STAFF_STARTED_SPEAKING)), effects)

        vm.onEvent(ConversationUiEvent.MicReleased)
        assertEquals(summary, vm.uiState.value.summary)
        assertFalse(vm.uiState.value.isSummarizing)
        val turn = conversation.savedTurns.single()
        assertEquals(TurnDirection.INCOMING, turn.direction)
        assertEquals("1 viên • 35.000đ", turn.processedText)
    }

    @Test
    fun `no speech error goes to staff side`() {
        conversation.summaryResult = AppResult.Failure(AppError(ErrorCode.NO_SPEECH_DETECTED))
        val vm = createViewModel()

        vm.onEvent(ConversationUiEvent.MicPressed)
        vm.onEvent(ConversationUiEvent.MicReleased)

        assertEquals(ErrorCode.NO_SPEECH_DETECTED, vm.uiState.value.staffError)
        assertNull(vm.uiState.value.deafError)
    }

    @Test
    fun `quick reply speaks directly without calling api`() {
        val vm = createViewModel()

        vm.onEvent(ConversationUiEvent.QuickReplyClicked("Lấy 1 hộp"))

        assertTrue(conversation.expandCalls.isEmpty())
        assertEquals(listOf("Lấy 1 hộp"), speaker.spoken)
        assertEquals("Lấy 1 hộp", vm.uiState.value.spokenSentence)
        assertEquals(TurnDirection.OUTGOING, conversation.savedTurns.single().direction)
    }

    @Test
    fun `sign press is ignored while staff is recording`() {
        val vm = createViewModel()
        vm.onEvent(ConversationUiEvent.MicPressed)

        vm.onEvent(ConversationUiEvent.SignPressed)

        assertFalse(vm.uiState.value.isSigning)
        assertEquals(0, conversation.startSignCount)
    }

    @Test
    fun `replay speaks last sentence again`() {
        val vm = createViewModel()
        vm.onEvent(ConversationUiEvent.QuickReplyClicked("Cảm ơn"))

        vm.onEvent(ConversationUiEvent.ReplayClicked)

        assertEquals(listOf("Cảm ơn", "Cảm ơn"), speaker.spoken)
        assertEquals(1, conversation.savedTurns.size)
    }

    @Test
    fun `staff can interrupt playback then release mic to receive summary`() = runTest(mainRule.dispatcher) {
        speaker.speakAction = { awaitCancellation() }
        conversation.summaryResult = AppResult.Success(summary)
        val vm = createViewModel()
        vm.onEvent(ConversationUiEvent.ChipToggled("Sốt"))
        vm.onEvent(ConversationUiEvent.SendChipsClicked)
        assertTrue(vm.uiState.value.isSpeaking)
        assertTrue(vm.uiState.value.canStartMic)

        vm.onEvent(ConversationUiEvent.MicPressed)
        assertTrue(vm.uiState.value.isStaffRecording)
        assertFalse(vm.uiState.value.isSpeaking)
        assertEquals(1, conversation.startVoiceCount)
        assertTrue(speaker.stopped)
        assertEquals(0, conversation.summarizeCalls)

        vm.onEvent(ConversationUiEvent.MicReleased)
        assertEquals(summary, vm.uiState.value.summary)
        assertEquals(1, conversation.summarizeCalls)
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test
    fun `mic does not restart or submit twice while transcription is pending`() = runTest(mainRule.dispatcher) {
        val pending = CompletableDeferred<SummaryResult>()
        conversation.summarizeAction = { pending.await() }
        val vm = createViewModel()
        vm.onEvent(ConversationUiEvent.MicPressed)
        vm.onEvent(ConversationUiEvent.MicPressed)
        assertEquals(1, conversation.startVoiceCount)
        vm.onEvent(ConversationUiEvent.MicReleased)
        assertTrue(vm.uiState.value.isSummarizing)
        assertFalse(vm.uiState.value.canStartMic)
        vm.onEvent(ConversationUiEvent.MicPressed)
        vm.onEvent(ConversationUiEvent.MicReleased)
        assertEquals(1, conversation.summarizeCalls)
        assertEquals(1, conversation.startVoiceCount)
        pending.complete(summary)
        assertEquals(summary, vm.uiState.value.summary)
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test
    fun `granting mic permission prompts another hold without recording unexpectedly`() {
        val vm = createViewModel()
        vm.onEvent(ConversationUiEvent.MicPermissionGranted)
        assertTrue(vm.uiState.value.showMicReadyHint)
        assertEquals(0, conversation.startVoiceCount)
        vm.onEvent(ConversationUiEvent.MicPressed)
        assertFalse(vm.uiState.value.showMicReadyHint)
        assertTrue(vm.uiState.value.isStaffRecording)
        vm.onEvent(ConversationUiEvent.CaptureCancelled)
        assertFalse(vm.uiState.value.isBusy)
        assertEquals(0, conversation.summarizeCalls)
    }

    @Test
    fun `recorder failure shows its specific recovery message and allows retry`() {
        val message = "Không thu được âm thanh. Hãy kiểm tra quyền micro."
        speaker.recordingError = BridgeException(BridgeError.DEVICE, message)
        val vm = createViewModel()
        vm.onEvent(ConversationUiEvent.MicPressed)
        assertEquals(ErrorCode.DEVICE_ERROR, vm.uiState.value.staffError)
        assertEquals(message, vm.uiState.value.staffErrorMessage)
        assertFalse(vm.uiState.value.isBusy)
        speaker.recordingError = null
        vm.onEvent(ConversationUiEvent.MicPressed)
        assertNull(vm.uiState.value.staffError)
        assertNull(vm.uiState.value.staffErrorMessage)
        vm.onEvent(ConversationUiEvent.CaptureCancelled)
    }
}
