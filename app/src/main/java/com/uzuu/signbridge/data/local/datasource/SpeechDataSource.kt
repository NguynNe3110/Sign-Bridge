package com.uzuu.signbridge.data.local.datasource

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.uzuu.signbridge.domain.model.BridgeError
import com.uzuu.signbridge.domain.model.BridgeException
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class SpeechDataSource(private val context: Context) {
    private var engine: TextToSpeech? = null
    private val mutex = Mutex()

    suspend fun speak(sentence: String) = withContext(Dispatchers.Main.immediate) {
        mutex.withLock {
            val completed = withTimeoutOrNull(30_000) {
                val tts = engine ?: initialize().also { engine = it }
                val language = tts.setLanguage(Locale.forLanguageTag("vi-VN"))
                if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                    throw BridgeException(BridgeError.DEVICE, "Máy chưa có giọng đọc tiếng Việt. Câu vẫn hiển thị để đọc.")
                }
                suspendCancellableCoroutine<Unit> { continuation ->
                    val utteranceId = UUID.randomUUID().toString()
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) = Unit
                        override fun onDone(id: String?) {
                            if (id == utteranceId && continuation.isActive) continuation.resume(Unit)
                        }
                        @Deprecated("Android callback")
                        override fun onError(id: String?) {
                            if (id == utteranceId && continuation.isActive) continuation.resumeWithException(
                                BridgeException(BridgeError.DEVICE, "Không phát được giọng nói. Câu vẫn hiển thị trên màn hình."))
                        }
                        override fun onError(id: String?, errorCode: Int) = onError(id)
                    })
                    continuation.invokeOnCancellation { tts.stop() }
                    if (tts.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.ERROR &&
                        continuation.isActive) {
                        continuation.resumeWithException(BridgeException(BridgeError.DEVICE, "Không phát được giọng nói."))
                    }
                }
                true
            }
            if (completed == null) throw BridgeException(BridgeError.DEVICE, "Phát giọng nói quá lâu. Câu vẫn hiển thị trên màn hình.")
        }
    }

    private suspend fun initialize(): TextToSpeech = suspendCancellableCoroutine { continuation ->
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            val current = instance
            if (status == TextToSpeech.SUCCESS && current != null && continuation.isActive) {
                continuation.resume(current) { _, value, _ -> value.shutdown() }
            } else {
                current?.shutdown()
                if (continuation.isActive) continuation.resumeWithException(
                    BridgeException(BridgeError.DEVICE, "Không khởi tạo được bộ đọc tiếng Việt."))
            }
        }
        continuation.invokeOnCancellation { instance?.shutdown() }
    }

    fun stop() { engine?.stop() }
}

