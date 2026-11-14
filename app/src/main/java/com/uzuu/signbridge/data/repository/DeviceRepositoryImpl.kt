package com.uzuu.signbridge.data.repository

import com.uzuu.signbridge.data.local.datasource.AudioRecorderDataSource
import com.uzuu.signbridge.data.local.datasource.SignCameraDataSource
import com.uzuu.signbridge.data.local.datasource.SpeechDataSource
import com.uzuu.signbridge.domain.repository.DeviceRepository

class DeviceRepositoryImpl(
    private val camera: SignCameraDataSource,
    private val recorder: AudioRecorderDataSource,
    private val speech: SpeechDataSource,
) : DeviceRepository {
    override val supportsOnDeviceSign: Boolean get() = camera.supportsOnDeviceSign
    override fun startSignCapture() = camera.start()
    override suspend fun finishSignCapture() = camera.finish()
    override fun startSignClip() = camera.startClip()
    override suspend fun finishSignClip() = camera.finishClip()
    override fun startRecording() = recorder.start()
    override suspend fun finishRecording() = recorder.finish()
    override suspend fun speak(sentence: String) { speech.speak(sentence) }
    override fun cancelCapture() { camera.cancel(); recorder.cancel() }
    override fun stopSpeaking() = speech.stop()
}
