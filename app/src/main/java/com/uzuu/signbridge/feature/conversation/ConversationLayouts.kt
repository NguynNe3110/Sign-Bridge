package com.uzuu.signbridge.feature.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.ViewQuilt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.uzuu.signbridge.R
import com.uzuu.signbridge.feature.conversation.component.*
import com.uzuu.signbridge.ui.component.button.HoldButton
import com.uzuu.signbridge.ui.component.text.labelRes
import com.uzuu.signbridge.ui.icon.AppIcons
import com.uzuu.signbridge.ui.theme.Dimens
import com.uzuu.signbridge.ui.theme.SpokenSentenceStyle
import kotlinx.coroutines.launch

internal data class ConversationActions(
    val onBack: () -> Unit,
    val onManageChips: () -> Unit,
    val onMicPressed: () -> Unit,
    val onMicReleased: () -> Unit,
    val onSignPressed: () -> Unit,
    val onSignReleased: () -> Unit,
    val onReplay: () -> Unit,
    val onChipToggled: (String) -> Unit,
    val onClearChips: () -> Unit,
    val onSendChips: () -> Unit,
    val onQuickReply: (String) -> Unit,
    val onDismissStaffError: () -> Unit,
    val onDismissDeafError: () -> Unit,
    val onCaptureCancelled: () -> Unit,
)

