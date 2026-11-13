package com.uzuu.signbridge.core.result

/**
 * Kết quả chung cho thao tác có thể lỗi. Thuần Kotlin để `domain` dùng được.
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

/**
 * Lỗi theo `error.code` của backend (docs/luong-xu-ly-chinh.md §4.0)
 * cộng thêm lỗi phía mobile (mất mạng, quá hạn 10 giây).
 */
data class AppError(
    val code: ErrorCode,
    val message: String? = null,
)

enum class ErrorCode {
    INVALID_REQUEST,
    PAYLOAD_TOO_LARGE,
    UNSUPPORTED_MEDIA_TYPE,
    NO_SIGN_DETECTED,
    NO_SPEECH_DETECTED,
    AI_UPSTREAM_ERROR,
    AI_TIMEOUT,
    NETWORK_UNAVAILABLE,
    UNKNOWN,
    NOT_CONFIGURED,
    DEVICE_ERROR,
    STORAGE_ERROR,
    CAMERA_PERMISSION_REQUIRED,
    MIC_PERMISSION_REQUIRED,
}
