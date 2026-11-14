package com.uzuu.signbridge.feature.conversation.component

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import com.uzuu.signbridge.R
import com.uzuu.signbridge.domain.model.Highlight
import com.uzuu.signbridge.domain.model.HighlightType
import com.uzuu.signbridge.domain.model.IconCode
import com.uzuu.signbridge.domain.model.SummaryResult
import com.uzuu.signbridge.ui.icon.toImageVector
import com.uzuu.signbridge.ui.theme.AppTheme
import com.uzuu.signbridge.ui.theme.Dimens
import com.uzuu.signbridge.ui.theme.SummaryStyle

/** Tóm tắt lời nhân viên: icon + chữ lớn tô đậm số liệu + 3 nút trả lời nhanh. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SummaryCard(
    summary: SummaryResult,
    onQuickReply: (String) -> Unit,
    modifier: Modifier = Modifier,
    quickRepliesEnabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    var showTranscript by rememberSaveable(summary.transcript) { mutableStateOf(false) }
    val highlightColor = colors.onSurface
    val text = remember(summary, highlightColor) {
        buildHighlightedSummary(summary.summary, summary.highlights, highlightColor)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.CornerRadius),
        color = colors.surface,
    ) {
        Column(modifier = Modifier.padding(Dimens.SpaceL)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = summary.iconCode.toImageVector(),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.SummaryIconSize),
                )
                Spacer(Modifier.width(Dimens.SpaceM))
                Text(
                    text = stringResource(R.string.deaf_summary_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(Dimens.SpaceM))
            Text(text = text, style = SummaryStyle, color = colors.onSurface)

            if (summary.transcript.isNotBlank() && summary.transcript != summary.summary) {
                TextButton(
                    onClick = { showTranscript = !showTranscript },
                    modifier = Modifier.heightIn(min = Dimens.TouchTarget),
                ) {
                    Text(stringResource(if (showTranscript) R.string.deaf_hide_transcript else R.string.deaf_show_transcript))
                }
                if (showTranscript) {
                    SelectionContainer {
                        Text(
                            text = summary.transcript,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurface,
                        )
                    }
                }
            }

            if (summary.quickReplies.isNotEmpty()) {
                Spacer(Modifier.size(Dimens.SpaceL))
                Text(
                    text = stringResource(R.string.deaf_quick_replies),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.size(Dimens.SpaceS))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                ) {
                    summary.quickReplies.forEach { reply ->
                        OutlinedButton(
                            onClick = { onQuickReply(reply) },
                            enabled = quickRepliesEnabled,
                            modifier = Modifier
                                .heightIn(min = Dimens.TouchTarget),
                            shape = RoundedCornerShape(Dimens.CornerRadius),
                            border = BorderStroke(Dimens.BorderWidth, colors.outline),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
                        ) {
                            Text(text = reply, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

private fun buildHighlightedSummary(
    summary: String,
    highlights: List<Highlight>,
    highlightColor: Color,
): AnnotatedString = buildAnnotatedString {
    append(summary)
    findHighlightRanges(summary, highlights).forEach { range ->
        addStyle(
            style = SpanStyle(
                color = highlightColor,
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline,
            ),
            start = range.first,
            end = range.last + 1,
        )
    }
}

@Preview
@Composable
private fun SummaryCardPreview() {
    AppTheme {
        SummaryCard(
            summary = SummaryResult(
                transcript = "",
                summary = "Panadol Xanh • 1 viên sau ăn • Ngày 2 lần • 35.000đ",
                highlights = listOf(
                    Highlight(HighlightType.DOSAGE, "1 viên"),
                    Highlight(HighlightType.QUANTITY, "Ngày 2 lần"),
                    Highlight(HighlightType.PRICE, "35.000đ"),
                ),
                iconCode = IconCode.PILL,
                quickReplies = listOf("Lấy 1 hộp", "Chuyển khoản QR", "Có loại rẻ hơn không?"),
            ),
            onQuickReply = {},
        )
    }
}
