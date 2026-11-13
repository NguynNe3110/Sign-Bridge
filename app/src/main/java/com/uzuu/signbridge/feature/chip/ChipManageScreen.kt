package com.uzuu.signbridge.feature.chip

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uzuu.signbridge.R
import com.uzuu.signbridge.domain.model.ContextType
import com.uzuu.signbridge.domain.model.QuickChip
import com.uzuu.signbridge.ui.component.text.labelRes
import com.uzuu.signbridge.ui.theme.AppTheme
import com.uzuu.signbridge.ui.theme.Dimens

@Composable
fun ChipManageScreen(
    viewModel: ChipManageViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChipManageContent(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipManageContent(
    state: ChipManageUiState,
    onEvent: (ChipManageUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(Dimens.TouchTarget)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Text(
                    text = stringResource(R.string.chip_manage_title, stringResource(state.context.labelRes())),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }

        item {
            AddChipInput(
                value = state.newLabel,
                error = state.inputError,
                onValueChange = { onEvent(ChipManageUiEvent.NewLabelChanged(it)) },
                onAdd = { onEvent(ChipManageUiEvent.AddClicked) },
            )
        }

        item { SectionTitle(stringResource(R.string.chip_manage_personal)) }
        if (state.personalChips.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.chip_manage_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        items(state.personalChips, key = { it.id }) { chip ->
            PersonalChipRow(chip = chip, onDelete = { onEvent(ChipManageUiEvent.DeleteClicked(chip.id)) })
        }

        item { SectionTitle(stringResource(R.string.chip_manage_preset)) }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
            ) {
                state.presetChips.forEach { chip ->
                    Surface(
                        shape = RoundedCornerShape(Dimens.CornerRadius),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(Dimens.BorderWidth, MaterialTheme.colorScheme.outline),
                    ) {
                        Text(
                            text = chip.label,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceM),
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.size(Dimens.SpaceXl)) }
    }
}

@Composable
private fun AddChipInput(
    value: String,
    error: ChipInputError?,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.chip_manage_input_label)) },
            isError = error != null,
            supportingText = error?.let { { Text(it.message()) } },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(Dimens.SpaceS))
        Button(
            onClick = onAdd,
            modifier = Modifier
                .padding(top = Dimens.SpaceS)
                .heightIn(min = Dimens.TouchTarget),
            shape = RoundedCornerShape(Dimens.CornerRadius),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(R.string.chip_manage_add), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ChipInputError.message(): String = when (this) {
    ChipInputError.EMPTY -> stringResource(R.string.chip_error_empty)
    ChipInputError.TOO_LONG -> stringResource(R.string.chip_error_too_long, ChipManageViewModel.MAX_LABEL_LENGTH)
    ChipInputError.DUPLICATE -> stringResource(R.string.chip_error_duplicate)
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Dimens.SpaceS),
    )
}

@Composable
private fun PersonalChipRow(chip: QuickChip, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.CornerRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(Dimens.BorderWidth, MaterialTheme.colorScheme.primary),
    ) {
        Row(
            modifier = Modifier.padding(start = Dimens.SpaceL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = chip.label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(Dimens.TouchTarget)) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.chip_manage_delete, chip.label),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Preview
@Composable
private fun ChipManageContentPreview() {
    AppTheme {
        ChipManageContent(
            state = ChipManageUiState(
                context = ContextType.PHARMACY,
                personalChips = listOf(QuickChip(1, ContextType.PHARMACY, "Dị ứng penicillin", isPersonal = true)),
                presetChips = listOf(
                    QuickChip(2, ContextType.PHARMACY, "Đau đầu", isPersonal = false),
                    QuickChip(3, ContextType.CUSTOM, "Cảm ơn", isPersonal = false),
                ),
            ),
            onEvent = {},
            onBack = {},
        )
    }
}
