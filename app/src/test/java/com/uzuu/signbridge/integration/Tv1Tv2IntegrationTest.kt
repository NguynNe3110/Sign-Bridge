package com.uzuu.signbridge.integration

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.uzuu.signbridge.core.result.ErrorCode
import com.uzuu.signbridge.data.local.AppDatabase
import com.uzuu.signbridge.data.local.dao.ConversationDao
import com.uzuu.signbridge.data.repository.ConversationRepositoryImpl
import com.uzuu.signbridge.domain.StubTranslation
import com.uzuu.signbridge.domain.model.*
import com.uzuu.signbridge.domain.usecase.*
import com.uzuu.signbridge.feature.chip.*
import com.uzuu.signbridge.feature.conversation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class Tv1Tv2IntegrationTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var database: AppDatabase
    private lateinit var storage: ConversationRepositoryImpl
    private val remote = StubTranslation()
    private val device = RecordingSpeakerRepository()
    private val store = ViewModelStore()

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        val app = RuntimeEnvironment.getApplication()
        app.deleteDatabase("tv1-tv2-test.db")
        database = AppDatabase(app, "tv1-tv2-test.db")
        storage = ConversationRepositoryImpl(ConversationDao(database), Dispatchers.Unconfined)
    }
    @After fun cleanup() {
        store.clear()
        database.close()
        RuntimeEnvironment.getApplication().deleteDatabase("tv1-tv2-test.db")
        Dispatchers.resetMain()
    }
    private fun viewModel(): ConversationViewModel =
        ConversationViewModel(ServiceContext.CAFE, storage, device,
            SendChipsUseCase(storage, remote), RecognizeSignUseCase(storage, remote),
            SummarizeSpeechUseCase(storage, remote), SpeakQuickReplyUseCase(storage, device))
            .also { store.put("conversation", it) }

    @Test fun tv1OfflineSendUsesRealSqliteOnceAndSurvivesReopen() = runTest(dispatcher) {
        remote.expandAction = { throw BridgeException(BridgeError.NETWORK, "offline") }
        val vm = viewModel()
        val chip = vm.uiState.value.chips.first()
        vm.onEvent(ConversationUiEvent.ChipToggled(chip.label))
        vm.onEvent(ConversationUiEvent.SendChipsClicked)
        assertEquals(chip.label, vm.uiState.value.spokenSentence)
        assertTrue(vm.uiState.value.isOffline)
        assertTrue(device.spoken.isEmpty())
        assertEquals(1, storage.getHistory().size)
        assertEquals(1, storage.getChips(ServiceContext.CAFE).single { it.id == chip.id }.usageCount)
        store.clear()
        database.close()
        database = AppDatabase(RuntimeEnvironment.getApplication(), "tv1-tv2-test.db")
        val restored = ConversationRepositoryImpl(ConversationDao(database), Dispatchers.Unconfined)
        assertEquals(chip.label, restored.getHistory().single().processedText)
    }

    @Test fun chipEditorAndConversationObserveSameSqliteRepository() = runTest(dispatcher) {
        val vm = viewModel()
        val editor = ChipManageViewModel(ServiceContext.CAFE, storage).also { store.put("chips", it) }
        editor.onEvent(ChipManageUiEvent.NewLabelChanged("Không đá"))
        editor.onEvent(ChipManageUiEvent.AddClicked)
        val chip = vm.uiState.value.chips.single { it.label == "Không đá" }
        assertTrue(chip.isPersonal)
        editor.onEvent(ChipManageUiEvent.DeleteClicked(chip.id))
        assertFalse(vm.uiState.value.chips.any { it.id == chip.id })
    }

    @Test fun ttsFailureKeepsSingleTurnAndSingleUsageUpdate() = runTest(dispatcher) {
        device.speechError = BridgeException(BridgeError.DEVICE, "no Vietnamese voice")
        val vm = viewModel()
        val chip = vm.uiState.value.chips.first()
        vm.onEvent(ConversationUiEvent.ChipToggled(chip.label))
        vm.onEvent(ConversationUiEvent.SendChipsClicked)
        assertEquals(ErrorCode.DEVICE_ERROR, vm.uiState.value.staffError)
        assertFalse(vm.uiState.value.isBusy)
        assertEquals(1, storage.getHistory().size)
        assertEquals(1, storage.getChips(ServiceContext.CAFE).single { it.id == chip.id }.usageCount)
    }

    @Test fun leavingScreenCancelsPendingApiWithoutSavingOfflineOrIncrementingUsage() = runTest(dispatcher) {
        val pending = CompletableDeferred<String>()
        remote.expandAction = { pending.await() }
        val vm = viewModel()
        val chip = vm.uiState.value.chips.first()
        vm.onEvent(ConversationUiEvent.ChipToggled(chip.label))
        vm.onEvent(ConversationUiEvent.SendChipsClicked)
        vm.onEvent(ConversationUiEvent.ScreenStopped)
        pending.complete("Late result")
        assertFalse(vm.uiState.value.isBusy)
        assertTrue(storage.getHistory().isEmpty())
        assertEquals(0, storage.getChips(ServiceContext.CAFE).single { it.id == chip.id }.usageCount)
        assertTrue(device.cancelled)
    }
}
