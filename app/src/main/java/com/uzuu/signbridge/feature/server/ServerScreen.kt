package com.uzuu.signbridge.feature.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uzuu.signbridge.R
import com.uzuu.signbridge.ui.component.navigation.AppTopBar
import com.uzuu.signbridge.ui.theme.AppTheme
import com.uzuu.signbridge.ui.theme.Dimens

@Composable
fun ServerScreen(viewModel: ServerViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ServerContent(state, viewModel::onEvent, onBack)
}

@Composable
fun ServerContent(
    state: ServerUiState,
    onEvent: (ServerUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Dimens.ContentMaxWidth).fillMaxSize()) {
            AppTopBar(title = stringResource(R.string.server_title), onBack = onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
            ) {
                Text(
                    stringResource(R.string.server_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                Surface(shape = RoundedCornerShape(Dimens.CornerRadius), color = colors.surface) {
                    Column(
                        Modifier.fillMaxWidth().padding(Dimens.SpaceL),
                        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
                    ) {
                        Text(
                            stringResource(R.string.server_active),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                        SelectionContainer {
                            Text(
                                state.savedUrl ?: stringResource(R.string.server_not_set),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = state.ipv4,
                    onValueChange = { onEvent(ServerUiEvent.IpChanged(it)) },
                    label = { Text(stringResource(R.string.server_ip_label)) },
                    placeholder = { Text("192.168.1.10") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                    singleLine = true,
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.port,
                    onValueChange = { onEvent(ServerUiEvent.PortChanged(it)) },
                    label = { Text(stringResource(R.string.server_port_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    singleLine = true,
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTarget),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val label = stringResource(R.string.server_https)
                    Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        state.useHttps,
                        onCheckedChange = { onEvent(ServerUiEvent.HttpsChanged(it)) },
                        enabled = !state.isBusy,
                        modifier = Modifier.semantics { contentDescription = label },
                    )
                }
                state.message?.let { message ->
                    Surface(
                        color = if (state.isError) colors.errorContainer else colors.surfaceVariant,
                        contentColor = if (state.isError) colors.onErrorContainer else colors.onSurfaceVariant,
                        shape = RoundedCornerShape(Dimens.CornerRadius),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(Dimens.SpaceL)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
                        ) {
                            if (state.isBusy) CircularProgressIndicator(Modifier.size(Dimens.ButtonIconSize))
                            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                    Button(
                        onClick = { onEvent(ServerUiEvent.SaveAndConnect) },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTarget),
                        shape = RoundedCornerShape(Dimens.CornerRadius),
                    ) { Text(stringResource(R.string.server_save)) }
                    OutlinedButton(
                        onClick = { onEvent(ServerUiEvent.CheckConnection) },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTarget),
                        shape = RoundedCornerShape(Dimens.CornerRadius),
                    ) { Text(stringResource(R.string.server_check)) }
                }
                TextButton(
                    onClick = { showHelp = !showHelp },
                    modifier = Modifier.heightIn(min = Dimens.TouchTarget),
                ) {
                    Text(stringResource(if (showHelp) R.string.server_help_hide else R.string.server_help_show))
                }
                if (showHelp) {
                    Text(
                        stringResource(R.string.server_network_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.server_save_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Dimens.SpaceS))
            }
        }
    }
}

@Preview
@Composable
private fun ServerPreview() {
    AppTheme { ServerContent(ServerUiState(ipv4 = "192.168.1.10"), {}, {}) }
}
