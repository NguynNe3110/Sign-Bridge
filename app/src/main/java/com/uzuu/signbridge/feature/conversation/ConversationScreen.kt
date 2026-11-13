package com.uzuu.signbridge.feature.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.uzuu.signbridge.core.ui.Haptics
import com.uzuu.signbridge.core.ui.CameraPreviewController
import com.uzuu.signbridge.domain.model.ContextType
import com.uzuu.signbridge.domain.model.Highlight
import com.uzuu.signbridge.domain.model.HighlightType
import com.uzuu.signbridge.domain.model.IconCode
import com.uzuu.signbridge.domain.model.QuickChip
import com.uzuu.signbridge.domain.model.SummaryResult
import com.uzuu.signbridge.feature.conversation.component.DeafPanel
import com.uzuu.signbridge.feature.conversation.component.StaffPanel
import com.uzuu.signbridge.ui.theme.AppTheme
import com.uzuu.signbridge.ui.theme.Dimens

@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    onBack: () -> Unit,
    onManageChips: (ContextType) -> Unit,
    cameraPreviewController: CameraPreviewController,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = remember(context) { Haptics(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val devices = rememberConversationDevices(viewModel, cameraPreviewController)

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is ConversationUiEffect.Vibrate -> haptics.vibrate(effect.pattern)
                }
            }
        }
    }

    ConversationContent(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onManageChips = { onManageChips(state.context) },
        onSignPressed = devices.onSignPressed,
        onMicPressed = devices.onMicPressed,
        cameraPreview = devices.preview,
    )
}

/**
 * Màn chia đôi: nửa trên xoay 180° cho nhân viên ngồi đối diện,
 * nửa dưới cho người khiếm thính. Hai người đọc được mà không cần xoay máy.
 */
@Composable
fun ConversationContent(
    state: ConversationUiState,
    onEvent: (ConversationUiEvent) -> Unit,
    onBack: () -> Unit,
    onManageChips: () -> Unit,
    modifier: Modifier = Modifier,
    onSignPressed: () -> Unit = { onEvent(ConversationUiEvent.SignPressed) },
    onMicPressed: () -> Unit = { onEvent(ConversationUiEvent.MicPressed) },
    cameraPreview: @Composable () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize()) {
        StaffPanel(
            spokenSentence = state.spokenSentence,
            isCustomerSigning = state.isSigning || state.isRecognizing,
            isSpeaking = state.isSpeaking,
            isRecording = state.isStaffRecording,
            isSummarizing = state.isSummarizing,
            micEnabled = !state.isBusy,
            canReplay = state.spokenSentence.isNotBlank() && !state.isBusy,
            error = state.staffError,
            onMicPressed = onMicPressed,
            onMicReleased = { onEvent(ConversationUiEvent.MicReleased) },
            onReplay = { onEvent(ConversationUiEvent.ReplayClicked) },
            onDismissError = { onEvent(ConversationUiEvent.StaffErrorDismissed) },
            isOffline = state.isOffline,
            onCaptureCancelled = { onEvent(ConversationUiEvent.CaptureCancelled) },
            modifier = Modifier
                .weight(0.42f)
                .fillMaxWidth()
                .rotate(180f),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.ZoneDividerHeight)
                .background(MaterialTheme.colorScheme.primary),
        )
        DeafPanel(
            state = state,
            onBack = onBack,
            onManageChips = onManageChips,
            onSignPressed = onSignPressed,
            onSignReleased = { onEvent(ConversationUiEvent.SignReleased) },
            onChipToggled = { onEvent(ConversationUiEvent.ChipToggled(it)) },
            onClearChips = { onEvent(ConversationUiEvent.ClearChipsClicked) },
            onSendChips = { onEvent(ConversationUiEvent.SendChipsClicked) },
            onQuickReply = { onEvent(ConversationUiEvent.QuickReplyClicked(it)) },
            onDismissError = { onEvent(ConversationUiEvent.DeafErrorDismissed) },
            cameraPreview = cameraPreview,
            onCaptureCancelled = { onEvent(ConversationUiEvent.CaptureCancelled) },
            modifier = Modifier
                .weight(0.58f)
                .fillMaxWidth(),
        )
    }
}

@Preview(heightDp = 800)
@Composable
private fun ConversationContentPreview() {
    AppTheme {
        ConversationContent(
            state = ConversationUiState(
                context = ContextType.PHARMACY,
                spokenSentence = "Chào bạn, mình bị đau đầu, muốn mua thuốc.",
                chips = listOf(
                    QuickChip("1", ContextType.PHARMACY, "Đau đầu", isPersonal = false),
                    QuickChip("2", ContextType.PHARMACY, "Sốt", isPersonal = false),
                    QuickChip("3", ContextType.PHARMACY, "Dị ứng penicillin", isPersonal = true),
                ),
                selectedChips = listOf("Đau đầu"),
                summary = SummaryResult(
                    transcript = "",
                    summary = "Panadol Xanh • 1 viên sau ăn • Ngày 2 lần • 35.000đ",
                    highlights = listOf(
                        Highlight(HighlightType.DOSAGE, "1 viên"),
                        Highlight(HighlightType.PRICE, "35.000đ"),
                    ),
                    iconCode = IconCode.PILL,
                    quickReplies = listOf("Lấy 1 hộp", "Chuyển khoản QR", "Có loại rẻ hơn không?"),
                ),
            ),
            onEvent = {},
            onBack = {},
            onManageChips = {},
        )
    }
}
