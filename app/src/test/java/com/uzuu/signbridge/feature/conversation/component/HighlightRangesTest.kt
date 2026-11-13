package com.uzuu.signbridge.feature.conversation.component

import com.uzuu.signbridge.domain.model.Highlight
import com.uzuu.signbridge.domain.model.HighlightType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightRangesTest {

    private val summary = "Panadol Xanh • 1 viên sau ăn • Ngày 2 lần • 35.000đ"

    @Test
    fun `finds every highlight in order`() {
        val ranges = findHighlightRanges(
            summary,
            listOf(
                Highlight(HighlightType.PRICE, "35.000đ"),
                Highlight(HighlightType.DOSAGE, "1 viên"),
            ),
        )
        assertEquals(listOf("1 viên", "35.000đ"), ranges.map { summary.substring(it) })
    }

    @Test
    fun `ignores highlight not present and blank text`() {
        val ranges = findHighlightRanges(
            summary,
            listOf(Highlight(HighlightType.PRICE, "40.000đ"), Highlight(HighlightType.OTHER, " ")),
        )
        assertTrue(ranges.isEmpty())
    }

    @Test
    fun `merges overlapping highlights`() {
        val ranges = findHighlightRanges(
            summary,
            listOf(Highlight(HighlightType.QUANTITY, "Ngày 2 lần"), Highlight(HighlightType.TIME, "2 lần")),
        )
        assertEquals(listOf("Ngày 2 lần"), ranges.map { summary.substring(it) })
    }

    @Test
    fun `highlights repeated occurrences`() {
        val text = "Quầy 3 • gọi số 3"
        val ranges = findHighlightRanges(text, listOf(Highlight(HighlightType.COUNTER, "3")))
        assertEquals(2, ranges.size)
    }
}
