package com.uzuu.signbridge.data.remote.api

import com.uzuu.signbridge.domain.model.BridgeError
import com.uzuu.signbridge.domain.model.BridgeException
import com.uzuu.signbridge.domain.model.ServerAddress
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

class BridgeApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val allowHttp: Boolean,
    private val baseUrlProvider: () -> String = { baseUrl },
    private val allowLocalHttp: Boolean = false,
    private val isDebug: Boolean = DEBUG_ENABLED,
    private val logger: ApiLogger = ApiLogger,
) {
    suspend fun execute(
        path: String,
        method: String = "GET",
        body: RequestBody? = null,
        headers: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val baseUrl = baseUrlProvider()
        if (baseUrl.isBlank()) {
            if (isDebug) logger.logConfigError("Chưa cấu hình server (baseUrl rỗng).")
            throw BridgeException(BridgeError.NOT_CONFIGURED, "Chưa cấu hình server. Bạn vẫn có thể gửi thẻ ở chế độ offline.")
        }
        val base = baseUrl.toHttpUrlOrNull()
        if (base == null) {
            if (isDebug) logger.logConfigError("Địa chỉ server không hợp lệ: $baseUrl")
            throw BridgeException(BridgeError.NOT_CONFIGURED, "Địa chỉ server không hợp lệ.")
        }
        val localHttp = allowLocalHttp && ServerAddress.isLocalIpv4(base.host)
        if ((!allowHttp && !localHttp && base.scheme != "https") || base.query != null || base.fragment != null ||
            base.username.isNotEmpty() || base.password.isNotEmpty()) {
            if (isDebug) logger.logConfigError("Server cần URL hợp lệ và HTTPS cho bản release: $baseUrl")
            throw BridgeException(BridgeError.NOT_CONFIGURED, "Server cần URL hợp lệ và HTTPS cho bản release.")
        }
        val url = base.resolve("api/$path") ?: base.newBuilder().addPathSegments("api/$path").build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("ngrok-skip-browser-warning", "true")
            .apply {
                headers.forEach { (k, v) -> header(k, v) }
                method(method, body)
            }
            .build()

        if (isDebug) {
            logger.logRequest(request.method, url.toString(), request.headers, body)
        }

        val startNs = System.nanoTime()
        try {
            client.newCall(request).await().use {
                val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
                val raw = it.body?.string().orEmpty()
                if (!it.isSuccessful) {
                    if (isDebug) {
                        logger.logResponse(request.method, url.toString(), it.code, it.message, tookMs, raw, isError = true)
                    }
                    if (it.header("Ngrok-Error-Code") != null) {
                        throw BridgeException(BridgeError.NETWORK, "Đường hầm ngrok của dịch vụ AI chưa sẵn sàng. Kiểm tra URL và bật lại dịch vụ.")
                    }
                    if (it.code == 404 && path == "sign") {
                        throw BridgeException(BridgeError.NOT_CONFIGURED,
                            "Dịch vụ này chưa cung cấp API nhận diện ký hiệu. Hãy dùng thẻ từ khóa.")
                    }
                    val code = runCatching {
                        val json = JSONObject(raw)
                        json.optJSONObject("error")?.optString("code")
                            ?: (json.opt("detail") as? String)
                    }.getOrNull()
                    val known = BridgeError.entries.firstOrNull { error -> error.name == code &&
                        error in API_ERRORS }
                    val fallback = when (it.code) {
                        413 -> BridgeError.PAYLOAD_TOO_LARGE
                        415 -> BridgeError.UNSUPPORTED_MEDIA_TYPE
                        408, 504 -> BridgeError.AI_TIMEOUT
                        in 400..499 -> BridgeError.INVALID_REQUEST
                        else -> BridgeError.AI_UPSTREAM_ERROR
                    }
                    val error = known ?: fallback
                    throw BridgeException(error, messageFor(error))
                }
                if (isDebug) {
                    logger.logResponse(request.method, url.toString(), it.code, it.message, tookMs, raw, isError = false)
                }
                raw
            }
        } catch (error: InterruptedIOException) {
            val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
            if (isDebug) logger.logFailure(request.method, url.toString(), tookMs, error)
            throw BridgeException(BridgeError.AI_TIMEOUT, "Kết nối quá thời hạn. Vui lòng dùng thẻ hoặc thử lại.", error)
        } catch (error: IOException) {
            val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
            if (isDebug) logger.logFailure(request.method, url.toString(), tookMs, error)
            throw BridgeException(BridgeError.NETWORK, "Không kết nối được server. Vui lòng kiểm tra mạng.", error)
        }
    }

    suspend fun request(
        path: String,
        body: RequestBody? = null,
        headers: Map<String, String> = emptyMap(),
    ): JSONObject {
        val raw = execute(path, if (body != null) "POST" else "GET", body, headers)
        return try {
            JSONObject(raw)
        } catch (error: Exception) {
            if (isDebug) logger.logFailure("REQUEST", path, 0, error)
            throw BridgeException(BridgeError.AI_UPSTREAM_ERROR, "Server trả dữ liệu JSON không hợp lệ.", error)
        }
    }

    suspend fun requestArray(
        path: String,
        headers: Map<String, String> = emptyMap(),
    ): JSONArray {
        val raw = execute(path, "GET", null, headers)
        return try {
            JSONArray(raw)
        } catch (error: Exception) {
            if (isDebug) logger.logFailure("GET", path, 0, error)
            throw BridgeException(BridgeError.AI_UPSTREAM_ERROR, "Server trả danh sách JSON không hợp lệ.", error)
        }
    }

    suspend fun delete(
        path: String,
        headers: Map<String, String> = emptyMap(),
    ): JSONObject {
        val raw = execute(path, "DELETE", null, headers)
        return try {
            JSONObject(raw)
        } catch (error: Exception) {
            if (isDebug) logger.logFailure("DELETE", path, 0, error)
            throw BridgeException(BridgeError.AI_UPSTREAM_ERROR, "Server trả dữ liệu JSON không hợp lệ.", error)
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                // Resume with cancellation cleanup so a response racing cancellation is closed.
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    private fun messageFor(code: BridgeError) = when (code) {
        BridgeError.INVALID_REQUEST -> "Dữ liệu gửi lên chưa hợp lệ."
        BridgeError.PAYLOAD_TOO_LARGE -> "Ảnh hoặc bản ghi âm vượt giới hạn."
        BridgeError.UNSUPPORTED_MEDIA_TYPE -> "Server không hỗ trợ định dạng ảnh hoặc âm thanh."
        BridgeError.NO_SIGN_DETECTED -> "Chưa nhận được ký hiệu. Hãy thử lại hoặc dùng thẻ."
        BridgeError.NO_SPEECH_DETECTED -> "Chưa nghe rõ lời nói. Hãy giữ mic và nói lại."
        BridgeError.AI_TIMEOUT -> "Dịch vụ AI phản hồi quá chậm. Hãy dùng thẻ hoặc thử lại."
        else -> "Dịch vụ AI hiện chưa xử lý được yêu cầu. Hãy dùng thẻ hoặc thử lại."
    }

    companion object {
        /**
         * Cờ [DEBUG] bật/tắt ghi log API toàn cục.
         * Mặc định theo ApiLogger.isDebugEnabled (tức BuildConfig.DEBUG).
         */
        var DEBUG_ENABLED: Boolean
            get() = ApiLogger.isDebugEnabled
            set(value) { ApiLogger.isDebugEnabled = value }

        private val API_ERRORS = setOf(BridgeError.INVALID_REQUEST, BridgeError.PAYLOAD_TOO_LARGE,
            BridgeError.UNSUPPORTED_MEDIA_TYPE, BridgeError.NO_SIGN_DETECTED,
            BridgeError.NO_SPEECH_DETECTED, BridgeError.AI_UPSTREAM_ERROR, BridgeError.AI_TIMEOUT)
    }
}

