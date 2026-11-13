package com.uzuu.signbridge.feature.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uzuu.signbridge.domain.model.ServerAddress
import com.uzuu.signbridge.domain.repository.ServerSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ServerViewModel(private val settings: ServerSettingsRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(settings.address.value.let {
        ServerUiState(ipv4 = it?.ipv4.orEmpty(), port = (it?.port ?: 8080).toString(),
            useHttps = it?.useHttps ?: false, savedUrl = it?.baseUrl)
    })
    val state = mutableState.asStateFlow()

    fun onEvent(event: ServerUiEvent) {
        if (state.value.isBusy) return
        when (event) {
            is ServerUiEvent.IpChanged -> mutableState.update { it.copy(ipv4 = event.value, message = null) }
            is ServerUiEvent.PortChanged -> mutableState.update { it.copy(port = event.value, message = null) }
            is ServerUiEvent.HttpsChanged -> mutableState.update { it.copy(useHttps = event.value, message = null) }
            ServerUiEvent.CheckConnection -> connect(save = false)
            ServerUiEvent.SaveAndConnect -> connect(save = true)
        }
    }

    private fun connect(save: Boolean) {
        val current = state.value
        val endpoint = try {
            ServerAddress.parse(current.ipv4, current.port, current.useHttps)
        } catch (error: IllegalArgumentException) {
            mutableState.update { it.copy(message = error.message, isError = true) }
            return
        }
        mutableState.update { it.copy(isBusy = true, message = if (save) "Đang lưu và kết nối…" else "Đang kiểm tra kết nối…", isError = false) }
        viewModelScope.launch {
            var saved = false
            try {
                if (save) {
                    settings.save(endpoint)
                    saved = true
                    mutableState.update { it.copy(savedUrl = endpoint.baseUrl, ipv4 = endpoint.ipv4) }
                }
                val up = settings.checkConnection(endpoint)
                mutableState.update { it.copy(
                    message = if (up) {
                        if (saved) "Đã lưu và kết nối thành công. App sẽ dùng máy chủ này."
                        else "Kết nối thành công. Chọn Lưu và kết nối để áp dụng."
                    } else {
                        if (saved) "Đã lưu địa chỉ, nhưng máy chủ chưa sẵn sàng (health chưa UP)."
                        else "Máy chủ chưa sẵn sàng (health chưa UP)."
                    }, isError = !up) }
            } catch (cancel: CancellationException) { throw cancel
            } catch (error: Exception) {
                mutableState.update { it.copy(isError = true, message =
                    (if (saved) "Đã lưu địa chỉ, nhưng chưa kết nối được. " else "") +
                    (error.message ?: "Không kết nối được. Kiểm tra IP, cổng và backend đang chạy.")) }
            } finally { mutableState.update { it.copy(isBusy = false) } }
        }
    }
}
