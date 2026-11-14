package com.uzuu.signbridge.feature.chip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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

@Composable
fun ChipManageContent(
    state: ChipManageUiState,
    onEvent: (ChipManageUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val inputFocus = remember { FocusRequester() }
    LaunchedEffect(state.editingChipId) {
        if (state.editingChipId != null) {
            listState.scrollToItem(1)
            withFrameNanos { }
            inputFocus.requestFocus()
        }
    }
    Box(modifier = modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Dimens.ContentMaxWidth).fillMaxSize()) {
            AppTopBar(title = stringResource(R.string.action_manage_chips), onBack = onBack)
            LazyColumn(
                state = listState,
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
                    ChipInput(
                        value = state.newLabel,
                        error = state.inputError,
                        editing = state.editingChipId != null,
                        saving = state.isSaving,
                        inputFocus = inputFocus,
                        onValueChange = { onEvent(ChipManageUiEvent.NewLabelChanged(it)) },
                        onSave = {
                            onEvent(if (state.editingChipId == null) ChipManageUiEvent.AddClicked
                                else ChipManageUiEvent.SaveEditClicked)
                        },
                        onCancel = { onEvent(ChipManageUiEvent.CancelEditClicked) },
                    )
                }

                item {
                    Text(stringResource(R.string.chip_manage_local_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    ChipRow(chip = chip, enabled = !state.isSaving,
                        onEdit = { onEvent(ChipManageUiEvent.EditClicked(chip.id)) },
                        onDelete = { onEvent(ChipManageUiEvent.DeleteClicked(chip.id)) })
                }

                item { SectionTitle(stringResource(R.string.chip_manage_preset)) }
                items(state.presetChips, key = { it.id }) { chip ->
                    ChipRow(chip = chip, enabled = !state.isSaving,
                        onEdit = { onEvent(ChipManageUiEvent.EditClicked(chip.id)) })
                }
                item { Spacer(Modifier.size(Dimens.SpaceXl)) }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ChipInput(
    value: String,
    error: ChipInputError?,
    editing: Boolean,
    saving: Boolean,
    inputFocus: FocusRequester,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = !saving,
            label = { Text(stringResource(if (editing) R.string.chip_manage_edit_label else R.string.chip_manage_input_label)) },
            isError = error != null,
            supportingText = { Text(error?.message() ?: stringResource(R.string.chip_manage_input_hint)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (!saving) onSave() }),
            modifier = Modifier.fillMaxWidth().focusRequester(inputFocus),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        ) {
            if (editing) {
                TextButton(
                    onClick = onCancel,
                    enabled = !saving,
                    modifier = Modifier.heightIn(min = Dimens.TouchTarget),
                ) {
                    Text(stringResource(R.string.chip_manage_cancel))
                }
            }
            Button(
                onClick = onSave,
                enabled = !saving,
                modifier = Modifier.heightIn(min = Dimens.TouchTarget),
                shape = RoundedCornerShape(Dimens.CornerRadius),
            ) {
                if (!editing) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.size(Dimens.SpaceS))
                }
                Text(stringResource(when {
                    saving -> R.string.chip_manage_saving
                    editing -> R.string.chip_manage_save
                    else -> R.string.chip_manage_add
                }), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ChipInputError.message(): String = when (this) {
    ChipInputError.EMPTY -> stringResource(R.string.chip_error_empty)
    ChipInputError.TOO_LONG -> stringResource(R.string.chip_error_too_long, ChipManageViewModel.MAX_LABEL_LENGTH)
    ChipInputError.INVALID_CHARACTERS -> stringResource(R.string.chip_error_invalid_characters)
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
private fun ChipRow(chip: QuickChip, enabled: Boolean, onEdit: () -> Unit, onDelete: (() -> Unit)? = null) {
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
            IconButton(onClick = onEdit, enabled = enabled, modifier = Modifier.size(Dimens.TouchTarget)) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.chip_manage_edit, chip.label),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onDelete != null) IconButton(onClick = onDelete, enabled = enabled, modifier = Modifier.size(Dimens.TouchTarget)) {
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
