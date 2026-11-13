package com.uzuu.signbridge.data.local.datasource

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.uzuu.signbridge.core.ui.CameraPreviewController
import com.uzuu.signbridge.domain.model.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SignCameraDataSource(private val context: Context) : CameraPreviewController {
    private val executor = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var analyzer: ImageAnalysis? = null
    private var bound = false
    private var generation = 0
    private var capturing = false
    private var startedAt = 0L
    private var sampledAt = -CapturePolicy.FRAME_INTERVAL_MS
    private val frames = mutableListOf<SignFrame>()
    private var captureError: Exception? = null

    @SuppressLint("MissingPermission") // Permission is requested by the host before bind.
    override fun bind(owner: LifecycleOwner, surface: Preview.SurfaceProvider, rotation: Int, onReady: (Result<Unit>) -> Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return@addListener
            try {
                unbind()
                val cameraProvider = future.get()
                val cameraPreview = Preview.Builder().build().also { it.setSurfaceProvider(surface) }
                val analysis = ImageAnalysis.Builder()
                    .setTargetRotation(rotation)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also { it.setAnalyzer(executor, ::analyze) }
                cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, cameraPreview, analysis)
                provider = cameraProvider
                preview = cameraPreview
                analyzer = analysis
                synchronized(lock) { bound = true }
                onReady(Result.success(Unit))
            } catch (error: Exception) {
                onReady(Result.failure(BridgeException(BridgeError.DEVICE, "Không mở được camera trước. Bạn có thể dùng thẻ.", error)))
            }
        }, ContextCompat.getMainExecutor(context))
    }

    override fun unbind() {
        cancel()
        synchronized(lock) { bound = false }
        analyzer?.clearAnalyzer()
        preview?.let { provider?.unbind(it) }
        analyzer?.let { provider?.unbind(it) }
        preview = null
        analyzer = null
    }

    fun start() = synchronized(lock) {
        if (!bound) throw BridgeException(BridgeError.DEVICE, "Hãy mở camera và cấp quyền trước khi ghi ký hiệu.")
        generation++
        frames.clear()
        captureError = null
        startedAt = SystemClock.elapsedRealtime()
        sampledAt = startedAt - CapturePolicy.FRAME_INTERVAL_MS
        capturing = true
    }

    suspend fun finish(): List<SignFrame> = withContext(Dispatchers.Default) {
        val result = synchronized(lock) {
            capturing = false
            generation++
            captureError?.let { throw BridgeException(BridgeError.DEVICE, "Không đọc được khung hình. Hãy dùng thẻ.", it) }
            val count = CapturePolicy.usableFrameCount(frames.size, SystemClock.elapsedRealtime() - startedAt)
            frames.take(count).also { frames.clear() }
        }
        CapturePolicy.validateFrames(result)
        result
    }

    fun cancel() = synchronized(lock) {
        capturing = false
        generation++
        frames.clear()
        captureError = null
    }

    private fun analyze(image: ImageProxy) {
        try {
            val now = SystemClock.elapsedRealtime()
            val token = synchronized(lock) {
                if (!capturing || frames.size >= 16 || now - startedAt >= CapturePolicy.MAX_SIGN_DURATION_MS ||
                    now - sampledAt < CapturePolicy.FRAME_INTERVAL_MS) return
                sampledAt = now
                generation
            }
            val frame = encode(image)
            synchronized(lock) {
                if (capturing && generation == token && frames.size < 16) frames.add(frame)
            }
        } catch (error: Exception) {
            synchronized(lock) { if (capturing) captureError = error }
        } finally { image.close() }
    }

    private fun encode(image: ImageProxy): SignFrame {
        // CameraX handles pixel format and row padding; rotate actual sensor pixels before upload.
        val bitmap = image.toBitmap()
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
            Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }, true)
        val scale = minOf(1f, 640f / maxOf(rotated.width, rotated.height))
        val resized = Bitmap.createScaledBitmap(rotated,
            (rotated.width * scale).toInt().coerceAtLeast(1),
            (rotated.height * scale).toInt().coerceAtLeast(1), true)
        try {
            var quality = 85
            var bytes: ByteArray
            do {
                bytes = ByteArrayOutputStream().use { out ->
                    check(resized.compress(Bitmap.CompressFormat.JPEG, quality, out))
                    out.toByteArray()
                }
                quality -= 10
            } while (bytes.size > CapturePolicy.MAX_FRAME_BYTES && quality >= 25)
            if (bytes.size > CapturePolicy.MAX_FRAME_BYTES) {
                throw BridgeException(BridgeError.PAYLOAD_TOO_LARGE, "Không nén được ảnh dưới 250KB.")
            }
            return SignFrame(bytes, resized.width, resized.height)
        } finally {
            setOf(resized, rotated, bitmap).forEach { it.recycle() }
        }
    }
}

