package com.uzuu.signbridge.data.local.dao

import android.content.ContentValues
import android.database.Cursor
import com.uzuu.signbridge.data.local.AppDatabase
import com.uzuu.signbridge.data.local.entity.MessageTurnEntity
import com.uzuu.signbridge.data.local.entity.QuickChipEntity

class ConversationDao(private val database: AppDatabase) {
    fun createSession(id: String, context: String, startedAt: Long) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE ConversationSession SET status = 'CLOSED' WHERE status = 'ACTIVE'")
            db.execSQL("INSERT INTO ConversationSession VALUES (?, ?, ?, 'ACTIVE')",
                arrayOf<Any>(id, context, startedAt))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun endSession(id: String) {
        database.writableDatabase.execSQL("UPDATE ConversationSession SET status = 'CLOSED' WHERE id = ?", arrayOf(id))
    }

    fun chips(context: String): List<QuickChipEntity> =
        database.readableDatabase.rawQuery(
            "SELECT * FROM QuickChip WHERE contextId = ? ORDER BY usageCount DESC, label COLLATE NOCASE, id",
            arrayOf(context),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(QuickChipEntity(cursor.text("id"), cursor.text("contextId"),
                    cursor.text("label"), cursor.text("category"), cursor.number("usageCount").toInt(),
                    cursor.number("isPersonal") == 1L))
            }
        }

    fun chip(id: String): QuickChipEntity? =
        database.readableDatabase.rawQuery(
            "SELECT * FROM QuickChip WHERE id = ? LIMIT 1",
            arrayOf(id),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                QuickChipEntity(
                    cursor.text("id"), cursor.text("contextId"),
                    cursor.text("label"), cursor.text("category"),
                    cursor.number("usageCount").toInt(),
                    cursor.number("isPersonal") == 1L,
                )
            } else null
        }

    fun insertChip(chip: QuickChipEntity, remoteId: String? = null) {
        database.writableDatabase.execSQL(
            "INSERT INTO QuickChip(id, contextId, label, category, usageCount, isPersonal, labelKey, remoteId) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(chip.id, chip.contextId, chip.label, chip.category, chip.usageCount,
                if (chip.isPersonal) 1 else 0, chip.label.lowercase(java.util.Locale.ROOT), remoteId),
        )
    }

    fun remoteChipId(id: String): String? = database.readableDatabase.rawQuery(
        "SELECT remoteId FROM QuickChip WHERE id = ?", arrayOf(id),
    ).use { if (it.moveToFirst()) it.getString(0) else null }

    fun syncChips(context: String, chips: List<QuickChipEntity>) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            // The API may include shared CUSTOM cards. Never insert them into another context.
            for (chip in chips.filter { it.contextId == context }) {
                val existing = db.rawQuery(
                    "SELECT id, label, usageCount, localLabelOverride FROM QuickChip " +
                        "WHERE contextId = ? AND (id = ? OR remoteId = ? OR labelKey = ?) " +
                        "ORDER BY CASE WHEN id = ? THEN 0 WHEN remoteId = ? THEN 1 ELSE 2 END LIMIT 1",
                    arrayOf(context, chip.id, chip.id, chip.label.lowercase(java.util.Locale.ROOT), chip.id, chip.id),
                ).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.text("id")
                        // Preserve preset IDs and local edits. Only an unedited remote row follows server text.
                        val label = if (id == chip.id && cursor.number("localLabelOverride") == 0L) chip.label
                            else cursor.text("label")
                        db.update("QuickChip", ContentValues().apply {
                            put("label", label)
                            put("labelKey", label.lowercase(java.util.Locale.ROOT))
                            put("usageCount", maxOf(cursor.number("usageCount").toInt(), chip.usageCount))
                            put("remoteId", chip.id)
                        }, "id = ?", arrayOf(id))
                        true
                    } else false
                }
                if (!existing) insertChip(chip, remoteId = chip.id)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updateChip(id: String, label: String): Boolean =
        database.writableDatabase.update("QuickChip", ContentValues().apply {
            put("label", label)
            put("labelKey", label.lowercase(java.util.Locale.ROOT))
            put("localLabelOverride", 1)
        }, "id = ?", arrayOf(id)) > 0

    fun deletePersonalChip(id: String): Boolean =
        database.writableDatabase.delete("QuickChip", "id = ? AND isPersonal = 1", arrayOf(id)) > 0

    fun appendTurn(turn: MessageTurnEntity, usedChipIds: List<String>) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val context = db.rawQuery(
                "SELECT contextId FROM ConversationSession WHERE id = ? AND status = 'ACTIVE'",
                arrayOf(turn.sessionId),
            ).use { check(it.moveToFirst()) { "Conversation session is not active" }; it.getString(0) }
            db.insertOrThrow("MessageTurn", null, ContentValues().apply {
                put("id", turn.id); put("sessionId", turn.sessionId); put("direction", turn.direction)
                put("rawInput", turn.rawInput); put("aiProcessedText", turn.processedText)
                put("iconCode", turn.iconCode); put("isOffline", if (turn.isOffline) 1 else 0)
                put("createdAt", turn.createdAt)
            })
            usedChipIds.distinct().forEach { id ->
                db.execSQL("UPDATE QuickChip SET usageCount = usageCount + 1 WHERE id = ? AND contextId = ?",
                    arrayOf(id, context))
                db.rawQuery("SELECT changes()", null).use {
                    check(it.moveToFirst() && it.getInt(0) == 1) { "Selected chip no longer exists" }
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun history(limit: Int): List<MessageTurnEntity> =
        database.readableDatabase.rawQuery(
            "SELECT * FROM MessageTurn ORDER BY createdAt DESC, rowid DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(MessageTurnEntity(
                    cursor.text("id"), cursor.text("sessionId"), cursor.text("direction"),
                    cursor.text("rawInput"), cursor.text("aiProcessedText"),
                    cursor.getString(cursor.getColumnIndexOrThrow("iconCode")),
                    cursor.number("isOffline") == 1L, cursor.number("createdAt"),
                ))
            }
        }

    private fun Cursor.text(column: String): String = getString(getColumnIndexOrThrow(column))
    private fun Cursor.number(column: String): Long = getLong(getColumnIndexOrThrow(column))
}

