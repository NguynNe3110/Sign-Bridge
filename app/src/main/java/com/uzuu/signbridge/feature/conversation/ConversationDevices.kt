package com.uzuu.signbridge.feature.conversation

import android.Manifest
import android.content.pm.PackageManager
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.uzuu.signbridge.R
import com.uzuu.signbridge.core.ui.CameraPreviewController

internal data class ConversationDeviceControls(
    val onSignPressed: () -> Unit,
    val onMicPressed: () -> Unit,
    val preview: @Composable () -> Unit,
)

/** Permission and surface lifecycle belong to the Screen, never the ViewModel. */
@Composable
internal fun rememberConversationDevices(
    viewModel: ConversationViewModel,
    controller: CameraPreviewController,
): ConversationDeviceControls {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var previewOpen by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    val previewView = remember(context) {
        PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
    }
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) previewOpen = true
        else viewModel.onEvent(ConversationUiEvent.PermissionDenied(camera = true))
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (!it) viewModel.onEvent(ConversationUiEvent.PermissionDenied(camera = false))
        // Do not start recording after the original press has already ended.
    }

    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onEvent(ConversationUiEvent.ScreenStopped)
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            viewModel.onEvent(ConversationUiEvent.ScreenStopped)
        }
    }
    DisposableEffect(previewOpen, owner, controller) {
        if (previewOpen && granted(Manifest.permission.CAMERA)) {
            controller.bind(owner, previewView.surfaceProvider, previewView.display?.rotation ?: Surface.ROTATION_0) {
                cameraReady = it.isSuccess
                if (it.isFailure) {
                    viewModel.onEvent(ConversationUiEvent.CameraUnavailable)
                }
            }
        }
        onDispose {
            cameraReady = false
            if (previewOpen) controller.unbind()
        }
    }

    return ConversationDeviceControls(
        onSignPressed = {
            when {
                !granted(Manifest.permission.CAMERA) -> cameraPermission.launch(Manifest.permission.CAMERA)
                !previewOpen -> previewOpen = true
                cameraReady -> viewModel.onEvent(ConversationUiEvent.SignPressed)
            }
        },
        onMicPressed = {
            if (granted(Manifest.permission.RECORD_AUDIO)) viewModel.onEvent(ConversationUiEvent.MicPressed)
            else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        },
        preview = {
            if (previewOpen) Column {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxWidth().height(112.dp))
                Text(stringResource(if (cameraReady) R.string.camera_hold_again else R.string.camera_preparing))
            }
        },
    )
}
