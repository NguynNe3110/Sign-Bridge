package com.uzuu.signbridge.core.di

import android.content.Context
import com.uzuu.signbridge.MyApp
import com.uzuu.signbridge.core.ui.CameraPreviewController
import com.uzuu.signbridge.domain.repository.ConversationRepository
import com.uzuu.signbridge.domain.repository.DeviceRepository
import com.uzuu.signbridge.domain.repository.TranslationRepository
import com.uzuu.signbridge.domain.usecase.RecognizeSignUseCase
import com.uzuu.signbridge.domain.usecase.SendChipsUseCase
import com.uzuu.signbridge.domain.usecase.SpeakQuickReplyUseCase
import com.uzuu.signbridge.domain.usecase.SummarizeSpeechUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppContainer(@ApplicationContext context: Context): AppContainer {
        return (context as MyApp).container
    }

    @Provides
    @Singleton
    fun provideConversationRepository(container: AppContainer): ConversationRepository {
        return container.conversation
    }

    @Provides
    @Singleton
    fun provideTranslationRepository(container: AppContainer): TranslationRepository {
        return container.translation
    }

    @Provides
    @Singleton
    fun provideDeviceRepository(container: AppContainer): DeviceRepository {
        return container.device
    }

    @Provides
    @Singleton
    fun provideCameraPreviewController(container: AppContainer): CameraPreviewController {
        return container.cameraPreview
    }

    @Provides
    @Singleton
    fun provideSendChipsUseCase(container: AppContainer): SendChipsUseCase {
        return container.sendChips
    }

    @Provides
    @Singleton
    fun provideRecognizeSignUseCase(container: AppContainer): RecognizeSignUseCase {
        return container.recognizeSign
    }

    @Provides
    @Singleton
    fun provideSummarizeSpeechUseCase(container: AppContainer): SummarizeSpeechUseCase {
        return container.summarizeSpeech
    }

    @Provides
    @Singleton
    fun provideSpeakQuickReplyUseCase(container: AppContainer): SpeakQuickReplyUseCase {
        return container.speakQuickReply
    }
}
