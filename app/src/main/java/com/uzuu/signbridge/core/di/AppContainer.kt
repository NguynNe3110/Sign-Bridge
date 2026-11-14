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
import com.uzuu.signbridge.data.session.UserSessionManager
import com.uzuu.signbridge.domain.repository.*
import com.uzuu.signbridge.domain.usecase.*
import com.uzuu.signbridge.feature.devicecheck.DeviceCheckViewModel
import com.uzuu.signbridge.feature.conversation.ConversationViewModel
import com.uzuu.signbridge.feature.chip.ChipManageViewModel
import com.uzuu.signbridge.domain.model.ContextType
import com.uzuu.signbridge.domain.repository.ServerSettingsRepository
import com.uzuu.signbridge.feature.server.ServerViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase(appContext)
    private val camera = SignCameraDataSource(appContext)
    val cameraPreview: CameraPreviewController = camera
    // Direct AI requests include both speech recognition and LLM inference.
    private val httpClient = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS).build()
    val serverSettings: ServerSettingsRepository = ServerSettingsRepositoryImpl(
        appContext.getSharedPreferences("server_connection", Context.MODE_PRIVATE),
        probe = { address -> TranslationRepositoryImpl(BridgeApi(httpClient, address.baseUrl,
            BuildConfig.DEBUG, allowLocalHttp = true)).checkHealth() },
    )
    val bridgeApi = BridgeApi(
        httpClient,
        BuildConfig.API_BASE_URL, BuildConfig.DEBUG,
        baseUrlProvider = { serverSettings.address.value?.baseUrl ?: BuildConfig.API_BASE_URL },
        allowLocalHttp = true,
    )
    val userSession = UserSessionManager(
        appContext.getSharedPreferences("user_session", Context.MODE_PRIVATE),
    )
    val conversation: ConversationRepository = ConversationRepositoryImpl(
        dao = ConversationDao(database),
        api = bridgeApi,
        session = userSession,
    )
    val translation: TranslationRepository = TranslationRepositoryImpl(bridgeApi)
    val device: DeviceRepository = DeviceRepositoryImpl(
        camera, AudioRecorderDataSource(appContext), SpeechDataSource(appContext),
    )
    val sendChips = SendChipsUseCase(conversation, translation)
    val recognizeSign = RecognizeSignUseCase(conversation, translation)
    val summarizeSpeech = SummarizeSpeechUseCase(conversation, translation)
    val speakQuickReply = SpeakQuickReplyUseCase(conversation, device)
    val serverViewModelFactory = viewModelFactory { initializer { ServerViewModel(serverSettings) } }

    fun conversationViewModelFactory(context: ContextType) = viewModelFactory {
        initializer { ConversationViewModel(context, conversation, device,
            sendChips, recognizeSign, summarizeSpeech, speakQuickReply) }
    }

    fun chipManageViewModelFactory(context: ContextType) = viewModelFactory {
        initializer { ChipManageViewModel(context, conversation) }
    }

    val deviceCheckFactory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DeviceCheckViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return DeviceCheckViewModel(conversation, translation, device,
                sendChips, recognizeSign, summarizeSpeech, speakQuickReply) as T
        }
    }
}
