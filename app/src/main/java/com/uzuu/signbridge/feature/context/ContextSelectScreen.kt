package com.uzuu.signbridge.feature.context

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.uzuu.signbridge.R
import com.uzuu.signbridge.domain.model.ContextType
import com.uzuu.signbridge.ui.component.text.labelRes
import com.uzuu.signbridge.ui.icon.toImageVector
import com.uzuu.signbridge.ui.theme.AppTheme
import com.uzuu.signbridge.ui.theme.Dimens

/** Màn tĩnh chọn ngữ cảnh: Quán ăn, Hiệu thuốc, Hành chính, Tự do. */
@Composable
fun ContextSelectScreen(
    onContextSelected: (ContextType) -> Unit,
    modifier: Modifier = Modifier,
    onServerSettings: () -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = Dimens.ContentMaxWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Dimens.SpaceXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        ) {
            Spacer(Modifier.size(Dimens.SpaceXl))
            Text(
                text = stringResource(R.string.context_select_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.context_select_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(Dimens.SpaceL))
            Surface(
                shape = RoundedCornerShape(Dimens.CornerRadius),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column {
                    ContextType.entries.forEachIndexed { index, context ->
                        ContextRow(context = context, onClick = { onContextSelected(context) })
                        if (index < ContextType.entries.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = Dimens.SpaceL),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.size(Dimens.SpaceM))
            TextButton(
                onClick = onServerSettings,
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTarget),
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(Dimens.ButtonIconSize))
                Spacer(Modifier.width(Dimens.SpaceS))
                Text(stringResource(R.string.server_title))
            }
        }
    }
}

@Composable
private fun ContextRow(
    context: ContextType,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.ContextRowHeight)
            .clickable(onClick = onClick)
            .padding(Dimens.SpaceL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = context.toImageVector(),
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(Dimens.SummaryIconSize),
        )
        Spacer(Modifier.width(Dimens.SpaceL))
        Text(
            text = stringResource(context.labelRes()),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
private fun ContextSelectScreenPreview() {
    AppTheme { ContextSelectScreen(onContextSelected = {}) }
}
