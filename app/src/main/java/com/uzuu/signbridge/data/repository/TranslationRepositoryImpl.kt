package com.uzuu.signbridge.data.repository

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

class TranslationRepositoryImpl(private val api: BridgeApi) : TranslationRepository {
    override suspend fun checkHealth(): Boolean =
        api.request("health").optString("status") == "UP"

    override suspend fun expand(context: ServiceContext, chips: List<String>, previousIncoming: String?): String {
        if (chips.size !in 1..5 || chips.any { it.isBlank() || it.length > 50 }) invalid("Thẻ không hợp lệ.")
        val json = JSONObject().put("context", context.name).put("chips", JSONArray(chips))
            .put("previousIncoming", previousIncoming?.take(300) ?: JSONObject.NULL)
        val response = api.request("expand", json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        return decode {
            response.text("spoken_sentence").also {
                require(it.isNotBlank() && wordCount(it) <= 40)
            }
        }
    }

    override suspend fun recognizeSign(
        context: ServiceContext, frames: List<SignFrame>,
        personalChips: List<String>, previousIncoming: String?,
    ): SignResult {
        CapturePolicy.validateFrames(frames)
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
            val suggestions = response.strings("suggestedChips")
            require(suggestions.size in 3..6 && suggestions.all { it.isNotBlank() && it.length <= 50 })
            val sentence = response.text("spoken_sentence")
            require(wordCount(sentence) <= 40)
            SignResponseDto(glosses, confidence, sentence, suggestions).toDomain(context)
        }
    }

    override suspend fun summarize(
        context: ServiceContext, audio: RecordedAudio, previousOutgoing: String?,
    ): SpeechSummary {
        CapturePolicy.validateAudio(audio)
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
    }
}

