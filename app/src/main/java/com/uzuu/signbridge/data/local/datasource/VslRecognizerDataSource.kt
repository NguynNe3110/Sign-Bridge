package com.uzuu.signbridge.data.local.datasource

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarker
import com.uzuu.signbridge.domain.model.BridgeError
import com.uzuu.signbridge.domain.model.BridgeException
import com.uzuu.signbridge.domain.model.GlossPrediction
import java.io.FileInputStream
import java.nio.channels.FileChannel
import org.json.JSONArray
import org.tensorflow.lite.Interpreter

/**
 * Nhận diện ký hiệu trên máy (guideline-vsl-hybrid §6): MediaPipe Holistic → 201 toạ độ/frame,
 * model TFLite `[1, 60, 201]` → softmax nhãn. Không gửi ảnh ra ngoài.
 * File model nằm ở `assets/vsl/` (không commit — xem README-BACKEND.md); thiếu file → [isAvailable] = false.
 */
class VslRecognizerDataSource(private val context: Context) {
    private val lock = Any()
    private var landmarker: HolisticLandmarker? = null
    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private var lastTimestampMs = 0L

    val isAvailable: Boolean by lazy {
        runCatching { context.assets.list(ASSET_DIR).orEmpty().toSet().containsAll(REQUIRED_FILES) }
            .getOrDefault(false)
    }

    /** Nạp model trước để lần ký đầu không bị chậm. Chạy trên luồng nền. */
    fun prepare() {
        if (isAvailable) synchronized(lock) { load() }
    }

    /** Landmark của 1 frame đã xoay đứng (RGB, không lật gương). Gọi tuần tự từ luồng phân tích camera. */
    fun extract(frame: Bitmap, timestampMs: Long): FloatArray = synchronized(lock) {
        val detector = load().first
        // detectForVideo yêu cầu timestamp tăng dần nghiêm ngặt trong suốt vòng đời detector.
        val timestamp = maxOf(timestampMs, lastTimestampMs + 1).also { lastTimestampMs = it }
        val result = detector.detectForVideo(BitmapImageBuilder(frame).build(), timestamp)
        VslSequence.pack(
            result.poseLandmarks().xyz(VslSequence.POSE_POINTS),
            result.leftHandLandmarks().xyz(VslSequence.HAND_POINTS),
            result.rightHandLandmarks().xyz(VslSequence.HAND_POINTS),
        )
    }

    /** Chuỗi 60×201 đã nội suy → nhãn top-1 nguyên văn trong labels.json + xác suất. */
    fun classify(sequence: Array<FloatArray>): GlossPrediction = synchronized(lock) {
        require(sequence.size == VslSequence.FRAMES && sequence.all { it.size == VslSequence.FEATURES })
        val model = load().second
        val output = Array(1) { FloatArray(labels.size) }
        try {
            model.run(arrayOf(sequence), output)
        } catch (error: Exception) {
            throw BridgeException(BridgeError.DEVICE, "Không chạy được model ký hiệu trên máy. Hãy dùng thẻ.", error)
        }
        val probabilities = output[0]
        var best = 0
        for (i in probabilities.indices) if (probabilities[i] > probabilities[best]) best = i
        GlossPrediction(labels[best], probabilities[best].coerceIn(0f, 1f))
    }

    fun close() = synchronized(lock) {
        landmarker?.close()
        interpreter?.close()
        landmarker = null
        interpreter = null
    }

    private fun load(): Pair<HolisticLandmarker, Interpreter> {
        val readyDetector = landmarker
        val readyModel = interpreter
        if (readyDetector != null && readyModel != null) return readyDetector to readyModel
        if (!isAvailable) throw BridgeException(BridgeError.NOT_CONFIGURED, "Máy chưa có model nhận diện ký hiệu.")
        var tflite: Interpreter? = null
        try {
            val names = context.assets.open("$ASSET_DIR/$LABELS").bufferedReader().use { JSONArray(it.readText()) }
            labels = List(names.length()) { names.getString(it) }
            val model = context.assets.openFd("$ASSET_DIR/$MODEL").use { fd ->
                FileInputStream(fd.fileDescriptor).use { input ->
                    input.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
            tflite = Interpreter(model, Interpreter.Options().setNumThreads(4))
            val outputSize = tflite.getOutputTensor(0).shape().last()
            check(outputSize == labels.size) { "labels.json (${labels.size}) không khớp model ($outputSize)" }
            val holistic = HolisticLandmarker.createFromOptions(context,
                HolisticLandmarker.HolisticLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("$ASSET_DIR/$HOLISTIC")
                        .setDelegate(Delegate.CPU).build())
                    .setRunningMode(RunningMode.VIDEO)
                    // Như repo nguồn: Holistic(min_detection_confidence=0.5, min_tracking_confidence=0.5).
                    .setMinPoseDetectionConfidence(0.5f)
                    .setMinHandLandmarksConfidence(0.5f)
                    .setOutputFaceBlendshapes(false)
                    .setOutputPoseSegmentationMasks(false)
                    .build())
            interpreter = tflite
            landmarker = holistic
            return holistic to tflite
        } catch (error: Exception) {
            tflite?.close()
            throw BridgeException(BridgeError.DEVICE, "Không nạp được model ký hiệu trên máy. Hãy dùng thẻ.", error)
        }
    }

    private fun List<NormalizedLandmark>.xyz(points: Int): FloatArray? {
        if (size < points) return null
        return FloatArray(points * 3).also { out ->
            for (i in 0 until points) {
                out[i * 3] = this[i].x()
                out[i * 3 + 1] = this[i].y()
                out[i * 3 + 2] = this[i].z()
            }
        }
    }

    private companion object {
        const val ASSET_DIR = "vsl"
        const val MODEL = "vsl_model.tflite"
        const val LABELS = "labels.json"
        const val HOLISTIC = "holistic_landmarker.task"
        val REQUIRED_FILES = setOf(MODEL, LABELS, HOLISTIC)
    }
}
