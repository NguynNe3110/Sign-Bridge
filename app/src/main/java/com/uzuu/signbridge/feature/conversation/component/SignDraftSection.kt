package com.uzuu.signbridge.feature.conversation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.uzuu.signbridge.R
import com.uzuu.signbridge.domain.model.CapturePolicy
import com.uzuu.signbridge.domain.model.GlossPrediction
import com.uzuu.signbridge.ui.theme.AppTheme
import com.uzuu.signbridge.ui.theme.Dimens
import kotlin.math.roundToInt

/** Các ký hiệu nhận diện trên máy trong lượt hiện tại (mục 4.2a), chờ người dùng bấm Ghép câu. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SignDraftSection(
    draft: List<GlossPrediction>,
    enabled: Boolean,
    canAddMore: Boolean,
    onSend: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
    ) {
        Text(
            text = stringResource(R.string.deaf_sign_draft_title, draft.size, CapturePolicy.MAX_TURN_GLOSSES),
            style = MaterialTheme.typography.titleMedium,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        ) {
            draft.forEach { gloss ->
                val doubtful = gloss.confidence < CapturePolicy.VSL_MIN_GLOSS_CONFIDENCE
                Surface(
                    shape = RoundedCornerShape(Dimens.CornerRadius),
                    color = if (doubtful) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.deaf_sign_draft_item, gloss.label,
                            (gloss.confidence * 100).roundToInt()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (doubtful) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = Dimens.SpaceM, vertical = Dimens.SpaceS),
                    )
                }
            }
        }
        Text(
            text = if (canAddMore) stringResource(R.string.deaf_sign_draft_hint)
                else stringResource(R.string.deaf_sign_draft_full, CapturePolicy.MAX_TURN_GLOSSES),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClear, enabled = enabled, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
                Text(stringResource(R.string.deaf_sign_draft_clear))
            }
            Button(onClick = onSend, enabled = enabled, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
                Text(stringResource(R.string.deaf_sign_draft_send))
            }
        }
    }
}

@Preview
@Composable
private fun SignDraftSectionPreview() {
    AppTheme {
        SignDraftSection(
            draft = listOf(GlossPrediction("đau bụng", 0.93f), GlossPrediction("hiệu thuốc", 0.42f)),
            enabled = true, canAddMore = true, onSend = {}, onClear = {},
        )
    }
}
