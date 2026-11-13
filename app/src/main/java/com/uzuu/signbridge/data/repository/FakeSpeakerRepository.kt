package com.uzuu.signbridge.data.repository

import com.uzuu.signbridge.domain.repository.SpeakerRepository
import kotlinx.coroutines.delay

/**
 * Bản giả: không phát âm thanh, chỉ chờ theo độ dài câu để mô phỏng thời gian đọc.
 * TV2 thay bằng bản dùng Android TextToSpeech tiếng Việt.
 */
class FakeSpeakerRepository(
    private val millisPerChar: Long = 40,
) : SpeakerRepository {

    override suspend fun speak(sentence: String) {
        delay((sentence.length * millisPerChar).coerceIn(500L, 4_000L))
    }

    override fun stop() = Unit
}
