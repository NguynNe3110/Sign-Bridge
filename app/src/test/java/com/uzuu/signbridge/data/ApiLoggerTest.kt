package com.uzuu.signbridge.data

import android.app.Application
import com.uzuu.signbridge.data.remote.api.ApiLogger
import com.uzuu.signbridge.data.remote.api.BridgeApi
import com.uzuu.signbridge.data.repository.TranslationRepositoryImpl
import com.uzuu.signbridge.domain.model.BridgeException
import com.uzuu.signbridge.domain.model.RecordedAudio
import com.uzuu.signbridge.domain.model.ServiceContext
import com.uzuu.signbridge.domain.model.SignFrame
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ApiLoggerTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private val recordedLogs = mutableListOf<Triple<Int, String, String>>()

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build()
        recordedLogs.clear()
        ApiLogger.writer = { priority, tag, message ->
            recordedLogs.add(Triple(priority, tag, message))
        }
    }

    @After
    fun cleanup() {
        client.connectionPool.evictAll()
        server.shutdown()
        client.dispatcher.executorService.shutdown()
    }

    @Test
    fun logsRequestAndResponseWithDebugTagWhenDebugEnabled() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"spoken_sentence":"Xin chào"}"""))
        val api = BridgeApi(client, server.url("/").toString(), allowHttp = true, isDebug = true)
        val repo = TranslationRepositoryImpl(api)

        val result = repo.expand(ServiceContext.CAFE, listOf("Cà phê"), "Bạn dùng gì?")
        assertEquals("Xin chào", result)

        val messages = recordedLogs.map { it.third }

        // Kiểm tra cờ và định dạng [DEBUG]
        assertTrue("Mọi dòng log phải có prefix [DEBUG]", messages.all { it.startsWith("[DEBUG]") })
        assertTrue("Mọi dòng log phải có tag [DEBUG]", recordedLogs.all { it.second == "[DEBUG]" })

        // Kiểm tra log khi gửi
        assertTrue("Phải log dòng GỬI API", messages.any { it.contains("--> [GỬI API] POST") && it.contains("/api/expand") })
        assertTrue("Phải log JSON body gửi đi", messages.any { it.contains("Body:") && it.contains("Cà phê") })

        // Kiểm tra log khi nhận
        assertTrue("Phải log dòng NHẬN API", messages.any { it.contains("<-- [NHẬN API] HTTP 200") && it.contains("/api/expand") })
        assertTrue("Phải log Response Body nhận về", messages.any { it.contains("Response Body:") && it.contains("Xin chào") })
    }

    @Test
    fun logsNothingWhenDebugDisabled() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"spoken_sentence":"Xin chào"}"""))
        val api = BridgeApi(client, server.url("/").toString(), allowHttp = true, isDebug = false)
        val repo = TranslationRepositoryImpl(api)

        repo.expand(ServiceContext.CAFE, listOf("Cà phê"), null)
        assertTrue("Khi tắt cờ debug, không được ghi log", recordedLogs.isEmpty())
    }

    @Test
    fun summarizesMultipartImagesAndAudioWithoutDumpingRawBytes() = runBlocking {
        // 1. Kiểm tra gửi frames ảnh
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"glosses":["XIN_CHAO"],"confidence":0.95,"spoken_sentence":"Xin chào","suggestedChips":["Cảm ơn","Có","Không"]}"""
        ))
        val api = BridgeApi(client, server.url("/").toString(), allowHttp = true, isDebug = true)
        val repo = TranslationRepositoryImpl(api)

        val dummyJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)
        repo.recognizeSign(ServiceContext.CAFE, List(4) { SignFrame(dummyJpeg, 640, 480) }, listOf("Gợi ý"), null)

        val signMessages = recordedLogs.map { it.third }
        assertTrue("Phải có tóm tắt số frame", signMessages.any { it.contains("[frames]: 4 frame(s)") })
        assertTrue("Phải hiển thị định dạng ảnh và kích thước tóm tắt", signMessages.any { it.contains("image/jpeg") })
        assertFalse("Tuyệt đối không in raw byte ảnh ra log", signMessages.any { it.contains("\uFFFD") || it.contains("JFIF") })

        // 2. Kiểm tra gửi audio
        recordedLogs.clear()
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"transcript":"Xin chào","summary":"Xin chào","highlights":[],"iconCode":"INFO","quickReplies":["Đồng ý","Cảm ơn","Không"]}"""
        ))
        val dummyAudio = RecordedAudio(ByteArray(1024) { 0x41 }, 1000)
        repo.summarize(ServiceContext.CAFE, dummyAudio, null)

        val audioMessages = recordedLogs.map { it.third }
        assertTrue("Phải log tóm tắt phần audio", audioMessages.any { it.contains("[audio]: filename=\"reply.m4a\"") && it.contains("audio/mp4") })
    }

    @Test
    fun logsHttpErrorsAndApiFailuresWithDebugFlag() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"detail":"NO_SPEECH_DETECTED"}"""))
        val api = BridgeApi(client, server.url("/").toString(), allowHttp = true, isDebug = true)
        val repo = TranslationRepositoryImpl(api)

        val dummyAudio = RecordedAudio(ByteArray(100), 500)
        try {
            repo.summarize(ServiceContext.CAFE, dummyAudio, null)
            fail("Expected exception")
        } catch (_: BridgeException) { }

        val messages = recordedLogs.map { it.third }
        assertTrue("Phải log khi nhận API lỗi 422", messages.any { it.contains("[NHẬN API LỖI] HTTP 422") })
        assertTrue("Phải log nội dung lỗi", messages.any { it.contains("NO_SPEECH_DETECTED") })
    }

    @Test
    fun globalDebugFlagTogglesLogging() = runBlocking {
        val original = BridgeApi.DEBUG_ENABLED
        try {
            // Tắt cờ toàn cục
            BridgeApi.DEBUG_ENABLED = false
            assertFalse(ApiLogger.isDebugEnabled)

            val api = BridgeApi(client, server.url("/").toString(), allowHttp = true)
            val repo = TranslationRepositoryImpl(api)

            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"UP"}"""))
            repo.checkHealth()
            assertTrue("Khi BridgeApi.DEBUG_ENABLED = false, không có log", recordedLogs.isEmpty())

            // Bật cờ toàn cục
            BridgeApi.DEBUG_ENABLED = true
            assertTrue(ApiLogger.isDebugEnabled)

            val api2 = BridgeApi(client, server.url("/").toString(), allowHttp = true)
            val repo2 = TranslationRepositoryImpl(api2)

            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"UP"}"""))
            repo2.checkHealth()
            assertFalse("Khi BridgeApi.DEBUG_ENABLED = true, phải có log", recordedLogs.isEmpty())
            assertTrue(recordedLogs.any { it.third.contains("[NHẬN API] HTTP 200") })
        } finally {
            BridgeApi.DEBUG_ENABLED = original
        }
    }
}
