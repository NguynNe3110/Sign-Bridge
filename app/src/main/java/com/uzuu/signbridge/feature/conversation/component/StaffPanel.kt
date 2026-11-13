package com.uzuu.signbridge.feature.conversation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.uzuu.signbridge.R
import com.uzuu.signbridge.core.result.ErrorCode
import com.uzuu.signbridge.ui.component.button.HoldButton
import com.uzuu.signbridge.ui.theme.AccentCyan
import com.uzuu.signbridge.ui.theme.Dimens
import com.uzuu.signbridge.ui.theme.SpokenSentenceStyle
import com.uzuu.signbridge.ui.theme.StaffZoneBackground

/**
 * Nửa trên dành cho nhân viên. Màn hình cha xoay 180° để nhân viên ngồi đối diện đọc được.
 */
@Composable
fun StaffPanel(
    spokenSentence: String,
    isCustomerSigning: Boolean,
    isSpeaking: Boolean,
    isRecording: Boolean,
    isSummarizing: Boolean,
    micEnabled: Boolean,
    canReplay: Boolean,
    error: ErrorCode?,
    onMicPressed: () -> Unit,
    onMicReleased: () -> Unit,
    onReplay: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    isOffline: Boolean = false,
    onCaptureCancelled: () -> Unit = onMicReleased,
) {
    Column(
        modifier = modifier
            .background(StaffZoneBackground)
            .padding(Dimens.ScreenPadding),
    ) {
        if (isOffline) Text(stringResource(R.string.staff_offline), color = AccentCyan)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.staff_label_customer_says),
                    style = MaterialTheme.typography.titleMedium,
                    color = AccentCyan,
                    modifier = Modifier.weight(1f),
                )
                if (isSpeaking) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = AccentCyan)
                    Spacer(Modifier.width(Dimens.SpaceXs))
                    Text(
                        text = stringResource(R.string.staff_speaking),
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentCyan,
                    )
                } else if (canReplay) {
                    TextButton(onClick = onReplay) {
                        Icon(Icons.Filled.Replay, contentDescription = null)
                        Spacer(Modifier.width(Dimens.SpaceXs))
                        Text(stringResource(R.string.staff_replay), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.size(Dimens.SpaceS))

            val sentenceText = when {
                isCustomerSigning -> stringResource(R.string.staff_customer_signing)
                spokenSentence.isNotBlank() -> spokenSentence
                else -> stringResource(R.string.staff_waiting)
            }
            Text(
                text = sentenceText,
                style = SpokenSentenceStyle,
                color = if (spokenSentence.isNotBlank() && !isCustomerSigning) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            if (error != null) {
                Spacer(Modifier.size(Dimens.SpaceM))
                ErrorBanner(error = error, onDismiss = onDismissError)
            }
        }

        Spacer(Modifier.size(Dimens.SpaceM))
        if (isSummarizing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.SpaceL),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(Dimens.ButtonIconSize))
                    Spacer(Modifier.width(Dimens.SpaceM))
                    Text(stringResource(R.string.staff_summarizing), style = MaterialTheme.typography.titleLarge)
                }
            }
        } else {
            HoldButton(
                label = stringResource(R.string.staff_mic_idle),
                activeLabel = stringResource(R.string.staff_mic_active),
                icon = Icons.Filled.Mic,
                isActive = isRecording,
                enabled = micEnabled || isRecording,
                onPressStart = onMicPressed,
                onPressEnd = onMicReleased,
                onPressCancel = onCaptureCancelled,
                idleContainerColor = AccentCyan,
            )
        }
    }
}
