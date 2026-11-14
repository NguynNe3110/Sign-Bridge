package com.uzuu.signbridge.domain.model

object CapturePolicy {
    const val FRAME_INTERVAL_MS = 375L
    const val MAX_SIGN_DURATION_MS = 6_000L
    const val MAX_AUDIO_DURATION_MS = 60_000L
    const val MAX_FRAME_BYTES = 250 * 1024
    const val MAX_AUDIO_BYTES = 2 * 1024 * 1024

    // Nhận diện trên máy (guideline-vsl-hybrid §6–§7): 1 lần giữ nút = 1 ký hiệu ≈ 4 giây như lúc train.
    const val VSL_CLIP_MS = 4_000L
    /** Nội suy cubic cần ít nhất 4 frame có landmark. */
    const val VSL_MIN_CLIP_FRAMES = 4
    /** Mỗi lượt gom tối đa 10 ký hiệu rồi gọi `/api/sign/glosses` một lần. */
    const val MAX_TURN_GLOSSES = 10
    /** Cùng ngưỡng `VSL_MIN_GLOSS_CONFIDENCE` của server. */
    const val VSL_MIN_GLOSS_CONFIDENCE = 0.7f

    fun usableFrameCount(available: Int, durationMs: Long): Int =
        minOf(available, (durationMs.coerceAtMost(MAX_SIGN_DURATION_MS) / 1_500L * 4).toInt(), 16)
            .coerceAtLeast(0) / 4 * 4

    fun validateFrames(frames: List<SignFrame>) {
        if (frames.size !in 4..16 || frames.size % 4 != 0) {
            throw BridgeException(BridgeError.INVALID_REQUEST, "Giữ ký hiệu ít nhất 1,5 giây để lấy đủ 4 khung hình.")
        }
        if (frames.any { it.width !in 1..640 || it.height !in 1..640 || it.jpeg.isEmpty() || it.jpeg.size > MAX_FRAME_BYTES }) {
            throw BridgeException(BridgeError.PAYLOAD_TOO_LARGE, "Khung hình phải là JPEG tối đa 640px và 250KB.")
        }
    }

    fun validateAudio(audio: RecordedAudio) {
        if (audio.durationMs !in 300..MAX_AUDIO_DURATION_MS || audio.bytes.isEmpty()) {
            throw BridgeException(BridgeError.INVALID_REQUEST, "Giữ mic ít nhất 0,3 giây, tối đa 60 giây.")
        }
        if (audio.bytes.size > MAX_AUDIO_BYTES) {
            throw BridgeException(BridgeError.PAYLOAD_TOO_LARGE, "Bản ghi âm vượt quá 2MB.")
        }
    }
}

