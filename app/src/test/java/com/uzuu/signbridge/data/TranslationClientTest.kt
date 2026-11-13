package com.uzuu.signbridge.data

import android.app.Application
import com.uzuu.signbridge.data.remote.api.BridgeApi
import com.uzuu.signbridge.data.repository.TranslationRepositoryImpl
import com.uzuu.signbridge.domain.model.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class TranslationClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var repo: TranslationRepositoryImpl

    @Before fun setup() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build()
        repo = TranslationRepositoryImpl(BridgeApi(client, server.url("/").toString(), true))
    }
    @After fun cleanup() {
        client.connectionPool.evictAll()
        server.shutdown()
        client.dispatcher.executorService.shutdown()
    }
    private fun response(json: String, status: Int = 200) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(json)

    @Test fun expansionUsesUtf8JsonAndConversationContext() = runBlocking {
        server.enqueue(response("""{"spoken_sentence":"Cho mình cà phê sữa."}"""))
        assertEquals("Cho mình cà phê sữa.", repo.expand(ServiceContext.CAFE, listOf("Cà phê sữa"), "Bạn uống gì?"))
        val request = server.takeRequest()
        assertEquals("/api/expand", request.path)
        assertEquals("POST", request.method)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("CAFE", body.getString("context"))
        assertEquals("Cà phê sữa", body.getJSONArray("chips").getString(0))
        assertEquals("Bạn uống gì?", body.getString("previousIncoming"))
    }

    @Test fun signUsesOrderedMultipartAndSuppressesUnlistedGloss() = runBlocking {
        server.enqueue(response("""{"glosses":["ĐAU","bịa thêm"],"confidence":0.99,"spoken_sentence":"Không được đọc.","suggestedChips":["Đau đầu","Có","Không"]}"""))
        val result = repo.recognizeSign(ServiceContext.PHARMACY,
            List(4) { SignFrame(byteArrayOf(it.toByte()), 640, 480) }, listOf("Dị ứng"), "Đau ở đâu?")
        assertTrue(result.needsConfirmation)
        assertEquals(listOf("ĐAU", "UNKNOWN"), result.glosses)
        assertEquals("", result.sentence)
        val request = server.takeRequest()
        assertEquals("/api/sign", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"context\""))
        assertTrue(body.contains("PHARMACY"))
        assertTrue(body.contains("name=\"personalChips\""))
        (0..3).forEach { assertTrue(body.contains("filename=\"frame_$it.jpg\"")) }
        assertTrue(body.indexOf("frame_0.jpg") < body.indexOf("frame_3.jpg"))
    }

    @Test fun audioUsesM4aAndDropsHighlightsNotInSummary() = runBlocking {
        server.enqueue(response("""{"transcript":"Giá 35.000đ.","summary":"Giá 35.000đ.","highlights":[{"type":"PRICE","text":"35.000đ"},{"type":"OTHER","text":"không có"}],"iconCode":"MONEY","quickReplies":["Đồng ý","Cảm ơn","Không"]}"""))
        val result = repo.summarize(ServiceContext.CAFE, RecordedAudio(byteArrayOf(1, 2), 1000), "Hỏi giá")
        assertEquals(1, result.highlights.size)
        assertEquals("35.000đ", result.highlights.single().text)
        val request = server.takeRequest()
        assertEquals("/api/summarize", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"audio\"; filename=\"reply.m4a\""))
        assertTrue(body.contains("Content-Type: audio/mp4"))
        assertTrue(body.contains("name=\"previousOutgoing\""))
    }

    @Test fun allDocumentedErrorsRemainTyped() = runBlocking {
        val errors = mapOf(
            BridgeError.INVALID_REQUEST to 400, BridgeError.PAYLOAD_TOO_LARGE to 413,
            BridgeError.UNSUPPORTED_MEDIA_TYPE to 415, BridgeError.NO_SIGN_DETECTED to 422,
            BridgeError.NO_SPEECH_DETECTED to 422, BridgeError.AI_UPSTREAM_ERROR to 502,
            BridgeError.AI_TIMEOUT to 504,
        )
        errors.forEach { (code, status) ->
            server.enqueue(response("""{"error":{"code":"${code.name}","message":"test"}}""", status))
            try { repo.checkHealth(); fail("Expected $code")
            } catch (error: BridgeException) { assertEquals(code, error.code) }
        }
    }

    @Test fun malformedSuccessAndInvalidConfidenceAreRejected() = runBlocking {
        server.enqueue(response("""{"spoken_sentence":123}"""))
        try { repo.expand(ServiceContext.CAFE, listOf("Cà phê"), null); fail("Expected schema error")
        } catch (error: BridgeException) { assertEquals(BridgeError.AI_UPSTREAM_ERROR, error.code) }
        server.enqueue(response("""{"glosses":["CÓ"],"confidence":2,"spoken_sentence":"Có","suggestedChips":["Có","Không","Cảm ơn"]}"""))
        try { repo.recognizeSign(ServiceContext.CAFE, List(4) { SignFrame(byteArrayOf(1), 640, 480) }, emptyList(), null)
            fail("Expected confidence validation")
        } catch (error: BridgeException) { assertEquals(BridgeError.AI_UPSTREAM_ERROR, error.code) }
    }

    @Test fun bodyReadTimeoutIsTypedForOfflineFallback() = runBlocking {
        val shortClient = client.newBuilder().readTimeout(50, TimeUnit.MILLISECONDS).build()
        val shortRepo = TranslationRepositoryImpl(BridgeApi(shortClient, server.url("/").toString(), true))
        server.enqueue(response("""{"spoken_sentence":"Cảm ơn."}""").setBodyDelay(300, TimeUnit.MILLISECONDS))
        try { shortRepo.expand(ServiceContext.CAFE, listOf("Cảm ơn"), null); fail("Expected timeout")
        } catch (error: BridgeException) {
            assertEquals(BridgeError.AI_TIMEOUT, error.code)
            assertTrue(error.allowsOfflineChips)
        }
    }

    @Test fun releaseRejectsCleartextAndMissingConfigurationDoesNotCallNetwork() = runBlocking {
        val secureRepo = TranslationRepositoryImpl(BridgeApi(client, server.url("/").toString(), false))
        val blankRepo = TranslationRepositoryImpl(BridgeApi(client, "", true))
        for (repository in listOf(secureRepo, blankRepo)) {
            try { repository.checkHealth(); fail("Expected config error")
            } catch (error: BridgeException) { assertEquals(BridgeError.NOT_CONFIGURED, error.code) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun cancellingRequestPropagatesCancellation() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val pending = async(Dispatchers.IO) { repo.expand(ServiceContext.CAFE, listOf("Cà phê"), null) }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        pending.cancelAndJoin()
        assertTrue(pending.isCancelled)
    }
}

