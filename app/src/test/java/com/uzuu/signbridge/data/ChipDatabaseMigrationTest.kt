package com.uzuu.signbridge.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.uzuu.signbridge.data.local.AppDatabase
import com.uzuu.signbridge.data.local.dao.ConversationDao
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ChipDatabaseMigrationTest {
    @Test fun versionOneGetsAdditionalDefaultsWithoutLosingEditsPersonalCardsOrHistory() {
        val app = RuntimeEnvironment.getApplication()
        val name = "chip-migration-test.db"
        app.deleteDatabase(name)
        val legacy = object : SQLiteOpenHelper(app, name, null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("CREATE TABLE ContextPreset(id TEXT PRIMARY KEY)")
                db.execSQL("""CREATE TABLE QuickChip(
                    id TEXT PRIMARY KEY, contextId TEXT NOT NULL REFERENCES ContextPreset(id),
                    label TEXT NOT NULL COLLATE NOCASE, category TEXT NOT NULL,
                    usageCount INTEGER NOT NULL DEFAULT 0, isPersonal INTEGER NOT NULL DEFAULT 0,
                    labelKey TEXT NOT NULL, UNIQUE(contextId, labelKey))""")
                db.execSQL("CREATE TABLE ConversationSession(id TEXT PRIMARY KEY, contextId TEXT, startedAt INTEGER, status TEXT)")
                db.execSQL("""CREATE TABLE MessageTurn(id TEXT PRIMARY KEY, sessionId TEXT, direction TEXT,
                    rawInput TEXT, aiProcessedText TEXT, iconCode TEXT, isOffline INTEGER, createdAt INTEGER)""")
                val original = mapOf(
                    "CAFE" to listOf("Cà phê sữa", "Ít ngọt", "Mang đi", "Hỏi giá", "Thanh toán"),
                    "PHARMACY" to listOf("Đau đầu", "Không buồn ngủ", "Uống sau ăn", "Hỏi giá", "Dị ứng"),
                    "PUBLIC" to listOf("CCCD", "Xác nhận cư trú", "Quầy nào", "Giấy tờ", "Cảm ơn"),
                    "CUSTOM" to listOf("Xin chào", "Cảm ơn", "Có", "Không", "Thanh toán"),
                )
                original.forEach { (context, labels) ->
                    db.execSQL("INSERT INTO ContextPreset VALUES (?)", arrayOf(context))
                    labels.forEachIndexed { index, label ->
                        db.execSQL("INSERT INTO QuickChip(id, contextId, label, category, labelKey) VALUES (?, ?, ?, 'DETAIL', ?)",
                            arrayOf("seed_${context}_$index", context, label, label.lowercase(java.util.Locale.ROOT)))
                    }
                }
                db.execSQL("UPDATE QuickChip SET label = 'Sữa hạt', labelKey = 'sữa hạt', usageCount = 7 WHERE id = 'seed_CAFE_0'")
                db.execSQL("INSERT INTO QuickChip VALUES ('personal', 'CAFE', 'Bánh mì chay', 'DETAIL', 4, 1, 'bánh mì chay')")
                db.execSQL("INSERT INTO ConversationSession VALUES ('session', 'CAFE', 100, 'ACTIVE')")
                db.execSQL("INSERT INTO MessageTurn VALUES ('turn', 'session', 'OUTGOING', 'Sữa hạt', 'Cho mình sữa hạt.', NULL, 0, 101)")
            }
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        legacy.writableDatabase
        legacy.close()
        try {
            AppDatabase(app, name).use { database ->
                val dao = ConversationDao(database)
                listOf("CAFE", "PHARMACY", "PUBLIC", "CUSTOM").forEach { context ->
                    assertEquals("Missing defaults in $context", 15, dao.chips(context).count { !it.isPersonal })
                }
                assertEquals("Sữa hạt", dao.chip("seed_CAFE_0")?.label)
                assertEquals(7, dao.chip("seed_CAFE_0")?.usageCount)
                assertEquals(4, dao.chip("personal")?.usageCount)
                assertEquals("Cho mình sữa hạt.", dao.history(10).single().processedText)
                dao.updateChip("personal", "Bánh mì không ớt")
            }
            // A subsequent launch must not insert defaults again or reset existing edits.
            AppDatabase(app, name).use { database ->
                val dao = ConversationDao(database)
                assertEquals(16, dao.chips("CAFE").size)
                assertEquals("Bánh mì không ớt", dao.chip("personal")?.label)
                assertEquals(7, dao.chip("seed_CAFE_0")?.usageCount)
            }
        } finally { app.deleteDatabase(name) }
    }
}
