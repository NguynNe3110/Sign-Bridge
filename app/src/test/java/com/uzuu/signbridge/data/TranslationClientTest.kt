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

    @Test fun fastApiNoSpeechDetailRemainsTyped() = runBlocking {
        server.enqueue(response("""{"detail":"NO_SPEECH_DETECTED"}""", 422))
        try {
            repo.summarize(ServiceContext.PHARMACY, RecordedAudio(byteArrayOf(1), 1000), null)
            fail("Expected no-speech error")
        } catch (error: BridgeException) { assertEquals(BridgeError.NO_SPEECH_DETECTED, error.code) }
    }

    @Test fun directAiLongQuickReplyAndCompleteSummaryArePreserved() = runBlocking {
        val summary = "Một trà sữa trân châu ít đường và năm mươi phần trăm đá, tổng cộng ba mươi lăm nghìn đồng."
        val reply = "Của em hết bao nhiêu tiền vậy?"
        val json = JSONObject().put("transcript", summary).put("summary", summary)
            .put("highlights", org.json.JSONArray()).put("iconCode", "DRINK")
            .put("quickReplies", org.json.JSONArray(listOf("Dạ em lấy món này ạ", reply, "Chờ em một chút nhé")))
        server.enqueue(response(json.toString()))
        val result = repo.summarize(ServiceContext.CAFE, RecordedAudio(byteArrayOf(1), 1000), null)
        assertEquals(summary, result.summary)
        assertEquals(reply, result.quickReplies[1])
    }

    @Test fun offlineNgrokIsNetworkErrorInsteadOfInvalidRequest() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setHeader("Ngrok-Error-Code", "ERR_NGROK_3200")
            .setBody("The endpoint is offline."))
        try { repo.checkHealth(); fail("Expected network error")
        } catch (error: BridgeException) { assertEquals(BridgeError.NETWORK, error.code) }
    }

    @Test fun unavailableSignEndpointSuggestsChipsInsteadOfInvalidInput() = runBlocking {
        server.enqueue(response("""{"detail":"Not Found"}""", 404))
        try {
            repo.recognizeSign(ServiceContext.CAFE, List(4) { SignFrame(byteArrayOf(1), 640, 480) }, emptyList(), null)
            fail("Expected unavailable endpoint")
        } catch (error: BridgeException) { assertEquals(BridgeError.NOT_CONFIGURED, error.code) }
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

    @Test fun mockFallbackReturnsRealisticDataWhenServerUnavailable() = runBlocking {
        val repoWithFallback = TranslationRepositoryImpl(
            BridgeApi(client, server.url("/").toString(), true),
            enableMockFallback = true,
        )
        // Simulate server returning 504 Gateway Timeout (AI_TIMEOUT)
        server.enqueue(MockResponse().setResponseCode(504))
        val expanded = repoWithFallback.expand(ServiceContext.CAFE, listOf("Cà phê sữa"), null)
        assertTrue(expanded.contains("Cà phê sữa"))

        // Simulate server returning 404 (NOT_CONFIGURED)
        server.enqueue(MockResponse().setResponseCode(404))
        val summary = repoWithFallback.summarize(ServiceContext.CAFE, RecordedAudio(byteArrayOf(1, 2), 1000), null)
        assertEquals("DRINK", summary.iconCode)
        assertTrue(summary.quickReplies.isNotEmpty())
    }

    @Test fun signNeverFallsBackToMockSentenceWhenServerFails() = runBlocking {
        val repoWithFallback = TranslationRepositoryImpl(
            BridgeApi(client, server.url("/").toString(), true),
            enableMockFallback = true,
        )
        val frames = List(4) { SignFrame(byteArrayOf(it.toByte()), 640, 480) }
        val failures = listOf(
            MockResponse().setResponseCode(502) to BridgeError.AI_UPSTREAM_ERROR,
            response("""{"error":{"code":"AI_TIMEOUT","message":"test"}}""", 504) to BridgeError.AI_TIMEOUT,
            response("""{"glosses":"ĐAU"}""") to BridgeError.AI_UPSTREAM_ERROR,
        )
        failures.forEach { (reply, expected) ->
            server.enqueue(reply)
            try {
                repoWithFallback.recognizeSign(ServiceContext.CAFE, frames, emptyList(), null)
                fail("Expected $expected, not a mock sentence")
            } catch (error: BridgeException) { assertEquals(expected, error.code) }
        }
    }

    @Test fun signKeepsRecognitionWhenServerSuggestionsAreOutsideContract() = runBlocking {
        val frames = List(4) { SignFrame(byteArrayOf(it.toByte()), 640, 480) }
        server.enqueue(response("""{"glosses":["CÀ PHÊ"],"confidence":0.9,"spoken_sentence":"Cho mình một cà phê nhé.","suggestedChips":["Cà phê"]}"""))
        val few = repo.recognizeSign(ServiceContext.CAFE, frames, emptyList(), null)
        assertFalse(few.needsConfirmation)
        assertEquals("Cho mình một cà phê nhé.", few.sentence)
        assertEquals(listOf("Cà phê"), few.suggestedChips)

        server.enqueue(response("""{"glosses":["UNKNOWN","NGÀY"],"confidence":0.4,"spoken_sentence":"","suggestedChips":["Một ngày"," ","Hai ngày","Một ngày","A","B","C","D","E"]}"""))
        val many = repo.recognizeSign(ServiceContext.CAFE, frames, emptyList(), null)
        assertTrue(many.needsConfirmation)
        assertEquals(listOf("Một ngày", "Hai ngày", "A", "B", "C", "D"), many.suggestedChips)
    }

    @Test fun deviceGlossesUseJsonContractAndKeepModelLabels() = runBlocking {
        server.enqueue(response("""{"glosses":["ĐAU BỤNG","HIỆU THUỐC"],"confidence":0.81,"spoken_sentence":"Mình bị đau bụng, muốn tìm hiệu thuốc ạ.","suggestedChips":["đau bụng","hiệu thuốc"]}"""))
        val result = repo.composeSignGlosses(ServiceContext.PHARMACY,
            listOf(GlossPrediction("đau bụng", 0.93f), GlossPrediction("hiệu thuốc", 0.81f)),
            listOf("Dị ứng penicillin"), "Bạn cần gì?")
        assertFalse(result.needsConfirmation)
        assertEquals(listOf("ĐAU BỤNG", "HIỆU THUỐC"), result.glosses)
        assertEquals("Mình bị đau bụng, muốn tìm hiệu thuốc ạ.", result.sentence)
        val request = server.takeRequest()
        assertEquals("/api/sign/glosses", request.path)
        assertEquals("POST", request.method)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("PHARMACY", body.getString("context"))
        assertEquals("đau bụng", body.getJSONArray("glosses").getString(0))
        assertEquals(0.81, body.getJSONArray("confidences").getDouble(1), 1e-6)
        assertEquals("Dị ứng penicillin", body.getJSONArray("personalChips").getString(0))
        assertEquals("Bạn cần gì?", body.getString("previousIncoming"))
    }

    @Test fun deviceGlossesUnknownBlanksSentenceAndOldServerIsNotConfigured() = runBlocking {
        server.enqueue(response("""{"glosses":["UNKNOWN"],"confidence":0.69,"spoken_sentence":"Không được đọc.","suggestedChips":["hiệu thuốc"]}"""))
        val unsure = repo.composeSignGlosses(ServiceContext.PHARMACY,
            listOf(GlossPrediction("hiệu thuốc", 0.42f)), emptyList(), null)
        assertTrue(unsure.needsConfirmation)
        assertEquals("", unsure.sentence)
        assertTrue(JSONObject(server.takeRequest().body.readUtf8()).isNull("previousIncoming"))

        server.enqueue(response("""{"detail":"Not Found"}""", 404))
        try {
            repo.composeSignGlosses(ServiceContext.PHARMACY, listOf(GlossPrediction("sốt", 0.9f)), emptyList(), null)
            fail("Expected unavailable endpoint")
        } catch (error: BridgeException) { assertEquals(BridgeError.NOT_CONFIGURED, error.code) }
    }

    @Test fun signOverlongSentenceIsNotSpokenButGlossesRemain() = runBlocking {
        val longSentence = List(41) { "từ" }.joinToString(" ")
        server.enqueue(response("""{"glosses":["CÀ PHÊ"],"confidence":0.95,"spoken_sentence":"$longSentence","suggestedChips":["Cà phê","Đá","Mang đi"]}"""))
        val result = repo.recognizeSign(ServiceContext.CAFE,
            List(4) { SignFrame(byteArrayOf(it.toByte()), 640, 480) }, emptyList(), null)
        assertTrue(result.needsConfirmation)
        assertEquals("", result.sentence)
        assertEquals(listOf("CÀ PHÊ"), result.glosses)
    }
}