private val ConversationUiState.canChangeLayout: Boolean
    get() = !isSigning && !isStaffRecording

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationLayouts(
    state: ConversationUiState,
    actions: ConversationActions,
    cameraPreview: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    initialLayout: ConversationLayout = ConversationLayout.SHARED,
) {
    var layout by rememberSaveable { mutableStateOf(initialLayout) }
    var showLayouts by rememberSaveable { mutableStateOf(false) }
    val header: @Composable () -> Unit = {
        ConversationHeader(state, actions, layout, onLayouts = { showLayouts = true })
    }
    Box(modifier.fillMaxSize()) {
        when (layout) {
            ConversationLayout.SHARED -> SharedConversation(state, actions, cameraPreview, header)
            ConversationLayout.FOCUSED -> FocusedConversation(state, actions, cameraPreview, header)
            ConversationLayout.TABLE -> TableConversation(state, actions, cameraPreview, header)
        }
    }
    if (showLayouts) {
        ModalBottomSheet(
            onDismissRequest = { showLayouts = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())
                    .padding(bottom = Dimens.SpaceXl).selectableGroup(),
            ) {
                Text(
                    stringResource(R.string.layout_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = Dimens.SpaceXl, vertical = Dimens.SpaceS),
                )
                ConversationLayout.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = layout == option,
                            enabled = state.canChangeLayout,
                            role = Role.RadioButton,
                            onClick = { layout = option; showLayouts = false },
                        ).padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceM),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
                    ) {
                        RadioButton(selected = layout == option, onClick = null)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                            Text(stringResource(option.labelRes), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(option.descriptionRes), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationHeader(
    state: ConversationUiState,
    actions: ConversationActions,
    layout: ConversationLayout,
    onLayouts: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(actions.onBack, Modifier.size(Dimens.TouchTarget)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(state.context.labelRes()), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(layout.labelRes), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onLayouts, Modifier.size(Dimens.TouchTarget), enabled = state.canChangeLayout) {
            Icon(Icons.AutoMirrored.Filled.ViewQuilt, stringResource(R.string.layout_change, stringResource(layout.labelRes)))
        }
        IconButton(actions.onManageChips, Modifier.size(Dimens.TouchTarget)) {
            Icon(Icons.Filled.Tune, stringResource(R.string.action_manage_chips))
        }
    }
}

@Composable
private fun SharedConversation(
    state: ConversationUiState,
    actions: ConversationActions,
    cameraPreview: @Composable () -> Unit,
    header: @Composable () -> Unit,
) {
    val chipsRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Dimens.ContentMaxWidth).fillMaxSize()) {
            header()
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
            ) {
                SharedOutgoingMessage(state, actions)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                CustomerMessages(state, actions.onQuickReply, actions.onChipToggled,
                    actions.onDismissDeafError, cameraPreview)
                Box(Modifier.bringIntoViewRequester(chipsRequester)) {
                    ChipBoard(state, actions.onChipToggled)
                }
            }
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                    .padding(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
            ) {
                if (state.selectedChips.isNotEmpty()) {
                    SelectionBar(state.selectedChips.size, !state.isBusy, actions.onClearChips, actions.onSendChips)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.shared_hold_hint), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { chipsRequester.bringIntoView() } }) {
                        Text(stringResource(R.string.shared_choose_chips))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
                    HoldButton(
                        label = stringResource(R.string.shared_sign_idle),
                        activeLabel = stringResource(R.string.shared_sign_active),
                        icon = AppIcons.Sign, isActive = state.isSigning,
                        enabled = !state.isBusy || state.isSigning,
                        onPressStart = actions.onSignPressed, onPressEnd = actions.onSignReleased,
                        onPressCancel = actions.onCaptureCancelled,
                        modifier = Modifier.weight(1f).heightIn(min = Dimens.SharedHoldButtonHeight),
                        idleContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        idleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    HoldButton(
                        label = stringResource(R.string.shared_mic_idle),
                        activeLabel = stringResource(R.string.shared_mic_active),
                        icon = Icons.Filled.Mic, isActive = state.isStaffRecording,
                        enabled = state.canStartMic || state.isStaffRecording,
                        onPressStart = actions.onMicPressed, onPressEnd = actions.onMicReleased,
                        onPressCancel = actions.onCaptureCancelled,
                        modifier = Modifier.weight(1f).heightIn(min = Dimens.SharedHoldButtonHeight),
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedOutgoingMessage(state: ConversationUiState, actions: ConversationActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.staff_label_customer_says),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
            if (state.spokenSentence.isNotBlank()) {
                TextButton(actions.onReplay, enabled = !state.isBusy) {
                    Icon(Icons.Filled.Replay, null)
                    Spacer(Modifier.width(Dimens.SpaceXs))
                    Text(stringResource(R.string.staff_replay))
                }
            }
        }
        val text = when {
            state.isSigning || state.isRecognizing -> stringResource(R.string.staff_customer_signing)
            state.spokenSentence.isNotBlank() -> state.spokenSentence
            else -> stringResource(R.string.shared_waiting)
        }
        Text(text, style = if (state.spokenSentence.isNotBlank()) SpokenSentenceStyle else MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (state.isOffline) Text(stringResource(R.string.staff_offline), style = MaterialTheme.typography.labelMedium)
        if (state.isSpeaking) Text(stringResource(R.string.staff_speaking), style = MaterialTheme.typography.labelMedium)
        state.staffError?.let { ErrorBanner(it, actions.onDismissStaffError, message = state.staffErrorMessage) }
        if (state.showMicReadyHint) Text(stringResource(R.string.staff_mic_ready), style = MaterialTheme.typography.bodyMedium)
    }
}

internal class FocusedConversationTabState(
    initialStaffTab: Boolean = false,
) {
    var staffTab by mutableStateOf(initialStaffTab)
        internal set

    private var wasSummarizing = false
    private var wasRecognizingSign = false
    private var waitingForCustomerSend = false

    fun selectTab(isStaff: Boolean) {
        staffTab = isStaff
    }

    fun onCustomerSendInitiated() {
        waitingForCustomerSend = true
    }

    fun update(state: ConversationUiState) {
        // 1. Staff finished speaking & summarizing -> auto switch to Customer tab
        if (state.isSummarizing) {
            wasSummarizing = true
        } else if (wasSummarizing) {
            wasSummarizing = false
            if (state.staffError == null && state.summary != null) {
                staffTab = false
            }
        }

        // 2. Customer finished signing -> auto switch to Staff tab
        if (state.isRecognizing) {
            wasRecognizingSign = true
        } else if (wasRecognizingSign) {
            wasRecognizingSign = false
            if (state.deafError == null && state.pendingSign == null && state.spokenSentence.isNotBlank()) {
                staffTab = true
            }
        }

        // 3. Customer finished selecting & sending chips -> auto switch to Staff tab
        if (waitingForCustomerSend) {
            if (state.deafError != null) {
                waitingForCustomerSend = false
            } else if (!state.isExpanding && state.selectedChips.isEmpty() && state.spokenSentence.isNotBlank()) {
                waitingForCustomerSend = false
                staffTab = true
            }
        }
    }

    companion object {
        val Saver: Saver<FocusedConversationTabState, Boolean> = Saver(
            save = { it.staffTab },
            restore = { FocusedConversationTabState(initialStaffTab = it) },
        )
    }
}

@Composable
internal fun rememberFocusedConversationTabState(
    state: ConversationUiState,
    initialStaffTab: Boolean = false,
): FocusedConversationTabState {
    val tabState = rememberSaveable(saver = FocusedConversationTabState.Saver) {
        FocusedConversationTabState(initialStaffTab = initialStaffTab)
    }

    LaunchedEffect(state) {
        tabState.update(state)
    }

    return tabState
}

@Composable
private fun FocusedConversation(
    state: ConversationUiState,
    actions: ConversationActions,
    cameraPreview: @Composable () -> Unit,
    header: @Composable () -> Unit,
) {
    val tabState = rememberFocusedConversationTabState(state)
    val focusedActions = remember(actions, tabState) {
        actions.copy(
            onSendChips = {
                tabState.onCustomerSendInitiated()
                actions.onSendChips()
            }
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Dimens.ContentMaxWidth).fillMaxSize()) {
            header()
            Row(Modifier.fillMaxWidth().selectableGroup()) {
                Tab(selected = !tabState.staffTab, onClick = { tabState.selectTab(false) }, enabled = state.canChangeLayout,
                    modifier = Modifier.weight(1f).background(if (!tabState.staffTab) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                    text = { Text(stringResource(R.string.layout_customer)) })
                Tab(selected = tabState.staffTab, onClick = { tabState.selectTab(true) }, enabled = state.canChangeLayout,
                    modifier = Modifier.weight(1f).background(if (tabState.staffTab) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                    text = { Text(stringResource(R.string.layout_staff)) })
            }
            if (tabState.staffTab) StaffConversationPanel(state, actions, Modifier.weight(1f).fillMaxWidth())
            else CustomerConversationPanel(state, focusedActions, cameraPreview, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun TableConversation(
    state: ConversationUiState,
    actions: ConversationActions,
    cameraPreview: @Composable () -> Unit,
    header: @Composable () -> Unit,
) {
    var expandedStaff by rememberSaveable { mutableStateOf(state.spokenSentence.isNotBlank()) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize()) {
                StaffConversationPanel(state, actions, Modifier.weight(1f).fillMaxHeight().rotate(180f))
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                CustomerConversationPanel(state, actions, cameraPreview, Modifier.weight(1f).fillMaxHeight(), header)
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                // User-controlled sizes remain stable during hold gestures; no state-driven resizing.
                StaffConversationPanel(state, actions,
                    Modifier.weight(if (expandedStaff) 0.5f else 0.32f).fillMaxWidth().rotate(180f))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(
                    onClick = { expandedStaff = !expandedStaff }, enabled = state.canChangeLayout,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTarget),
                ) {
                    Text(stringResource(if (expandedStaff) R.string.layout_shrink_staff else R.string.layout_expand_staff),
                        style = MaterialTheme.typography.labelMedium)
                }
                CustomerConversationPanel(state, actions, cameraPreview,
                    Modifier.weight(if (expandedStaff) 0.5f else 0.68f).fillMaxWidth(), header)
            }
        }
    }
}

@Composable
private fun StaffConversationPanel(state: ConversationUiState, actions: ConversationActions, modifier: Modifier) {
    StaffPanel(
        spokenSentence = state.spokenSentence,
        isCustomerSigning = state.isSigning || state.isRecognizing,
        isSpeaking = state.isSpeaking, isRecording = state.isStaffRecording, isSummarizing = state.isSummarizing,
        micEnabled = state.canStartMic, canReplay = state.spokenSentence.isNotBlank() && !state.isBusy,
        error = state.staffError, errorMessage = state.staffErrorMessage, showMicReadyHint = state.showMicReadyHint,
        onMicPressed = actions.onMicPressed, onMicReleased = actions.onMicReleased, onReplay = actions.onReplay,
        onDismissError = actions.onDismissStaffError, onCaptureCancelled = actions.onCaptureCancelled,
        isOffline = state.isOffline, modifier = modifier,
    )
}

@Composable
private fun CustomerConversationPanel(
    state: ConversationUiState,
    actions: ConversationActions,
    cameraPreview: @Composable () -> Unit,
    modifier: Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    DeafPanel(
        state = state, onBack = actions.onBack, onManageChips = actions.onManageChips,
        onSignPressed = actions.onSignPressed, onSignReleased = actions.onSignReleased,
        onChipToggled = actions.onChipToggled, onClearChips = actions.onClearChips,
        onSendChips = actions.onSendChips, onQuickReply = actions.onQuickReply,
        onDismissError = actions.onDismissDeafError, cameraPreview = cameraPreview,
        onCaptureCancelled = actions.onCaptureCancelled, modifier = modifier,
        showHeader = header != null, header = header,
    )
}
