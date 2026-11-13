package com.uzuu.signbridge.data.remote.api

import com.uzuu.signbridge.domain.model.BridgeError
import com.uzuu.signbridge.domain.model.BridgeException
import com.uzuu.signbridge.domain.model.ServerAddress
import java.io.IOException
import java.io.InterruptedIOException
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
import org.json.JSONObject

class BridgeApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val allowHttp: Boolean,
    private val baseUrlProvider: () -> String = { baseUrl },
    private val allowLocalHttp: Boolean = false,
) {
    suspend fun request(path: String, body: RequestBody? = null): JSONObject = withContext(Dispatchers.IO) {
        val baseUrl = baseUrlProvider()
        if (baseUrl.isBlank()) {
            throw BridgeException(BridgeError.NOT_CONFIGURED, "Chưa cấu hình server. Bạn vẫn có thể gửi thẻ ở chế độ offline.")
        }
        val base = baseUrl.toHttpUrlOrNull()
            ?: throw BridgeException(BridgeError.NOT_CONFIGURED, "Địa chỉ server không hợp lệ.")
        val localHttp = allowLocalHttp && ServerAddress.isLocalIpv4(base.host)
        if ((!allowHttp && !localHttp && base.scheme != "https") || base.query != null || base.fragment != null ||
            base.username.isNotEmpty() || base.password.isNotEmpty()) {
            throw BridgeException(BridgeError.NOT_CONFIGURED, "Server cần URL hợp lệ và HTTPS cho bản release.")
        }
        val url = base.newBuilder().addPathSegments("api/$path").build()
        val request = Request.Builder().url(url).header("Accept", "application/json").apply {
            if (body != null) post(body) else get()
        }.build()
        try { client.newCall(request).await().use {
            val raw = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val code = runCatching { JSONObject(raw).getJSONObject("error").getString("code") }.getOrNull()
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
            try { JSONObject(raw) } catch (error: Exception) {
                throw BridgeException(BridgeError.AI_UPSTREAM_ERROR, "Server trả dữ liệu JSON không hợp lệ.", error)
            }
        } } catch (error: InterruptedIOException) {
            throw BridgeException(BridgeError.AI_TIMEOUT, "Kết nối quá thời hạn. Vui lòng dùng thẻ hoặc thử lại.", error)
        } catch (error: IOException) {
            throw BridgeException(BridgeError.NETWORK, "Không kết nối được server. Vui lòng kiểm tra mạng.", error)
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

    private companion object {
        val API_ERRORS = setOf(BridgeError.INVALID_REQUEST, BridgeError.PAYLOAD_TOO_LARGE,
            BridgeError.UNSUPPORTED_MEDIA_TYPE, BridgeError.NO_SIGN_DETECTED,
            BridgeError.NO_SPEECH_DETECTED, BridgeError.AI_UPSTREAM_ERROR, BridgeError.AI_TIMEOUT)
    }
}

