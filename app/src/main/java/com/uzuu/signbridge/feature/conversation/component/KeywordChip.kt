package com.uzuu.signbridge.feature.conversation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.uzuu.signbridge.ui.theme.Dimens

/**
 * Thẻ từ khóa chữ lớn. [order] là thứ tự chọn (1..3) hiện ở góc trái khi đã chọn.
 */
@Composable
fun KeywordChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    order: Int? = null,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = Dimens.TouchTarget)
            .semantics { this.selected = selected },
        shape = RoundedCornerShape(Dimens.CornerRadius),
        color = if (selected) colors.primaryContainer else colors.surface,
        contentColor = when {
            !enabled -> colors.onSurface.copy(alpha = 0.38f)
            selected -> colors.onPrimaryContainer
            else -> colors.onSurface
        },
        border = BorderStroke(Dimens.BorderWidth, if (selected) colors.primary else colors.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected && order != null) {
                Text(text = order.toString(), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(Dimens.SpaceS))
            }
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
