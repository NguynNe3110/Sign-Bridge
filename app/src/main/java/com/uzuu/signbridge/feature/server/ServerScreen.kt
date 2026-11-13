package com.uzuu.signbridge.feature.server

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uzuu.signbridge.R
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
    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(Dimens.TouchTarget)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
            }
            Text(stringResource(R.string.server_title), style = MaterialTheme.typography.headlineSmall)
        }
        Text(stringResource(R.string.server_description), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.server_active), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary)
        SelectionContainer {
            Text(state.savedUrl ?: stringResource(R.string.server_not_set),
                style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(
            value = state.ipv4, onValueChange = { onEvent(ServerUiEvent.IpChanged(it)) },
            label = { Text(stringResource(R.string.server_ip_label)) },
            placeholder = { Text("192.168.1.10") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            singleLine = true, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.port, onValueChange = { onEvent(ServerUiEvent.PortChanged(it)) },
            label = { Text(stringResource(R.string.server_port_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.server_https), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge)
            val label = stringResource(R.string.server_https)
            Switch(state.useHttps, onCheckedChange = { onEvent(ServerUiEvent.HttpsChanged(it)) },
                enabled = !state.isBusy, modifier = Modifier.semantics { contentDescription = label })
        }
        Text(stringResource(R.string.server_network_hint), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline)
        state.message?.let { message ->
            Row(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
                if (state.isBusy) CircularProgressIndicator(Modifier.size(Dimens.ButtonIconSize))
                Text(message, style = MaterialTheme.typography.bodyMedium,
                    color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
            }
        }
        Button(
            onClick = { onEvent(ServerUiEvent.SaveAndConnect) }, enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTarget),
        ) { Text(stringResource(R.string.server_save)) }
        OutlinedButton(
            onClick = { onEvent(ServerUiEvent.CheckConnection) }, enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTarget),
        ) { Text(stringResource(R.string.server_check)) }
        Text(stringResource(R.string.server_save_hint), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Dimens.SpaceL))
    }
}

@Preview
@Composable
private fun ServerPreview() {
    AppTheme { ServerContent(ServerUiState(ipv4 = "192.168.1.10"), {}, {}) }
}
