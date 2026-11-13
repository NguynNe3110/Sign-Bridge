package com.uzuu.signbridge.data.local.datasource

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import com.uzuu.signbridge.domain.model.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioRecorderDataSource(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var startedAt = 0L
    private var automaticallyStopped = false
    private var deviceError = false

    init {
        // Remove only this feature's abandoned temporary audio after process death.
        File(context.cacheDir, "speech").apply { mkdirs() }.listFiles()
            ?.filter { it.isFile && it.name.startsWith("reply_") && it.extension == "m4a" }
            ?.forEach { it.delete() }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission") // UI asks RECORD_AUDIO before starting.
    fun start() {
        cancel()
        val file = File.createTempFile("reply_", ".m4a", File(context.cacheDir, "speech").apply { mkdirs() })
        output = file
        val instance = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        recorder = instance
        automaticallyStopped = false
        deviceError = false
        try {
            instance.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16_000)
                setAudioEncodingBitRate(64_000)
                setMaxDuration(CapturePolicy.MAX_AUDIO_DURATION_MS.toInt())
                setMaxFileSize(CapturePolicy.MAX_AUDIO_BYTES.toLong())
                setOutputFile(file.absolutePath)
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                        what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) automaticallyStopped = true
                }
                setOnErrorListener { _, _, _ -> deviceError = true }
                prepare()
                start()
            }
            startedAt = SystemClock.elapsedRealtime()
        } catch (error: Exception) {
            cancel()
            throw BridgeException(BridgeError.DEVICE, "Không thu được âm thanh. Hãy kiểm tra quyền micro.", error)
        }
    }

    suspend fun finish(): RecordedAudio {
        val instance = recorder ?: throw BridgeException(BridgeError.DEVICE, "Chưa bắt đầu ghi âm.")
        val file = output ?: throw BridgeException(BridgeError.DEVICE, "Không tìm thấy bản ghi âm.")
        val duration = (SystemClock.elapsedRealtime() - startedAt).coerceAtMost(CapturePolicy.MAX_AUDIO_DURATION_MS)
        try {
            if (deviceError) throw BridgeException(BridgeError.DEVICE, "Micro bị gián đoạn. Hãy ghi âm lại.")
            if (!automaticallyStopped) instance.stop()
            instance.release()
            recorder = null
            return withContext(Dispatchers.IO) {
                if (file.length() > CapturePolicy.MAX_AUDIO_BYTES) {
                    throw BridgeException(BridgeError.PAYLOAD_TOO_LARGE, "Bản ghi âm vượt 2MB.")
                }
                RecordedAudio(file.readBytes(), duration).also { CapturePolicy.validateAudio(it) }
            }
        } catch (error: BridgeException) { throw error
        } catch (error: kotlinx.coroutines.CancellationException) { throw error
        } catch (error: Exception) {
            throw BridgeException(BridgeError.DEVICE, "Bản ghi quá ngắn hoặc bị gián đoạn. Hãy thử lại.", error)
        } finally {
            runCatching { recorder?.release() }
            recorder = null
            file.delete()
            output = null
        }
    }

    fun cancel() {
        runCatching { if (!automaticallyStopped) recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        output?.delete()
        output = null
    }
}

