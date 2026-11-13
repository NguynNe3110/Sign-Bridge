package com.uzuu.signbridge.core.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.uzuu.signbridge.BuildConfig
import com.uzuu.signbridge.core.ui.CameraPreviewController
import com.uzuu.signbridge.data.local.AppDatabase
import com.uzuu.signbridge.data.local.dao.ConversationDao
import com.uzuu.signbridge.data.local.datasource.*
import com.uzuu.signbridge.data.remote.api.BridgeApi
import com.uzuu.signbridge.data.repository.*
import com.uzuu.signbridge.domain.repository.*
import com.uzuu.signbridge.domain.usecase.*
import com.uzuu.signbridge.feature.devicecheck.DeviceCheckViewModel
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase(appContext)
    private val camera = SignCameraDataSource(appContext)
    val cameraPreview: CameraPreviewController = camera
    val conversation: ConversationRepository = ConversationRepositoryImpl(ConversationDao(database))
    val translation: TranslationRepository = TranslationRepositoryImpl(BridgeApi(
        OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS).build(),
        BuildConfig.API_BASE_URL, BuildConfig.DEBUG,
    ))
    val device: DeviceRepository = DeviceRepositoryImpl(
        camera, AudioRecorderDataSource(appContext), SpeechDataSource(appContext),
    )
    val sendChips = SendChipsUseCase(conversation, translation)
    val recognizeSign = RecognizeSignUseCase(conversation, translation)
    val summarizeSpeech = SummarizeSpeechUseCase(conversation, translation)
    val speakQuickReply = SpeakQuickReplyUseCase(conversation, device)

    val deviceCheckFactory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DeviceCheckViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return DeviceCheckViewModel(conversation, translation, device,
                sendChips, recognizeSign, summarizeSpeech, speakQuickReply) as T
        }
    }
}

