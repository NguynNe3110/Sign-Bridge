package com.uzuu.signbridge.feature.conversation

import android.Manifest
import android.content.pm.PackageManager
import android.view.Surface
import android.view.ViewOutlineProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.uzuu.signbridge.R
import com.uzuu.signbridge.core.ui.CameraPreviewController
import com.uzuu.signbridge.ui.theme.Dimens

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
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            // CameraX scales the texture to fill; keep it inside the preview's bounds.
            outlineProvider = ViewOutlineProvider.BOUNDS
            clipToOutline = true
        }
    }
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun closeCamera() {
        if (viewModel.uiState.value.isSigning) {
            viewModel.onEvent(ConversationUiEvent.CaptureCancelled)
        }
        previewOpen = false
        cameraReady = false
        controller.unbind()
    }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) previewOpen = true
        else viewModel.onEvent(ConversationUiEvent.PermissionDenied(camera = true))
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onEvent(if (it) ConversationUiEvent.MicPermissionGranted
            else ConversationUiEvent.PermissionDenied(camera = false))
        // Do not start recording after the original press has already ended.
    }

    DisposableEffect(owner, viewModel, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                closeCamera()
                viewModel.onEvent(ConversationUiEvent.ScreenStopped)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            viewModel.onEvent(ConversationUiEvent.ScreenStopped)
        }
    }
    DisposableEffect(previewOpen, owner, controller) {
        var active = true
        if (previewOpen && granted(Manifest.permission.CAMERA)) {
            controller.bind(owner, previewView.surfaceProvider, previewView.display?.rotation ?: Surface.ROTATION_0) {
                if (!active || !previewOpen) return@bind
                cameraReady = it.isSuccess
                if (it.isFailure) {
                    previewOpen = false
                    viewModel.onEvent(ConversationUiEvent.CameraUnavailable)
                }
            }
        }
        onDispose {
            active = false
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
            Column {
                TextButton(
                    onClick = {
                        when {
                            previewOpen -> closeCamera()
                            granted(Manifest.permission.CAMERA) -> {
                                viewModel.onEvent(ConversationUiEvent.DeafErrorDismissed)
                                previewOpen = true
                            }
                            else -> cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.TouchTarget),
                ) {
                    Icon(if (previewOpen) Icons.Filled.VideocamOff else Icons.Filled.Videocam,
                        contentDescription = null)
                    Spacer(Modifier.width(Dimens.SpaceS))
                    Text(stringResource(if (previewOpen) R.string.camera_turn_off else R.string.camera_turn_on))
                }
                if (previewOpen) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clipToBounds(),
                    )
                    Text(stringResource(if (cameraReady) R.string.camera_hold_again else R.string.camera_preparing))
                }
            }
        },
    )
}
