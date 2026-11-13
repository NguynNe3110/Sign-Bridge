package com.uzuu.signbridge.feature

import com.uzuu.signbridge.MainDispatcherRule
import com.uzuu.signbridge.domain.model.ServerAddress
import com.uzuu.signbridge.domain.repository.ServerSettingsRepository
import com.uzuu.signbridge.feature.server.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ServerViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val initial = ServerAddress.parse("192.168.1.10", "8080")
    private val repository = object : ServerSettingsRepository {
        override val address = MutableStateFlow<ServerAddress?>(initial)
        var checks = 0
        var saveFailure = false
        var check: suspend () -> Boolean = { true }
        override suspend fun save(address: ServerAddress) {
            if (saveFailure) error("Disk unavailable")
            this.address.value = address
        }
        override suspend fun checkConnection(address: ServerAddress): Boolean { checks++; return check() }
    }
    @Test fun checkingDraftDoesNotApplyIt() {
        val vm = ServerViewModel(repository)
        vm.onEvent(ServerUiEvent.IpChanged("192.168.1.20"))
        vm.onEvent(ServerUiEvent.CheckConnection)
        assertEquals(initial, repository.address.value)
        assertEquals(initial.baseUrl, vm.state.value.savedUrl)
        assertFalse(vm.state.value.isError)
    }
    @Test fun savesEvenWhenBackendIsOffline() {
        repository.check = { throw java.io.IOException("Server is offline") }
        val vm = ServerViewModel(repository)
        vm.onEvent(ServerUiEvent.IpChanged("192.168.1.20"))
        vm.onEvent(ServerUiEvent.SaveAndConnect)
        assertEquals("192.168.1.20", repository.address.value?.ipv4)
        assertTrue(vm.state.value.isError)
        assertTrue(vm.state.value.message.orEmpty().startsWith("Đã lưu"))
        assertFalse(vm.state.value.isBusy)
    }
    @Test fun invalidInputAndFailedSaveDoNotReplaceWorkingAddress() {
        val vm = ServerViewModel(repository)
        vm.onEvent(ServerUiEvent.IpChanged("999.1.2.3"))
        vm.onEvent(ServerUiEvent.SaveAndConnect)
        assertEquals(0, repository.checks)
        assertEquals(initial, repository.address.value)
        vm.onEvent(ServerUiEvent.IpChanged("192.168.1.20"))
        repository.saveFailure = true
        vm.onEvent(ServerUiEvent.SaveAndConnect)
        assertEquals(initial, repository.address.value)
        assertEquals(0, repository.checks)
        assertTrue(vm.state.value.isError)
    }
    @Test fun doubleTapCannotStartDuplicateRequests() {
        val pending = CompletableDeferred<Boolean>()
        repository.check = { pending.await() }
        val vm = ServerViewModel(repository)
        vm.onEvent(ServerUiEvent.CheckConnection)
        vm.onEvent(ServerUiEvent.CheckConnection)
        assertTrue(vm.state.value.isBusy)
        assertEquals(1, repository.checks)
        pending.complete(true)
        assertFalse(vm.state.value.isBusy)
    }
}
