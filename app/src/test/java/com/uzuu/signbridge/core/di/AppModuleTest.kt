package com.uzuu.signbridge.core.di

import android.content.Context
import com.uzuu.signbridge.MyApp
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppModuleTest {

    @Test
    fun appModule_delegatesToAppContainer() {
        val app = RuntimeEnvironment.getApplication() as MyApp
        val container = AppModule.provideAppContainer(app)

        assertSame(app.container, container)
        assertSame(container.conversation, AppModule.provideConversationRepository(container))
        assertSame(container.translation, AppModule.provideTranslationRepository(container))
        assertSame(container.device, AppModule.provideDeviceRepository(container))
        assertSame(container.cameraPreview, AppModule.provideCameraPreviewController(container))
        assertSame(container.sendChips, AppModule.provideSendChipsUseCase(container))
        assertSame(container.recognizeSign, AppModule.provideRecognizeSignUseCase(container))
        assertSame(container.summarizeSpeech, AppModule.provideSummarizeSpeechUseCase(container))
        assertSame(container.speakQuickReply, AppModule.provideSpeakQuickReplyUseCase(container))
    }
}
