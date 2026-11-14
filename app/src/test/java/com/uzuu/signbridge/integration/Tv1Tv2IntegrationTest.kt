package com.uzuu.signbridge.integration

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.uzuu.signbridge.core.result.ErrorCode
import com.uzuu.signbridge.data.local.AppDatabase
import com.uzuu.signbridge.data.local.dao.ConversationDao
import com.uzuu.signbridge.data.repository.ConversationRepositoryImpl
import com.uzuu.signbridge.data.repository.TranslationRepositoryImpl
import com.uzuu.signbridge.data.remote.api.BridgeApi
import com.uzuu.signbridge.domain.StubTranslation
import com.uzuu.signbridge.domain.model.*
import com.uzuu.signbridge.domain.repository.TranslationRepository
import com.uzuu.signbridge.domain.usecase.*
import com.uzuu.signbridge.feature.chip.*
import com.uzuu.signbridge.feature.conversation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import java.util.concurrent.TimeUnit
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
    private fun viewModel(translation: TranslationRepository = remote): ConversationViewModel =
        ConversationViewModel(ServiceContext.CAFE, storage, device,
            SendChipsUseCase(storage, translation), RecognizeSignUseCase(storage, translation),
            ComposeSignUseCase(storage, translation),
            SummarizeSpeechUseCase(storage, translation), SpeakQuickReplyUseCase(storage, device))
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
        vm.onEvent(ConversationUiEvent.ChipToggled(chip.label))
        editor.onEvent(ChipManageUiEvent.EditClicked(chip.id))
        editor.onEvent(ChipManageUiEvent.NewLabelChanged("Thêm sữa hạt"))
        editor.onEvent(ChipManageUiEvent.SaveEditClicked)
        assertEquals(listOf("Thêm sữa hạt"), vm.uiState.value.selectedChips)
        editor.onEvent(ChipManageUiEvent.DeleteClicked(chip.id))
        assertFalse(vm.uiState.value.chips.any { it.id == chip.id })
        assertTrue(vm.uiState.value.selectedChips.isEmpty())
    }

    @Test fun localCreateAndEditSendCurrentUtf8ContentToRealHttpClientAfterReopen() = runTest(dispatcher) {
        val server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
        try {
            val translation = TranslationRepositoryImpl(BridgeApi(client, server.url("/").toString(), true))
            var vm = viewModel(translation)
            val editor = ChipManageViewModel(ServiceContext.CAFE, storage).also { store.put("chips", it) }
            editor.onEvent(ChipManageUiEvent.NewLabelChanged("Sữa yến mạch"))
            editor.onEvent(ChipManageUiEvent.AddClicked)
            val chip = vm.uiState.value.chips.single { it.label == "Sữa yến mạch" }

            suspend fun sendAndAssert(expected: String, sentence: String) {
                server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                    .setBody(JSONObject().put("spoken_sentence", sentence).toString()))
                vm.onEvent(ConversationUiEvent.SendChipsClicked)
                withContext(Dispatchers.IO) {
                    withTimeout(5_000) { vm.uiState.first { !it.isBusy } }
                    val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
                    assertEquals("POST", request.method)
                    assertEquals("/api/expand", request.path)
                    val json = JSONObject(request.body.readUtf8())
                    assertEquals("CAFE", json.getString("context"))
                    assertEquals(listOf(expected), json.getJSONArray("chips").let { array ->
                        (0 until array.length()).map(array::getString)
                    })
                }
                assertEquals(sentence, vm.uiState.value.spokenSentence)
                assertFalse(vm.uiState.value.isOffline)
                assertEquals(sentence, device.spoken.last())
            }

            vm.onEvent(ConversationUiEvent.ChipToggled(chip.label))
            sendAndAssert("Sữa yến mạch", "Cho mình sữa yến mạch.")
            vm.onEvent(ConversationUiEvent.ChipToggled(chip.label))
            editor.onEvent(ChipManageUiEvent.EditClicked(chip.id))
            editor.onEvent(ChipManageUiEvent.NewLabelChanged("Sữa hạt ít đá"))
            editor.onEvent(ChipManageUiEvent.SaveEditClicked)
            withContext(Dispatchers.IO) {
                withTimeout(5_000) {
                    editor.uiState.first { !it.isSaving }
                    vm.uiState.first { it.selectedChips == listOf("Sữa hạt ít đá") }
                }
            }
            assertNull(editor.uiState.value.inputError)
            assertEquals("Sữa hạt ít đá", storage.getChips(ServiceContext.CAFE).single { it.id == chip.id }.label)
            assertEquals(listOf("Sữa hạt ít đá"), vm.uiState.value.selectedChips)
            sendAndAssert("Sữa hạt ít đá", "Cho mình sữa hạt ít đá.")

            store.clear()
            database.close()
            database = AppDatabase(RuntimeEnvironment.getApplication(), "tv1-tv2-test.db")
            storage = ConversationRepositoryImpl(ConversationDao(database), Dispatchers.Unconfined)
            vm = viewModel(translation)
            withContext(Dispatchers.IO) {
                withTimeout(5_000) { vm.uiState.first { !it.isInitializing && it.chips.isNotEmpty() } }
            }
            val restored = vm.uiState.value.chips.single { it.id == chip.id }
            assertEquals("Sữa hạt ít đá", restored.label)
            assertEquals(2, restored.usageCount)
            vm.onEvent(ConversationUiEvent.ChipToggled(restored.label))
            sendAndAssert(restored.label, "Cho mình sữa hạt ít đá nhé.")
            // Card CRUD never makes network calls; only the three deliberate sends do.
            assertEquals(3, server.requestCount)
            assertEquals(3, storage.getChips(ServiceContext.CAFE).single { it.id == chip.id }.usageCount)
        } finally {
            client.connectionPool.evictAll()
            server.shutdown()
            client.dispatcher.executorService.shutdown()
        }
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
