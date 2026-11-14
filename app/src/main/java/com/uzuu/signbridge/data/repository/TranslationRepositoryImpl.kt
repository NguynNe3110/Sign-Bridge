package com.uzuu.signbridge.data.repository

import com.uzuu.signbridge.data.local.datasource.MockTranslationFallback
import com.uzuu.signbridge.data.mapper.toDomain
import com.uzuu.signbridge.data.remote.api.BridgeApi
import com.uzuu.signbridge.data.remote.dto.response.*
import com.uzuu.signbridge.domain.model.*
import com.uzuu.signbridge.domain.repository.TranslationRepository
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class TranslationRepositoryImpl(
    private val api: BridgeApi,
    private val enableMockFallback: Boolean = false,
) : TranslationRepository {
    override suspend fun checkHealth(): Boolean = try {
        api.request("health").optString("status") == "UP"
    } catch (e: Exception) {
        if (enableMockFallback && isRecoverable(e)) true else throw e
    }

    override suspend fun expand(context: ServiceContext, chips: List<String>, previousIncoming: String?): String {
        if (chips.size !in 1..5 || chips.any { it.isBlank() || it.length > 50 }) invalid("Thẻ không hợp lệ.")
        try {
            val json = JSONObject().put("context", context.name).put("chips", JSONArray(chips))
                .put("previousIncoming", previousIncoming?.take(300) ?: JSONObject.NULL)
            val response = api.request("expand", json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            return decode {
                response.text("spoken_sentence").also {
                    require(it.isNotBlank() && wordCount(it) <= 40)
                }
            }
        } catch (e: Exception) {
            if (enableMockFallback && isRecoverable(e)) {
                return MockTranslationFallback.mockExpand(context, chips)
            }
            throw e
        }
    }

    override suspend fun recognizeSign(
        context: ServiceContext, frames: List<SignFrame>,
        personalChips: List<String>, previousIncoming: String?,
    ): SignResult {
        CapturePolicy.validateFrames(frames)
        // Không có fallback dữ liệu mẫu: phát loa câu người dùng không hề ký là sai nghĩa (mục 4.2).
        // Lỗi được ném lên để màn hình báo lỗi và người dùng chuyển sang thẻ từ khóa.
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("context", context.name)
            .addFormDataPart("personalChips", JSONArray(personalChips.take(20)).toString())
        previousIncoming?.take(300)?.let { body.addFormDataPart("previousIncoming", it) }
        frames.forEachIndexed { index, frame ->
            body.addFormDataPart("frames", "frame_$index.jpg", frame.jpeg.toRequestBody("image/jpeg".toMediaType()))
        }
        val response = api.request("sign", body.build())
        return decode {
            val confidence = (response.get("confidence") as Number).toDouble()
            require(confidence.isFinite() && confidence in 0.0..1.0)
            val glosses = response.strings("glosses")
            require(glosses.isNotEmpty() && glosses.size <= 20 && glosses.none { it.isBlank() })
            // Server không ép số thẻ gợi ý từ LLM: lọc và cắt còn tối đa 6 thay vì bỏ cả kết quả nhận diện.
            val suggestions = response.strings("suggestedChips").map { it.trim() }
                .filter { it.isNotEmpty() && it.length <= 50 }.distinct().take(MAX_SIGN_SUGGESTIONS)
            // Câu quá dài → không phát loa, chuyển sang xác nhận bằng thẻ (glosses vẫn hiển thị).
            val sentence = response.text("spoken_sentence").takeIf { wordCount(it) <= 40 }.orEmpty()
            SignResponseDto(glosses, confidence, sentence, suggestions).toDomain(context)
        }
    }

    override suspend fun composeSignGlosses(
        context: ServiceContext, glosses: List<GlossPrediction>,
        personalChips: List<String>, previousIncoming: String?,
    ): SignResult {
        if (glosses.size !in 1..CapturePolicy.MAX_TURN_GLOSSES) invalid("Mỗi lượt cần 1–10 ký hiệu.")
        // Mục 4.2a: gửi nhãn nguyên văn của labels.json; server tự chuẩn hoá và ép về bộ ký hiệu.
        val json = JSONObject().put("context", context.name)
            .put("glosses", JSONArray(glosses.map { it.label }))
            .put("confidences", JSONArray(glosses.map { it.confidence.toDouble() }))
            .put("personalChips", JSONArray(personalChips.take(20)))
            .put("previousIncoming", previousIncoming?.take(300) ?: JSONObject.NULL)
        val response = api.request("sign/glosses",
            json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        return decode {
            val confidence = (response.get("confidence") as Number).toDouble()
            require(confidence.isFinite() && confidence in 0.0..1.0)
            val recognized = response.strings("glosses")
            require(recognized.isNotEmpty() && recognized.size <= CapturePolicy.MAX_TURN_GLOSSES &&
                recognized.none { it.isBlank() })
            val suggestions = response.strings("suggestedChips").map { it.trim() }
                .filter { it.isNotEmpty() && it.length <= 50 }.distinct().take(MAX_SIGN_SUGGESTIONS)
            val sentence = response.text("spoken_sentence").takeIf { wordCount(it) <= 40 }.orEmpty()
            // Không ép về bộ §7.2 như /api/sign: nhãn model (labels.json) là hợp lệ, server đã kiểm tra.
            val confirmed = confidence >= 0.7 && SignResult.UNKNOWN_GLOSS !in recognized
            SignResult(recognized, confidence, if (confirmed) sentence else "", suggestions)
        }
    }

    override suspend fun summarize(
        context: ServiceContext, audio: RecordedAudio, previousOutgoing: String?,
    ): SpeechSummary {
        CapturePolicy.validateAudio(audio)
        try {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("context", context.name)
                .addFormDataPart("audio", "reply.m4a", audio.bytes.toRequestBody("audio/mp4".toMediaType()))
            previousOutgoing?.take(300)?.let { body.addFormDataPart("previousOutgoing", it) }
            val response = api.request("summarize", body.build())
            return decode {
                val transcript = response.text("transcript")
                val summary = response.text("summary")
                val icon = response.text("iconCode")
                val replies = response.strings("quickReplies")
                // Direct AI may retain extra words to preserve numbers; do not truncate its output.
                require(transcript.isNotBlank() && summary.isNotBlank())
                require(icon in ICONS)
                require(replies.size == 3 && replies.all { it.isNotBlank() })
                val highlights = response.getJSONArray("highlights").let { items ->
                    (0 until items.length()).map { index ->
                        val item = items.getJSONObject(index)
                        val type = item.text("type")
                        require(type in HIGHLIGHT_TYPES)
                        HighlightResponseDto(type, item.text("text"))
                    }
                }
                SummaryResponseDto(transcript, summary, highlights, icon, replies).toDomain()
            }
        } catch (e: Exception) {
            if (enableMockFallback && isRecoverable(e)) {
                return MockTranslationFallback.mockSummarize(context)
            }
            throw e
        }
    }

    private fun isRecoverable(error: Exception): Boolean = when (error) {
        is CancellationException -> false
        is BridgeException -> error.code in setOf(
            BridgeError.NETWORK,
            BridgeError.NOT_CONFIGURED,
            BridgeError.AI_TIMEOUT,
            BridgeError.AI_UPSTREAM_ERROR,
        )
        else -> false
    }

    private fun JSONObject.text(key: String): String = get(key) as String
    private fun JSONObject.strings(key: String): List<String> = getJSONArray(key).let { array ->
        (0 until array.length()).map { array.get(it) as String }
    }
    private fun wordCount(text: String) = text.trim().split(Regex("\\s+"))
        .count { it.isNotBlank() && it != "•" }
    private fun invalid(message: String): Nothing = throw BridgeException(BridgeError.INVALID_REQUEST, message)
    private inline fun <T> decode(block: () -> T): T = try {
        block()
    } catch (cancel: CancellationException) { throw cancel
    } catch (error: Exception) {
        throw BridgeException(BridgeError.AI_UPSTREAM_ERROR, "Dữ liệu AI không đúng hợp đồng. Hãy thử lại hoặc dùng thẻ.", error)
    }

    private companion object {
        val ICONS = setOf("PILL", "MONEY", "QR_PAYMENT", "COUNTER", "TIME", "DOCUMENT", "DRINK", "FOOD", "CHECK", "WARNING", "INFO")
        val HIGHLIGHT_TYPES = setOf("PRICE", "COUNTER", "DOSAGE", "TIME", "QUANTITY", "OTHER")
        const val MAX_SIGN_SUGGESTIONS = 6
    }
}

