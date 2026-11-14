package com.uzuu.signbridge.data.remote.api

import android.util.Log
import com.uzuu.signbridge.BuildConfig
import okhttp3.Headers
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.Buffer
import java.util.Locale

interface ApiLogger {
    fun logRequest(method: String, url: String, headers: Headers?, body: RequestBody?)
    fun logResponse(
        method: String,
        url: String,
        code: Int,
        message: String,
        tookMs: Long,
        bodyString: String?,
        isError: Boolean = false,
    )
    fun logFailure(method: String, url: String, tookMs: Long, error: Throwable)
    fun logConfigError(message: String)

    companion object : ApiLogger {
        const val TAG = "[DEBUG]"

        const val PRIORITY_DEBUG = 3
        const val PRIORITY_INFO = 4
        const val PRIORITY_WARN = 5
        const val PRIORITY_ERROR = 6

        /**
         * Cờ [DEBUG] bật/tắt toàn bộ việc ghi log khi gửi/nhận API.
         * Mặc định lấy theo BuildConfig.DEBUG (true khi chạy bản debug, false khi release).
         */
        var isDebugEnabled: Boolean = BuildConfig.DEBUG

        /**
         * Handler ghi log thực tế. Mặc định ghi vào Logcat của Android,
         * có cơ chế fallback in ra console nếu chạy trong môi trường JVM/Test.
         */
        var writer: (priority: Int, tag: String, message: String) -> Unit = { priority, tag, message ->
            try {
                when (priority) {
                    PRIORITY_ERROR -> Log.e(tag, message)
                    PRIORITY_WARN -> Log.w(tag, message)
                    PRIORITY_INFO -> Log.i(tag, message)
                    else -> Log.d(tag, message)
                }
            } catch (_: Throwable) {
                println("$tag $message")
            }
        }

        override fun logRequest(method: String, url: String, headers: Headers?, body: RequestBody?) {
            writer(PRIORITY_DEBUG, TAG, "[DEBUG] --> [GỬI API] $method $url")

            headers?.let { h ->
                val relevant = listOf("Accept", "Content-Type", "Content-Length")
                val found = relevant.mapNotNull { name -> h[name]?.let { "$name: $it" } }
                if (found.isNotEmpty()) {
                    writer(PRIORITY_DEBUG, TAG, "[DEBUG]   Headers: ${found.joinToString(", ")}")
                }
            }

            if (body == null) {
                writer(PRIORITY_DEBUG, TAG, "[DEBUG]   Body: (không có body)")
            } else if (body is MultipartBody) {
                writer(PRIORITY_DEBUG, TAG, "[DEBUG]   Body: Multipart (${body.parts.size} phần):")
                val frameParts = mutableListOf<MultipartBody.Part>()
                val otherParts = mutableListOf<MultipartBody.Part>()

                for (part in body.parts) {
                    val disposition = part.headers?.get("Content-Disposition").orEmpty()
                    val name = extractParam(disposition, "name")
                    if (name == "frames") {
                        frameParts.add(part)
                    } else {
                        otherParts.add(part)
                    }
                }

                for (part in otherParts) {
                    val disposition = part.headers?.get("Content-Disposition").orEmpty()
                    val name = extractParam(disposition, "name") ?: "part"
                    val filename = extractParam(disposition, "filename")
                    val contentType = part.body.contentType()

                    if (filename != null || contentType?.type in listOf("image", "audio", "video")) {
                        val length = runCatching { part.body.contentLength() }.getOrDefault(-1L)
                        val sizeStr = if (length >= 0) formatBytes(length) else "kích thước không xác định"
                        val typeStr = contentType?.toString() ?: "binary"
                        writer(PRIORITY_DEBUG, TAG, "[DEBUG]     • [$name]: filename=\"$filename\" ($typeStr, $sizeStr)")
                    } else {
                        val text = readStringSafely(part.body)
                        writer(PRIORITY_DEBUG, TAG, "[DEBUG]     • [$name]: $text")
                    }
                }

                if (frameParts.isNotEmpty()) {
                    var totalBytes = 0L
                    frameParts.forEach {
                        val len = runCatching { it.body.contentLength() }.getOrDefault(0L)
                        if (len > 0) totalBytes += len
                    }
                    val sizeStr = formatBytes(totalBytes)
                    val firstFilename = extractParam(
                        frameParts.first().headers?.get("Content-Disposition").orEmpty(),
                        "filename"
                    ) ?: "frame_0.jpg"
                    val lastFilename = extractParam(
                        frameParts.last().headers?.get("Content-Disposition").orEmpty(),
                        "filename"
                    ) ?: "frame_${frameParts.size - 1}.jpg"
                    val fileRange = if (frameParts.size == 1) firstFilename else "$firstFilename..$lastFilename"
                    writer(
                        PRIORITY_DEBUG,
                        TAG,
                        "[DEBUG]     • [frames]: ${frameParts.size} frame(s) [$fileRange] (image/jpeg, tổng ~$sizeStr)"
                    )
                }
            } else {
                val contentType = body.contentType()
                if (contentType?.type in listOf("image", "audio", "video")) {
                    val length = runCatching { body.contentLength() }.getOrDefault(-1L)
                    val sizeStr = if (length >= 0) formatBytes(length) else "kích thước không xác định"
                    writer(PRIORITY_DEBUG, TAG, "[DEBUG]   Body: binary ($contentType, $sizeStr)")
                } else {
                    val text = readStringSafely(body)
                    writer(PRIORITY_DEBUG, TAG, "[DEBUG]   Body: $text")
                }
            }

            writer(PRIORITY_DEBUG, TAG, "[DEBUG] --> HẾT [GỬI API] ($method)")
        }

        override fun logResponse(
            method: String,
            url: String,
            code: Int,
            message: String,
            tookMs: Long,
            bodyString: String?,
            isError: Boolean,
        ) {
            val statusText = if (message.isNotBlank()) message else if (code in 200..299) "OK" else "Error"
            if (isError) {
                writer(PRIORITY_ERROR, TAG, "[DEBUG] <-- [NHẬN API LỖI] HTTP $code $statusText $url (${tookMs}ms)")
                if (!bodyString.isNullOrBlank()) {
                    val preview = if (bodyString.length > 2000) bodyString.take(2000) + "... (cắt ngắn)" else bodyString
                    writer(PRIORITY_ERROR, TAG, "[DEBUG]   Error Body: $preview")
                }
                writer(PRIORITY_ERROR, TAG, "[DEBUG] <-- HẾT [NHẬN API LỖI]")
            } else {
                writer(PRIORITY_DEBUG, TAG, "[DEBUG] <-- [NHẬN API] HTTP $code $statusText $url (${tookMs}ms)")
                if (!bodyString.isNullOrBlank()) {
                    val preview = if (bodyString.length > 2000) bodyString.take(2000) + "... (cắt ngắn)" else bodyString
                    writer(PRIORITY_DEBUG, TAG, "[DEBUG]   Response Body: $preview")
                }
                writer(PRIORITY_DEBUG, TAG, "[DEBUG] <-- HẾT [NHẬN API]")
            }
        }

        override fun logFailure(method: String, url: String, tookMs: Long, error: Throwable) {
            val errorName = error.javaClass.simpleName
            val errorMsg = error.message ?: "Không có thông báo lỗi"
            writer(
                PRIORITY_ERROR,
                TAG,
                "[DEBUG] <-- [LỖI GỌI API] $method $url (${tookMs}ms) thất bại: $errorName - $errorMsg"
            )
        }

        override fun logConfigError(message: String) {
            writer(PRIORITY_WARN, TAG, "[DEBUG] [CẤU HÌNH API LỖI] $message")
        }

        private fun readStringSafely(body: RequestBody): String = runCatching {
            if (body.isOneShot()) return "(stream không thể đọc lại)"
            val buffer = Buffer()
            body.writeTo(buffer)
            val text = buffer.readUtf8()
            if (text.length > 2000) {
                text.take(2000) + "... (cắt ngắn vì dài ${text.length} ký tự)"
            } else {
                text
            }
        }.getOrElse { "(không đọc được nội dung body: ${it.message})" }

        private fun formatBytes(bytes: Long): String {
            if (bytes < 0) return "không rõ"
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            return String.format(Locale.US, "%.1f MB", mb)
        }

        private fun extractParam(header: String, key: String): String? {
            val regex = Regex("""$key="([^"]+)"""")
            return regex.find(header)?.groupValues?.get(1)
        }
    }
}
