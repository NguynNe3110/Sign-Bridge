package com.uzuu.signbridge.feature.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
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
import com.uzuu.signbridge.ui.theme.AppTheme

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

/** Layout selection is local UI state. Every view dispatches the same conversation events. */
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
    initialLayout: ConversationLayout = ConversationLayout.SHARED,
) {
    ConversationLayouts(
        state = state,
        actions = ConversationActions(
            onBack = onBack,
            onManageChips = onManageChips,
            onMicPressed = onMicPressed,
            onMicReleased = { onEvent(ConversationUiEvent.MicReleased) },
            onSignPressed = onSignPressed,
            onSignReleased = { onEvent(ConversationUiEvent.SignReleased) },
            onReplay = { onEvent(ConversationUiEvent.ReplayClicked) },
            onChipToggled = { onEvent(ConversationUiEvent.ChipToggled(it)) },
            onClearChips = { onEvent(ConversationUiEvent.ClearChipsClicked) },
            onSendChips = { onEvent(ConversationUiEvent.SendChipsClicked) },
            onQuickReply = { onEvent(ConversationUiEvent.QuickReplyClicked(it)) },
            onDismissStaffError = { onEvent(ConversationUiEvent.StaffErrorDismissed) },
            onDismissDeafError = { onEvent(ConversationUiEvent.DeafErrorDismissed) },
            onCaptureCancelled = { onEvent(ConversationUiEvent.CaptureCancelled) },
        ),
        cameraPreview = cameraPreview,
        modifier = modifier,
        initialLayout = initialLayout,
    )
}

@Preview(name = "Shared", group = "Conversation layouts", heightDp = 800)
@Composable
private fun ConversationContentPreview() {
    ConversationLayoutPreview(ConversationLayout.SHARED)
}

@Preview(name = "Focused", group = "Conversation layouts", heightDp = 800)
@Composable
private fun FocusedConversationPreview() {
    ConversationLayoutPreview(ConversationLayout.FOCUSED)
}

@Preview(name = "Across the table", group = "Conversation layouts", heightDp = 800)
@Composable
private fun TableConversationPreview() {
    ConversationLayoutPreview(ConversationLayout.TABLE)
}

@Composable
private fun ConversationLayoutPreview(layout: ConversationLayout) {
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
            initialLayout = layout,
        )
    }
}
