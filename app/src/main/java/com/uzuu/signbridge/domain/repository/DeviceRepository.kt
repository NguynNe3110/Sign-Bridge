package com.uzuu.signbridge.domain.repository

import com.uzuu.signbridge.domain.model.GlossPrediction
import com.uzuu.signbridge.domain.model.RecordedAudio
import com.uzuu.signbridge.domain.model.SignFrame

interface DeviceRepository {
    /** true khi máy có model nhận diện ký hiệu offline; false → dùng luồng gửi ảnh `/api/sign`. */
    val supportsOnDeviceSign: Boolean
    fun startSignCapture()
    suspend fun finishSignCapture(): List<SignFrame>
    /** Bắt đầu ghi 1 ký hiệu bằng landmark trên máy (không gửi ảnh lên server). */
    fun startSignClip()
    suspend fun finishSignClip(): GlossPrediction
    fun startRecording()
    suspend fun finishRecording(): RecordedAudio
    suspend fun speak(sentence: String)
    fun cancelCapture()
    fun stopSpeaking()
}
