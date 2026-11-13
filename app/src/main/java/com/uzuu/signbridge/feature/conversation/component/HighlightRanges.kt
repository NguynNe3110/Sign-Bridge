package com.uzuu.signbridge.feature.conversation.component

import com.uzuu.signbridge.domain.model.Highlight

/**
 * Tìm vị trí các `highlights[].text` trong `summary` để tô đậm.
 * Bỏ qua highlight không có nguyên văn trong summary; các đoạn chồng nhau được gộp.
 */
fun findHighlightRanges(summary: String, highlights: List<Highlight>): List<IntRange> {
    val ranges = highlights
        .map { it.text }
        .filter { it.isNotBlank() }
        .flatMap { text -> summary.allIndexesOf(text).map { start -> start until start + text.length } }
        .sortedBy { it.first }

    val merged = mutableListOf<IntRange>()
    for (range in ranges) {
        val last = merged.lastOrNull()
        if (last != null && range.first <= last.last + 1) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
        } else {
            merged += range
        }
    }
    return merged
}

private fun String.allIndexesOf(text: String): List<Int> {
    val result = mutableListOf<Int>()
    var index = indexOf(text)
    while (index >= 0) {
        result += index
        index = indexOf(text, startIndex = index + text.length)
    }
    return result
}
