package com.uzuu.signbridge.data

import android.app.Application
import com.uzuu.signbridge.data.remote.api.BridgeApi
import com.uzuu.signbridge.domain.model.BridgeError
import com.uzuu.signbridge.domain.model.BridgeException
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BridgeApiTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var api: BridgeApi

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build()
        api = BridgeApi(client, server.url("/").toString(), allowHttp = true)
    }

    @After
    fun cleanup() {
        client.connectionPool.evictAll()
        server.shutdown()
        client.dispatcher.executorService.shutdown()
    }

    private fun jsonResponse(body: String, status: Int = 200) =
        MockResponse().setResponseCode(status)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    @Test
    fun requestArrayReturnsJsonArrayAndSendsCustomHeaders() = runBlocking {
        server.enqueue(jsonResponse("""[{"id":"1","text":"Cà phê"},{"id":"2","text":"Trà"}]"""))

        val array = api.requestArray("chips?context=CAFE", mapOf("X-User-Id" to "user-999"))
        assertEquals(2, array.length())
        assertEquals("Cà phê", array.getJSONObject(0).getString("text"))

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/chips?context=CAFE", recorded.path)
        assertEquals("user-999", recorded.getHeader("X-User-Id"))
        assertEquals("true", recorded.getHeader("ngrok-skip-browser-warning"))
    }

    @Test
    fun deleteSendsDeleteMethodAndParsesResponse() = runBlocking {
        server.enqueue(jsonResponse("""{"success":true}"""))

        val res = api.delete("chips/chip-123?user_id=user-999", mapOf("X-User-Id" to "user-999"))
        assertEquals(true, res.getBoolean("success"))

        val recorded = server.takeRequest()
        assertEquals("DELETE", recorded.method)
        assertEquals("/api/chips/chip-123?user_id=user-999", recorded.path)
        assertEquals("user-999", recorded.getHeader("X-User-Id"))
    }

    @Test
    fun httpErrorsMapToBridgeException() = runBlocking {
        server.enqueue(jsonResponse("""{"detail":"Thẻ không tồn tại"}""", 404))

        try {
            api.delete("chips/invalid-id")
            fail("Expected BridgeException")
        } catch (e: BridgeException) {
            assertEquals(BridgeError.INVALID_REQUEST, e.code)
        }
    }
}
