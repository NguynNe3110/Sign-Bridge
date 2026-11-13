package com.uzuu.signbridge.domain.repository

/** Phát loa TTS tiếng Việt (TV2 cài đặt). */
interface SpeakerRepository {

    /** Trả về khi đã đọc xong câu. */
    suspend fun speak(sentence: String)

    fun stop()
}
