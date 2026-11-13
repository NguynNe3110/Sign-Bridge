package com.uzuu.signbridge.data

import android.app.Application
import android.content.Context
import com.uzuu.signbridge.data.repository.ServerSettingsRepositoryImpl
import com.uzuu.signbridge.domain.model.ServerAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ServerSettingsTest {
    @Test fun persistsAndRestoresSavedEndpointWithoutSavingProbeDraft() = runBlocking {
        val preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences("test_server_connection", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val first = ServerSettingsRepositoryImpl(preferences, { true }, Dispatchers.Unconfined)
        val saved = ServerAddress.parse("192.168.1.10", "8080")
        first.save(saved)
        first.checkConnection(ServerAddress.parse("192.168.1.20", "9090"))
        val restored = ServerSettingsRepositoryImpl(preferences, { true }, Dispatchers.Unconfined)
        assertEquals(saved, restored.address.value)
        preferences.edit().clear().commit()
        Unit
    }
}
