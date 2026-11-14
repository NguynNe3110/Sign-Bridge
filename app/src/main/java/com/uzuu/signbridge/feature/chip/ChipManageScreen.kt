package com.uzuu.signbridge.feature.chip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
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
import com.uzuu.signbridge.ui.component.navigation.AppTopBar
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
    Box(modifier = modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Dimens.ContentMaxWidth).fillMaxSize()) {
            AppTopBar(title = stringResource(R.string.action_manage_chips), onBack = onBack)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
                contentPadding = PaddingValues(bottom = Dimens.SpaceXl),
            ) {
                item {
                    Text(
                        text = stringResource(state.context.labelRes()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            ) {
                                Text(
                                    text = chip.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceM),
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.size(Dimens.SpaceXl)) }
            }
        }
    }
}

@Composable
private fun AddChipInput(
    value: String,
    error: ChipInputError?,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.chip_manage_input_label)) },
            isError = error != null,
            supportingText = { Text(error?.message() ?: stringResource(R.string.chip_manage_input_hint)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.End)
                .heightIn(min = Dimens.TouchTarget),
            shape = RoundedCornerShape(Dimens.CornerRadius),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.size(Dimens.SpaceS))
            Text(stringResource(R.string.chip_manage_add), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ChipInputError.message(): String = when (this) {
    ChipInputError.EMPTY -> stringResource(R.string.chip_error_empty)
    ChipInputError.TOO_LONG -> stringResource(R.string.chip_error_too_long, ChipManageViewModel.MAX_LABEL_LENGTH)
    ChipInputError.DUPLICATE -> stringResource(R.string.chip_error_duplicate)
    ChipInputError.SAVE_FAILED -> stringResource(R.string.error_storage)
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = Dimens.SpaceL),
    )
}

@Composable
private fun PersonalChipRow(chip: QuickChip, onDelete: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = chip.label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(vertical = Dimens.SpaceM),
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(Dimens.TouchTarget)) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.chip_manage_delete, chip.label),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Preview
@Composable
private fun ChipManageContentPreview() {
    AppTheme {
        ChipManageContent(
            state = ChipManageUiState(
                context = ContextType.PHARMACY,
                personalChips = listOf(QuickChip("1", ContextType.PHARMACY, "Dị ứng penicillin", isPersonal = true)),
                presetChips = listOf(
                    QuickChip("2", ContextType.PHARMACY, "Đau đầu", isPersonal = false),
                    QuickChip("3", ContextType.CUSTOM, "Cảm ơn", isPersonal = false),
                ),
            ),
            onEvent = {},
            onBack = {},
        )
    }
}
