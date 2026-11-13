package com.uzuu.signbridge.domain.model

/** Thẻ từ khóa (docx §11 `QuickChip`). */
data class QuickChip(
    val id: Long,
    val context: ContextType,
    val label: String,
    val isPersonal: Boolean,
    val usageCount: Int = 0,
)
