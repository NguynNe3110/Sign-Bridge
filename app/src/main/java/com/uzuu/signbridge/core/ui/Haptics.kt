package com.uzuu.signbridge.core.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Kiểu rung báo cho người khiếm thính. */
enum class HapticPattern {
    /** Loa vừa phát xong câu của người khiếm thính. */
    SPEECH_FINISHED,

    /** Nhân viên bắt đầu nói. */
    STAFF_STARTED_SPEAKING,
}

/** Bọc Vibrator; cần quyền VIBRATE trong manifest. */
class Haptics(context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    fun vibrate(pattern: HapticPattern) {
        val target = vibrator?.takeIf { it.hasVibrator() } ?: return
        val timings = when (pattern) {
            // 2 nhịp ngắn: "đã đọc xong".
            HapticPattern.SPEECH_FINISHED -> longArrayOf(0, 120, 100, 120)
            // 1 nhịp dài: "nhân viên đang nói".
            HapticPattern.STAFF_STARTED_SPEAKING -> longArrayOf(0, 350)
        }
        target.vibrate(VibrationEffect.createWaveform(timings, -1))
    }
}
