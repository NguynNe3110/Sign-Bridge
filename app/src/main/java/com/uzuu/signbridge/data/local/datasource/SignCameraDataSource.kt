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

/**
 * Camera trước cho 2 chế độ:
 * - [start]/[finish]: lấy 4–16 ảnh JPEG gửi `/api/sign` (khi máy không có model).
 * - [startClip]/[finishClip]: 1 ký hiệu → landmark từng frame → model trên máy (mục 4.2a), không lưu/gửi ảnh.
 */
class SignCameraDataSource(
    private val context: Context,
    private val recognizer: VslRecognizerDataSource? = null,
) : CameraPreviewController {
    private val executor = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var analyzer: ImageAnalysis? = null
    private var bound = false
    private var generation = 0
    private var capturing = false
    private var clipMode = false
    private var startedAt = 0L
    private var sampledAt = -CapturePolicy.FRAME_INTERVAL_MS
    private val frames = mutableListOf<SignFrame>()
    private val landmarks = mutableListOf<FloatArray>()
    private var captureError: Exception? = null

    val supportsOnDeviceSign: Boolean
        get() = recognizer?.isAvailable == true

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
                // Nạp model trên luồng phân tích (cùng executor) trước frame đầu tiên của lần ký đầu.
                if (supportsOnDeviceSign) executor.execute { runCatching { recognizer?.prepare() } }
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

    fun start() = begin(clip = false)

    suspend fun finish(): List<SignFrame> = withContext(Dispatchers.Default) {
        val result = synchronized(lock) {
            stopCapture()
            val count = CapturePolicy.usableFrameCount(frames.size, SystemClock.elapsedRealtime() - startedAt)
            frames.take(count).also { frames.clear() }
        }
        CapturePolicy.validateFrames(result)
        result
    }

    fun startClip() {
        if (!supportsOnDeviceSign) {
            throw BridgeException(BridgeError.NOT_CONFIGURED, "Máy chưa có model nhận diện ký hiệu.")
        }
        begin(clip = true)
    }

    /** Kết thúc 1 ký hiệu: nội suy về 60 frame rồi chạy model (guideline §6). */
    suspend fun finishClip(): GlossPrediction = withContext(Dispatchers.Default) {
        val clip = synchronized(lock) {
            stopCapture()
            landmarks.toList().also { landmarks.clear() }
        }
        // Repo nguồn giữ cả frame không thấy tay (điền 0); chỉ từ chối khi cả clip không có tay nào.
        if (clip.size < CapturePolicy.VSL_MIN_CLIP_FRAMES || clip.none(VslSequence::hasHand)) {
            throw BridgeException(BridgeError.NO_SIGN_DETECTED,
                "Chưa thấy tay trong khung hình. Giữ nút lâu hơn và ký trước camera.")
        }
        requireNotNull(recognizer).classify(VslSequence.interpolate(clip))
    }

    fun cancel() = synchronized(lock) {
        capturing = false
        clipMode = false
        generation++
        frames.clear()
        landmarks.clear()
        captureError = null
    }

    private fun begin(clip: Boolean) = synchronized(lock) {
        if (!bound) throw BridgeException(BridgeError.DEVICE, "Hãy mở camera và cấp quyền trước khi ghi ký hiệu.")
        generation++
        frames.clear()
        landmarks.clear()
        captureError = null
        startedAt = SystemClock.elapsedRealtime()
        sampledAt = startedAt - CapturePolicy.FRAME_INTERVAL_MS
        clipMode = clip
        capturing = true
    }

    /** Gọi trong `lock`. */
    private fun stopCapture() {
        capturing = false
        clipMode = false
        generation++
        captureError?.let {
            throw it as? BridgeException ?: BridgeException(BridgeError.DEVICE, "Không đọc được khung hình. Hãy dùng thẻ.", it)
        }
    }

    private fun analyze(image: ImageProxy) {
        try {
            val now = SystemClock.elapsedRealtime()
            var clip = false
            val token = synchronized(lock) {
                if (!capturing) return
                clip = clipMode
                if (clip) {
                    // Lấy mọi frame kịp xử lý trong 4 giây như repo nguồn; frame đến khi đang bận bị CameraX bỏ.
                    if (now - startedAt >= CapturePolicy.VSL_CLIP_MS || landmarks.size >= MAX_CLIP_FRAMES) return
                } else {
                    if (frames.size >= 16 || now - startedAt >= CapturePolicy.MAX_SIGN_DURATION_MS ||
                        now - sampledAt < CapturePolicy.FRAME_INTERVAL_MS) return
                    sampledAt = now
                }
                generation
            }
            if (clip) {
                val upright = uprightBitmap(image)
                val vector = try {
                    requireNotNull(recognizer).extract(upright, SystemClock.uptimeMillis())
                } finally { upright.recycle() }
                synchronized(lock) {
                    if (capturing && clipMode && generation == token) landmarks.add(vector)
                }
            } else {
                val frame = encode(image)
                synchronized(lock) {
                    if (capturing && !clipMode && generation == token && frames.size < 16) frames.add(frame)
                }
            }
        } catch (error: Exception) {
            synchronized(lock) { if (capturing) captureError = error }
        } finally { image.close() }
    }

    /** Ảnh cảm biến đã xoay đứng, không lật gương (giống frame webcam đưa vào MediaPipe lúc train). */
    private fun uprightBitmap(image: ImageProxy): Bitmap {
        val bitmap = image.toBitmap()
        val degrees = image.imageInfo.rotationDegrees
        if (degrees == 0) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
            Matrix().apply { postRotate(degrees.toFloat()) }, true).also { if (it !== bitmap) bitmap.recycle() }
    }

    private fun encode(image: ImageProxy): SignFrame {
        // CameraX handles pixel format and row padding; rotate actual sensor pixels before upload.
        val rotated = uprightBitmap(image)
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
            setOf(resized, rotated).forEach { it.recycle() }
        }
    }

    private companion object {
        /** Trần an toàn bộ nhớ: 4 giây × 30 fps. */
        const val MAX_CLIP_FRAMES = 120
    }
}
