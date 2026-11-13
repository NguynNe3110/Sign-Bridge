package com.uzuu.signbridge.feature.conversation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.uzuu.signbridge.R
import com.uzuu.signbridge.domain.model.SignResult
import com.uzuu.signbridge.feature.conversation.ConversationUiState
import com.uzuu.signbridge.ui.component.button.HoldButton
import com.uzuu.signbridge.ui.component.text.labelRes
import com.uzuu.signbridge.ui.icon.AppIcons
import com.uzuu.signbridge.ui.icon.toImageVector
import com.uzuu.signbridge.ui.theme.AccentCyan
import com.uzuu.signbridge.ui.theme.DeafZoneBackground
import com.uzuu.signbridge.ui.theme.Dimens

/** Nửa dưới dành cho người khiếm thính: tóm tắt, xác nhận ký hiệu, thẻ từ khóa, nút Ký hiệu. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeafPanel(
    state: ConversationUiState,
    onBack: () -> Unit,
    onManageChips: () -> Unit,
    onSignPressed: () -> Unit,
    onSignReleased: () -> Unit,
    onChipToggled: (String) -> Unit,
    onClearChips: () -> Unit,
    onSendChips: () -> Unit,
    onQuickReply: (String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    cameraPreview: @Composable () -> Unit = {},
    onCaptureCancelled: () -> Unit = onSignReleased,
) {
    Column(
        modifier = modifier
            .background(DeafZoneBackground)
            .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.SpaceS),
    ) {
        Header(state = state, onBack = onBack, onManageChips = onManageChips)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
        ) {
            state.deafError?.let { ErrorBanner(error = it, onDismiss = onDismissError) }
            cameraPreview()

            when {
                state.isStaffRecording || state.isSummarizing -> StatusRow(
                    text = stringResource(R.string.deaf_staff_speaking),
                    showProgress = state.isSummarizing,
                )
                state.summary != null -> SummaryCard(
                    summary = state.summary,
                    onQuickReply = onQuickReply,
                    quickRepliesEnabled = !state.isBusy,
                )
            }

            when {
                state.isRecognizing -> StatusRow(stringResource(R.string.deaf_recognizing), showProgress = true)
                state.isExpanding -> StatusRow(stringResource(R.string.deaf_expanding), showProgress = true)
                state.pendingSign != null -> PendingSignSection(
                    pending = state.pendingSign,
                    selected = state.selectedChips,
                    canSelectMore = state.canSelectMoreChips,
                    onChipToggled = onChipToggled,
                )
                state.recognizedGlosses.isNotEmpty() -> Text(
                    text = stringResource(R.string.deaf_understood, state.recognizedGlosses.joinToString(" · ")),
                    style = MaterialTheme.typography.titleMedium,
                    color = AccentCyan,
                )
            }

            ChipBoard(
                state = state,
                onChipToggled = onChipToggled,
            )
        }

        if (state.selectedChips.isNotEmpty()) {
            SelectionBar(
                count = state.selectedChips.size,
                enabled = !state.isBusy,
                onClear = onClearChips,
                onSend = onSendChips,
            )
            Spacer(Modifier.size(Dimens.SpaceS))
        }

        HoldButton(
            label = stringResource(R.string.deaf_sign_idle),
            activeLabel = stringResource(R.string.deaf_sign_active),
            icon = AppIcons.Sign,
            isActive = state.isSigning,
            enabled = !state.isBusy || state.isSigning,
            onPressStart = onSignPressed,
            onPressEnd = onSignReleased,
            onPressCancel = onCaptureCancelled,
        )
    }
}

@Composable
private fun Header(
    state: ConversationUiState,
    onBack: () -> Unit,
    onManageChips: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(Dimens.TouchTarget)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        Icon(
            imageVector = state.context.toImageVector(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(Dimens.SpaceS))
        Text(
            text = stringResource(state.context.labelRes()),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onManageChips, modifier = Modifier.size(Dimens.TouchTarget)) {
            Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.action_manage_chips))
        }
    }
}

@Composable
private fun StatusRow(text: String, showProgress: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SpaceS)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showProgress) {
            CircularProgressIndicator(modifier = Modifier.size(Dimens.ButtonIconSize), color = AccentCyan)
        } else {
            Icon(Icons.Filled.Hearing, contentDescription = null, tint = AccentCyan)
        }
        Spacer(Modifier.width(Dimens.SpaceM))
        Text(text = text, style = MaterialTheme.typography.titleLarge, color = AccentCyan)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingSignSection(
    pending: SignResult,
    selected: List<String>,
    canSelectMore: Boolean,
    onChipToggled: (String) -> Unit,
) {
    val unknown = stringResource(R.string.deaf_unknown_gloss)
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        Text(
            text = stringResource(
                R.string.deaf_unsure_glosses,
                pending.glosses.joinToString(" · ") { if (it == SignResult.UNKNOWN_GLOSS) unknown else it },
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = stringResource(R.string.deaf_unsure_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        ) {
            pending.suggestedChips.forEach { label ->
                val index = selected.indexOf(label)
                KeywordChip(
                    label = label,
                    selected = index >= 0,
                    order = if (index >= 0) index + 1 else null,
                    enabled = index >= 0 || canSelectMore,
                    onClick = { onChipToggled(label) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipBoard(
    state: ConversationUiState,
    onChipToggled: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        Text(
            text = stringResource(R.string.deaf_chips_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        if (state.chips.isEmpty()) {
            Text(stringResource(R.string.deaf_no_chips), style = MaterialTheme.typography.bodyMedium)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        ) {
            state.chips.forEach { chip ->
                val index = state.selectedChips.indexOf(chip.label)
                KeywordChip(
                    label = chip.label,
                    selected = index >= 0,
                    order = if (index >= 0) index + 1 else null,
                    enabled = index >= 0 || state.canSelectMoreChips,
                    onClick = { onChipToggled(chip.label) },
                )
            }
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    enabled: Boolean,
    onClear: () -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        Text(
            text = stringResource(R.string.deaf_selected, count, ConversationUiState.MAX_SELECTED_CHIPS),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(
            onClick = onClear,
            enabled = enabled,
            modifier = Modifier.heightIn(min = Dimens.TouchTarget),
            shape = RoundedCornerShape(Dimens.CornerRadius),
        ) {
            Text(stringResource(R.string.deaf_clear_chips), style = MaterialTheme.typography.labelMedium)
        }
        Button(
            onClick = onSend,
            enabled = enabled,
            modifier = Modifier.heightIn(min = Dimens.TouchTarget),
            shape = RoundedCornerShape(Dimens.CornerRadius),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
            Spacer(Modifier.width(Dimens.SpaceS))
            Text(stringResource(R.string.deaf_send_chips), style = MaterialTheme.typography.labelLarge)
        }
    }
}
