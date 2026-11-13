package com.uzuu.signbridge.feature.context

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
    ) {
        Text(
            text = stringResource(R.string.context_select_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.context_select_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline,
        )
        OutlinedButton(onClick = onServerSettings,
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTarget)) {
            Text(stringResource(R.string.server_title))
        }
        ContextType.entries.forEach { context ->
            ContextCard(context = context, onClick = { onContextSelected(context) })
        }
    }
}

@Composable
private fun ContextCard(
    context: ContextType,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.ContextCardHeight),
        shape = RoundedCornerShape(Dimens.CornerRadius),
        color = colors.surface,
        contentColor = colors.onSurface,
        border = BorderStroke(Dimens.BorderWidth, colors.primary),
    ) {
        Row(
            modifier = Modifier.padding(Dimens.SpaceXl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = context.toImageVector(),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(Dimens.SummaryIconSize),
            )
            Spacer(Modifier.width(Dimens.SpaceXl))
            Text(text = stringResource(context.labelRes()), style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Preview
@Composable
private fun ContextSelectScreenPreview() {
    AppTheme { ContextSelectScreen(onContextSelected = {}) }
}
