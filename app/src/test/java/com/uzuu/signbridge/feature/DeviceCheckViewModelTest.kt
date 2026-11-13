package com.uzuu.signbridge.feature

import com.uzuu.signbridge.domain.*
import com.uzuu.signbridge.domain.usecase.*
import com.uzuu.signbridge.domain.model.*
import com.uzuu.signbridge.domain.repository.ConversationRepository
import com.uzuu.signbridge.feature.devicecheck.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceCheckViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun backgroundCancelsPendingApiWithoutSavingFallbackOrAcceptingDoubleSubmit() = runTest(dispatcher) {
        val local = MemoryConversation()
        val pending = CompletableDeferred<String>()
        val remote = StubTranslation().apply { expandAction = { pending.await() } }
        val device = StubDevice()
        val vm = DeviceCheckViewModel(local, remote, device, SendChipsUseCase(local, remote),
            RecognizeSignUseCase(local, remote), SummarizeSpeechUseCase(local, remote),
            SpeakQuickReplyUseCase(local, device))
        advanceUntilIdle()
        vm.onEvent(DeviceCheckUiEvent.ChipToggled("coffee"))
        vm.onEvent(DeviceCheckUiEvent.SendChips)
        vm.onEvent(DeviceCheckUiEvent.SendChips)
        runCurrent()
        assertEquals(1, remote.calls)
        assertEquals(DeviceOperation.PROCESSING, vm.state.value.operation)
        vm.onEvent(DeviceCheckUiEvent.Backgrounded)
        runCurrent()
        pending.complete("Câu phản hồi đến muộn")
        advanceUntilIdle()
        assertEquals(DeviceOperation.IDLE, vm.state.value.operation)
        assertTrue(local.turns.isEmpty())
        assertTrue(device.cancelled)
        assertEquals("", device.spoken)
    }

    @Test fun failedContextSwitchClearsOldSessionAndAllowsRetry() = runTest(dispatcher) {
        val local = MemoryConversation()
        var failPublic = true
        val repository = object : ConversationRepository by local {
            override suspend fun getChips(context: ServiceContext): List<QuickChip> {
                if (context == ServiceContext.PUBLIC && failPublic) {
                    throw BridgeException(BridgeError.STORAGE, "storage unavailable")
                }
                return local.getChips(context)
            }
        }
        val remote = StubTranslation()
        val device = StubDevice()
        val vm = DeviceCheckViewModel(repository, remote, device, SendChipsUseCase(repository, remote),
            RecognizeSignUseCase(repository, remote), SummarizeSpeechUseCase(repository, remote),
            SpeakQuickReplyUseCase(repository, device))
        advanceUntilIdle()
        assertNotNull(vm.state.value.session)
        vm.onEvent(DeviceCheckUiEvent.ContextSelected(ServiceContext.PUBLIC))
        advanceUntilIdle()
        assertNull(vm.state.value.session)
        assertTrue(vm.state.value.chips.isEmpty())
        assertTrue(vm.state.value.isIdle)
        failPublic = false
        vm.onEvent(DeviceCheckUiEvent.RetryLoad)
        advanceUntilIdle()
        assertEquals(ServiceContext.PUBLIC, vm.state.value.session?.context)
    }
}

