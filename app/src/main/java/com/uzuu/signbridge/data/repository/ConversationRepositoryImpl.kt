package com.uzuu.signbridge.data.repository

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import com.uzuu.signbridge.data.local.dao.ConversationDao
import com.uzuu.signbridge.data.local.entity.MessageTurnEntity
import com.uzuu.signbridge.data.local.entity.QuickChipEntity
import com.uzuu.signbridge.data.mapper.toChipResponseDto
import com.uzuu.signbridge.data.mapper.toDomain
import com.uzuu.signbridge.data.mapper.toEntity
import com.uzuu.signbridge.data.remote.api.BridgeApi
import com.uzuu.signbridge.data.session.UserSessionManager
import com.uzuu.signbridge.domain.model.*
import com.uzuu.signbridge.domain.repository.ConversationRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class ConversationRepositoryImpl(
    private val dao: ConversationDao,
    private val api: BridgeApi? = null,
    private val session: UserSessionManager? = null,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ConversationRepository {

    constructor(
        dao: ConversationDao,
        io: CoroutineDispatcher,
    ) : this(dao, null, null, io)
    private val revision = MutableStateFlow(0L)
    override fun observeChips(context: ServiceContext) = revision.map { getChips(context) }
    override suspend fun createSession(context: ServiceContext) = storage {
        ConversationSession(UUID.randomUUID().toString(), context, System.currentTimeMillis()).also {
            dao.createSession(it.id, context.name, it.startedAt)
        }
    }

    override suspend fun endSession(id: String) = storage { dao.endSession(id) }

    override suspend fun getChips(context: ServiceContext): List<QuickChip> = withContext(io) {
        if (api != null && session != null) {
            try {
                val userId = session.userId
                val array = api.requestArray("chips?context=${context.name}&user_id=$userId", mapOf("X-User-Id" to userId))
                val dtos = (0 until array.length()).map { array.getJSONObject(it).toChipResponseDto() }
                if (dtos.isNotEmpty()) {
                    dao.syncChips(context.name, dtos.map { it.toEntity() })
                    return@withContext dtos.map { it.toDomain() }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // Fallback to local SQLite cache
            }
        }
        storage { dao.chips(context.name).map { it.toDomain() } }
    }

    override suspend fun addChip(context: ServiceContext, label: String, category: ChipCategory): QuickChip {
        val normalized = normalizedLabel(label)
        return withContext(io) {
            if (api != null && session != null) {
                val userId = session.userId
                val json = JSONObject().apply {
                    put("text", normalized)
                    put("context", context.name)
                    put("user_id", userId)
                }
                val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val res = api.request("chips", requestBody, mapOf("X-User-Id" to userId))
                val dto = res.toChipResponseDto()
                storage {
                    try { dao.insertChip(dto.toEntity()) } catch (_: SQLiteConstraintException) {}
                }
                revision.update { it + 1 }
                return@withContext dto.toDomain()
            }
            storage {
                val entity = QuickChipEntity(UUID.randomUUID().toString(), context.name, normalized, category.name, 0, true)
                try { dao.insertChip(entity) } catch (error: SQLiteConstraintException) {
                    throw BridgeException(BridgeError.INVALID_REQUEST, "Ngữ cảnh này đã có thẻ cùng tên.", error)
                }
                revision.update { it + 1 }
                entity.toDomain()
            }
        }
    }

    override suspend fun updateChip(id: String, label: String): Unit = withContext(io) {
        val normalized = normalizedLabel(label)
        if (api != null && session != null) {
            val userId = session.userId
            val existing = storage { dao.chip(id) }
            val contextName = existing?.contextId ?: "CUSTOM"

            if (existing?.isPersonal == true) {
                val deleteRes = api.delete("chips/$id?user_id=$userId", mapOf("X-User-Id" to userId))
                if (!deleteRes.optBoolean("success", false)) {
                    throw BridgeException(BridgeError.INVALID_REQUEST, "Không thể cập nhật thẻ cá nhân.")
                }
                storage { dao.deletePersonalChip(id) }
            }

            val json = JSONObject().apply {
                put("text", normalized)
                put("context", contextName)
                put("user_id", userId)
            }
            val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val res = api.request("chips", requestBody, mapOf("X-User-Id" to userId))
            val dto = res.toChipResponseDto()
            storage {
                try { dao.insertChip(dto.toEntity()) } catch (_: SQLiteConstraintException) {}
            }
            revision.update { it + 1 }
            return@withContext
        }

        storage {
            try {
                if (!dao.updateChip(id, normalized)) {
                    throw BridgeException(BridgeError.INVALID_REQUEST, "Thẻ không còn tồn tại.")
                }
            } catch (error: SQLiteConstraintException) {
                throw BridgeException(BridgeError.INVALID_REQUEST, "Ngữ cảnh này đã có thẻ cùng tên.", error)
            }
            revision.update { it + 1 }
        }
    }

    private fun normalizedLabel(label: String): String {
        val normalized = java.text.Normalizer.normalize(label.trim(), java.text.Normalizer.Form.NFC)
        if (normalized.isBlank() || normalized.length > 50 || normalized.any { it.isISOControl() }) {
            throw BridgeException(BridgeError.INVALID_REQUEST, "Thẻ cần 1–50 ký tự và không chứa ký tự điều khiển.")
        }
        return normalized
    }

    override suspend fun deletePersonalChip(id: String): Unit = withContext(io) {
        if (api != null && session != null) {
            val userId = session.userId
            val res = api.delete("chips/$id?user_id=$userId", mapOf("X-User-Id" to userId))
            if (!res.optBoolean("success", false)) {
                throw BridgeException(BridgeError.INVALID_REQUEST, "Chỉ có thể xóa thẻ cá nhân còn tồn tại.")
            }
            storage { dao.deletePersonalChip(id) }
            revision.update { it + 1 }
            return@withContext
        }

        storage {
            if (!dao.deletePersonalChip(id)) {
                throw BridgeException(BridgeError.INVALID_REQUEST, "Chỉ có thể xóa thẻ cá nhân còn tồn tại.")
            }
            revision.update { it + 1 }
        }
    }

    override suspend fun getHistory(limit: Int) = storage { dao.history(limit.coerceIn(1, 500)).map { it.toDomain() } }

    override suspend fun appendTurn(
        sessionId: String, direction: Direction, rawInput: String, processedText: String,
        iconCode: String?, isOffline: Boolean, usedChipIds: List<String>,
    ) = storage {
        if (processedText.isBlank()) throw BridgeException(BridgeError.INVALID_REQUEST, "Nội dung hội thoại đang trống.")
        val turn = MessageTurnEntity(UUID.randomUUID().toString(), sessionId, direction.name,
            rawInput, processedText, iconCode, isOffline, System.currentTimeMillis())
        dao.appendTurn(turn, usedChipIds)
        revision.update { it + 1 }
        turn.toDomain()
    }

    private suspend fun <T> storage(block: () -> T): T = withContext(io) {
        try { block() } catch (error: SQLiteException) {
            throw BridgeException(BridgeError.STORAGE, "Không thể đọc hoặc lưu dữ liệu trên thiết bị.", error)
        } catch (error: IllegalStateException) {
            throw BridgeException(BridgeError.STORAGE, "Phiên hội thoại hoặc thẻ đã thay đổi. Vui lòng thử lại.", error)
        }
    }
}

