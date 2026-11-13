package com.uzuu.signbridge.ui.component.button

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.uzuu.signbridge.ui.theme.Dimens
import com.uzuu.signbridge.ui.theme.RecordingRed
import com.uzuu.signbridge.ui.theme.White

/**
 * Nút nhấn giữ (push-to-talk / push-to-sign).
 * Gọi [onPressStart] khi chạm xuống, [onPressEnd] khi thả hoặc huỷ chạm.
 */
@Composable
fun HoldButton(
    label: String,
    activeLabel: String,
    icon: ImageVector,
    isActive: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    idleContainerColor: Color = MaterialTheme.colorScheme.primary,
    idleContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    onPressCancel: () -> Unit = onPressEnd,
) {
    val currentOnStart by rememberUpdatedState(onPressStart)
    val currentOnEnd by rememberUpdatedState(onPressEnd)
    val currentOnCancel by rememberUpdatedState(onPressCancel)

    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        isActive -> RecordingRed
        else -> idleContainerColor
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.outline
        isActive -> White
        else -> idleContentColor
    }

    val gestureModifier = if (enabled) {
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    currentOnStart()
                    if (tryAwaitRelease()) currentOnEnd() else currentOnCancel()
                },
            )
        }
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.HoldButtonHeight)
            .semantics {
                role = Role.Button
                if (enabled) onClick { if (isActive) currentOnEnd() else currentOnStart(); true }
            }
            .then(gestureModifier),
        shape = RoundedCornerShape(Dimens.CornerRadius),
        color = containerColor,
        contentColor = contentColor,
        border = if (isActive) BorderStroke(Dimens.BorderWidth, White) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceM),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Dimens.ButtonIconSize),
            )
            Spacer(Modifier.width(Dimens.SpaceM))
            Text(
                text = if (isActive) activeLabel else label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}
