package com.uzuu.signbridge.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDatabase(context: Context, name: String = "signbridge.db") :
    SQLiteOpenHelper(context.applicationContext, name, null, 1) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE ContextPreset (
            id TEXT PRIMARY KEY, name TEXT NOT NULL, icon TEXT NOT NULL, colorTheme TEXT NOT NULL)""")
        db.execSQL("""CREATE TABLE QuickChip (
            id TEXT PRIMARY KEY, contextId TEXT NOT NULL REFERENCES ContextPreset(id),
            label TEXT NOT NULL COLLATE NOCASE CHECK(length(label) BETWEEN 1 AND 50),
            category TEXT NOT NULL, usageCount INTEGER NOT NULL DEFAULT 0 CHECK(usageCount >= 0),
            isPersonal INTEGER NOT NULL DEFAULT 0, labelKey TEXT NOT NULL, UNIQUE(contextId, labelKey))""")
        db.execSQL("""CREATE TABLE ConversationSession (
            id TEXT PRIMARY KEY, contextId TEXT NOT NULL REFERENCES ContextPreset(id),
            startedAt INTEGER NOT NULL, status TEXT NOT NULL)""")
        db.execSQL("""CREATE TABLE MessageTurn (
            id TEXT PRIMARY KEY, sessionId TEXT NOT NULL REFERENCES ConversationSession(id),
            direction TEXT NOT NULL, rawInput TEXT NOT NULL, aiProcessedText TEXT NOT NULL,
            iconCode TEXT, isOffline INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL)""")
        db.execSQL("CREATE INDEX turn_session_time ON MessageTurn(sessionId, createdAt)")
        db.execSQL("CREATE INDEX chip_context_usage ON QuickChip(contextId, usageCount DESC)")
        listOf(
            arrayOf("CAFE", "Quán ăn / cà phê", "COFFEE", "cafe"),
            arrayOf("PHARMACY", "Hiệu thuốc", "PILL", "pharmacy"),
            arrayOf("PUBLIC", "Hành chính", "DOCUMENT", "public"),
            arrayOf("CUSTOM", "Tự do", "INFO", "custom"),
        ).forEach { db.execSQL("INSERT INTO ContextPreset VALUES (?, ?, ?, ?)", it) }
        val seed = mapOf(
            "CAFE" to listOf(
                "Cà phê sữa", "Ít ngọt", "Mang đi", "Hỏi giá", "Thanh toán",
                "Cà phê đen", "Trà đào", "Nước suối", "Ít đá", "Sinh tố bơ",
                "Uống tại quán", "Cho xin mật khẩu wifi", "Cho xin thêm đá", "Chuyển khoản QR", "Cho mình xem menu",
            ),
            "PHARMACY" to listOf(
                "Đau đầu", "Không buồn ngủ", "Uống sau ăn", "Hỏi giá", "Dị ứng",
                "Bị sốt", "Đau họng", "Ho khan", "Sổ mũi", "Đau dạ dày",
                "Thuốc nhỏ mắt", "Băng cá nhân", "Vitamin C", "Uống trước ăn", "Uống ngày mấy lần",
            ),
            "PUBLIC" to listOf(
                "CCCD", "Xác nhận cư trú", "Quầy nào", "Giấy tờ", "Cảm ơn",
                "Lấy số thứ tự", "Bao giờ có kết quả", "Nộp hồ sơ", "Xin mẫu đơn", "Công chứng giấy tờ",
                "Lệ phí bao nhiêu", "Tôi cần gặp ai", "Thủ tục làm lại thẻ", "Chờ ở đâu", "Có cần ảnh thẻ không",
            ),
            "CUSTOM" to listOf(
                "Xin chào", "Cảm ơn", "Có", "Không", "Thanh toán",
                "Tôi là người khiếm thính", "Làm ơn viết ra giấy giúp tôi", "Xin lỗi làm phiền", "Chờ tôi một chút", "Tôi hiểu rồi",
                "Tôi chưa hiểu", "Nhà vệ sinh ở đâu", "Giúp tôi với", "Bao nhiêu tiền", "Tạm biệt",
            ),
        )
        seed.forEach { (context, labels) ->
            labels.forEachIndexed { index, label ->
                db.execSQL(
                    "INSERT INTO QuickChip(id, contextId, label, category, labelKey) VALUES (?, ?, ?, ?, ?)",
                    arrayOf("seed_${context}_$index", context, label,
                        if (index == 0) "SUBJECT" else if (index == 1) "DETAIL" else "ACTION",
                        label.lowercase(java.util.Locale.ROOT)),
                )
            }
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Add explicit, non-destructive migrations when the schema version changes.
        error("Missing database migration $oldVersion → $newVersion")
    }
}

