package com.uzuu.signbridge.data.repository

import android.content.SharedPreferences
import com.uzuu.signbridge.domain.model.BridgeError
import com.uzuu.signbridge.domain.model.BridgeException
import com.uzuu.signbridge.domain.model.ServerAddress
import com.uzuu.signbridge.domain.repository.ServerSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class ServerSettingsRepositoryImpl(
    private val preferences: SharedPreferences,
    private val probe: suspend (ServerAddress) -> Boolean,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ServerSettingsRepository {
    private val mutableAddress = MutableStateFlow(load())
    override val address = mutableAddress.asStateFlow()

    private fun load(): ServerAddress? = runCatching {
        ServerAddress.parse(preferences.getString("ipv4", "").orEmpty(),
            preferences.getInt("port", 8080).toString(), preferences.getBoolean("https", false))
    }.getOrNull()

    override suspend fun save(address: ServerAddress) = withContext(io) {
        val saved = preferences.edit().putString("ipv4", address.ipv4).putInt("port", address.port)
            .putBoolean("https", address.useHttps).commit()
        if (!saved) throw BridgeException(BridgeError.STORAGE, "Không lưu được địa chỉ máy chủ. Hãy thử lại.")
        mutableAddress.value = address
    }

    override suspend fun checkConnection(address: ServerAddress) = probe(address)
}
