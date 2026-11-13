package com.uzuu.signbridge.data.repository

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import com.uzuu.signbridge.data.local.dao.ConversationDao
import com.uzuu.signbridge.data.local.entity.MessageTurnEntity
import com.uzuu.signbridge.data.local.entity.QuickChipEntity
import com.uzuu.signbridge.data.mapper.toDomain
import com.uzuu.signbridge.domain.model.*
import com.uzuu.signbridge.domain.repository.ConversationRepository
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ConversationRepositoryImpl(
    private val dao: ConversationDao,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ConversationRepository {
    override suspend fun createSession(context: ServiceContext) = storage {
        ConversationSession(UUID.randomUUID().toString(), context, System.currentTimeMillis()).also {
            dao.createSession(it.id, context.name, it.startedAt)
        }
    }

    override suspend fun endSession(id: String) = storage { dao.endSession(id) }
    override suspend fun getChips(context: ServiceContext) = storage { dao.chips(context.name).map { it.toDomain() } }

    override suspend fun addChip(context: ServiceContext, label: String, category: ChipCategory): QuickChip {
        val normalized = java.text.Normalizer.normalize(label.trim(), java.text.Normalizer.Form.NFC)
        if (normalized.isBlank() || normalized.length > 50 || normalized.any { it.isISOControl() }) {
            throw BridgeException(BridgeError.INVALID_REQUEST, "Thẻ cần 1–50 ký tự và không chứa ký tự điều khiển.")
        }
        return storage {
            val entity = QuickChipEntity(UUID.randomUUID().toString(), context.name, normalized, category.name, 0, true)
            try { dao.insertChip(entity) } catch (error: SQLiteConstraintException) {
                throw BridgeException(BridgeError.INVALID_REQUEST, "Ngữ cảnh này đã có thẻ cùng tên.", error)
            }
            entity.toDomain()
        }
    }

    override suspend fun deletePersonalChip(id: String) = storage {
        if (!dao.deletePersonalChip(id)) {
            throw BridgeException(BridgeError.INVALID_REQUEST, "Chỉ có thể xóa thẻ cá nhân còn tồn tại.")
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

