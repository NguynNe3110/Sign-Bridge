package com.uzuu.signbridge.domain.repository

import com.uzuu.signbridge.domain.model.ServerAddress
import kotlinx.coroutines.flow.StateFlow

interface ServerSettingsRepository {
    val address: StateFlow<ServerAddress?>
    suspend fun save(address: ServerAddress)
    suspend fun checkConnection(address: ServerAddress): Boolean
}
