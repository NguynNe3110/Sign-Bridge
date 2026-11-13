package com.uzuu.signbridge.core.ui

import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner

/** Lifecycle wiring only. Screens request CAMERA before binding. */
interface CameraPreviewController {
    fun bind(owner: LifecycleOwner, surface: Preview.SurfaceProvider, rotation: Int, onReady: (Result<Unit>) -> Unit)
    fun unbind()
}

