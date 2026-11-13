package com.uzuu.signbridge.data

import android.app.Application
import com.uzuu.signbridge.data.remote.api.BridgeApi
import com.uzuu.signbridge.data.repository.TranslationRepositoryImpl
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ServerEndpointClientTest {
    @Test fun sameClientRoutesNextRequestToNewServerWithoutRecreation() = runBlocking {
        val first = MockWebServer()
        val second = MockWebServer()
        first.start(); second.start()
        val client = OkHttpClient()
        try {
            var active = first.url("/").toString()
            val repository = TranslationRepositoryImpl(BridgeApi(client, "", false,
                baseUrlProvider = { active }, allowLocalHttp = true))
            first.enqueue(MockResponse().setBody("""{"status":"UP"}"""))
            assertTrue(repository.checkHealth())
            assertEquals("/api/health", first.takeRequest().path)
            active = second.url("/").toString()
            second.enqueue(MockResponse().setBody("""{"status":"UP"}"""))
            assertTrue(repository.checkHealth())
            assertEquals("/api/health", second.takeRequest().path)
            assertEquals(1, first.requestCount)
            assertEquals(1, second.requestCount)
        } finally {
            first.shutdown(); second.shutdown()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }
}
