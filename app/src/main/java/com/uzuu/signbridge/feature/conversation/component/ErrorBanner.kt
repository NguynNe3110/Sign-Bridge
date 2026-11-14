package com.uzuu.signbridge.feature.conversation.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.uzuu.signbridge.R
import com.uzuu.signbridge.core.result.ErrorCode
import com.uzuu.signbridge.ui.theme.Dimens

@StringRes
fun ErrorCode.messageRes(): Int = when (this) {
    ErrorCode.NO_SIGN_DETECTED -> R.string.error_no_sign
    ErrorCode.NO_SPEECH_DETECTED -> R.string.error_no_speech
    ErrorCode.NETWORK_UNAVAILABLE -> R.string.error_offline
    ErrorCode.AI_TIMEOUT, ErrorCode.AI_UPSTREAM_ERROR -> R.string.error_ai
    ErrorCode.PAYLOAD_TOO_LARGE -> R.string.error_payload
    ErrorCode.NOT_CONFIGURED -> R.string.error_not_configured
    ErrorCode.DEVICE_ERROR -> R.string.error_device
    ErrorCode.STORAGE_ERROR -> R.string.error_storage
    ErrorCode.CAMERA_PERMISSION_REQUIRED -> R.string.error_camera_permission
    ErrorCode.MIC_PERMISSION_REQUIRED -> R.string.error_mic_permission
    ErrorCode.INVALID_REQUEST,
    ErrorCode.UNSUPPORTED_MEDIA_TYPE,
    ErrorCode.UNKNOWN,
    -> R.string.error_generic
}

@Composable
fun ErrorBanner(
    error: ErrorCode,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.CornerRadius),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = Dimens.SpaceL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Spacer(Modifier.width(Dimens.SpaceS))
            Text(
                text = stringResource(error.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = Dimens.SpaceM),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_dismiss))
            }
        }
    }
}
