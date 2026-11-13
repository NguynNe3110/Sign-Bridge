package com.uzuu.signbridge.domain.repository

import com.uzuu.signbridge.domain.model.RecordedAudio
import com.uzuu.signbridge.domain.model.SignFrame

interface DeviceRepository {
    fun startSignCapture()
    suspend fun finishSignCapture(): List<SignFrame>
    fun startRecording()
    suspend fun finishRecording(): RecordedAudio
    suspend fun speak(sentence: String)
    fun cancelCapture()
    fun stopSpeaking()
}

