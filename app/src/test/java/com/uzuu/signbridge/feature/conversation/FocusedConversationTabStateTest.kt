package com.uzuu.signbridge.feature.conversation

import com.uzuu.signbridge.core.result.ErrorCode
import com.uzuu.signbridge.domain.model.ContextType
import com.uzuu.signbridge.domain.model.Highlight
import com.uzuu.signbridge.domain.model.HighlightType
import com.uzuu.signbridge.domain.model.IconCode
import com.uzuu.signbridge.domain.model.SignResult
import com.uzuu.signbridge.domain.model.SummaryResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusedConversationTabStateTest {

    private val baseState = ConversationUiState(context = ContextType.PHARMACY)

    private val sampleSummary = SummaryResult(
        transcript = "Thuốc này uống sau ăn nhé.",
        summary = "Uống sau ăn",
        highlights = listOf(Highlight(HighlightType.DOSAGE, "sau ăn")),
        iconCode = IconCode.PILL,
        quickReplies = listOf("Vâng", "Cảm ơn"),
    )

    @Test
    fun `initial state defaults to customer tab and does not switch on idle update`() {
        val tabState = FocusedConversationTabState(initialStaffTab = false)
        assertFalse(tabState.staffTab)

        tabState.update(baseState)
        assertFalse(tabState.staffTab)
    }

    @Test
    fun `customer sending chips switches to staff tab when expansion completes`() {
        val tabState = FocusedConversationTabState(initialStaffTab = false)

        // Customer initiates sending chips
        tabState.onCustomerSendInitiated()

        // State while expanding: should not switch yet
        val expandingState = baseState.copy(
            isExpanding = true,
            selectedChips = listOf("Đau đầu", "Sốt"),
        )
        tabState.update(expandingState)
        assertFalse(tabState.staffTab)

        // Expansion completed successfully: sentence ready, chips cleared
        val completedState = baseState.copy(
            isExpanding = false,
            selectedChips = emptyList(),
            spokenSentence = "Tôi bị đau đầu và sốt.",
        )
        tabState.update(completedState)
        assertTrue(tabState.staffTab)
    }

    @Test
    fun `customer sending quick reply switches to staff tab immediately`() {
        val tabState = FocusedConversationTabState(initialStaffTab = false)

        // Quick reply clicked and send initiated
        tabState.onCustomerSendInitiated()

        // Quick reply does not expand, updates spokenSentence and clears chips directly
        val sentState = baseState.copy(
            isExpanding = false,
            selectedChips = emptyList(),
            spokenSentence = "Cảm ơn",
        )
        tabState.update(sentState)
        assertTrue(tabState.staffTab)
    }

    @Test
    fun `customer send error does not switch to staff tab`() {
        val tabState = FocusedConversationTabState(initialStaffTab = false)

        tabState.onCustomerSendInitiated()

        // Expansion fails with network error
        val errorState = baseState.copy(
            isExpanding = false,
            selectedChips = listOf("Đau đầu"),
            deafError = ErrorCode.NETWORK_UNAVAILABLE,
        )
        tabState.update(errorState)
        assertFalse(tabState.staffTab)
    }

    @Test
    fun `confident sign recognition switches to staff tab`() {
        val tabState = FocusedConversationTabState(initialStaffTab = false)

        // Customer releases sign button: recognition begins
        val recognizingState = baseState.copy(isRecognizing = true)
        tabState.update(recognizingState)
        assertFalse(tabState.staffTab)

        // Recognition completes successfully with confident sentence
        val recognizedState = baseState.copy(
            isRecognizing = false,
            spokenSentence = "Cho tôi mua thuốc giảm đau",
            pendingSign = null,
        )
        tabState.update(recognizedState)
        assertTrue(tabState.staffTab)
    }

    @Test
    fun `unconfident sign recognition requiring confirmation does not switch to staff tab`() {
        val tabState = FocusedConversationTabState(initialStaffTab = false)

        tabState.update(baseState.copy(isRecognizing = true))

        // Recognition needs confirmation -> pendingSign != null
        val confirmationState = baseState.copy(
            isRecognizing = false,
            pendingSign = SignResult(
                glosses = listOf("THUOC"),
                confidence = 0.4,
                sentence = "Thuốc",
                suggestedChips = listOf("Thuốc ho", "Thuốc cảm"),
            ),
        )
        tabState.update(confirmationState)
        assertFalse(tabState.staffTab)
    }

    @Test
    fun `sign recognition error does not switch to staff tab`() {
        val tabState = FocusedConversationTabState(initialStaffTab = false)

        tabState.update(baseState.copy(isRecognizing = true))

        val errorState = baseState.copy(
            isRecognizing = false,
            deafError = ErrorCode.DEVICE_ERROR,
        )
        tabState.update(errorState)
        assertFalse(tabState.staffTab)
    }

    @Test
    fun `sign capture cancelled before recognition does not switch to staff tab`() {
        val tabState = FocusedConversationTabState(initialStaffTab = false)

        // Signing started then cancelled (isRecognizing never became true)
        tabState.update(baseState.copy(isSigning = true))
        tabState.update(baseState.copy(isSigning = false))

        assertFalse(tabState.staffTab)
    }

    @Test
    fun `staff speech summarization switches to customer tab`() {
        // Start on staff tab
        val tabState = FocusedConversationTabState(initialStaffTab = true)
        assertTrue(tabState.staffTab)

        // Staff releases mic: summarization begins
        val summarizingState = baseState.copy(isSummarizing = true)
        tabState.update(summarizingState)
        assertTrue(tabState.staffTab)

        // Summarization completes and summary is generated
        val completedState = baseState.copy(
            isSummarizing = false,
            summary = sampleSummary,
        )
        tabState.update(completedState)
        assertFalse(tabState.staffTab)
    }

    @Test
    fun `staff speech error does not switch to customer tab`() {
        val tabState = FocusedConversationTabState(initialStaffTab = true)

        tabState.update(baseState.copy(isSummarizing = true))

        // Summarization fails with error
        val errorState = baseState.copy(
            isSummarizing = false,
            staffError = ErrorCode.NETWORK_UNAVAILABLE,
        )
        tabState.update(errorState)
        assertTrue(tabState.staffTab)
    }

    @Test
    fun `staff recording cancelled before summarization does not switch to customer tab`() {
        val tabState = FocusedConversationTabState(initialStaffTab = true)

        // Staff presses mic then cancels (isSummarizing never became true)
        tabState.update(baseState.copy(isStaffRecording = true))
        tabState.update(baseState.copy(isStaffRecording = false))

        assertTrue(tabState.staffTab)
    }

    @Test
    fun `manual tab selection changes tab and remains stable on idle updates`() {
        val tabState = FocusedConversationTabState(initialStaffTab = false)
        assertFalse(tabState.staffTab)

        // User manually taps Staff tab
        tabState.selectTab(true)
        assertTrue(tabState.staffTab)

        // Idle state updates should not overwrite manual choice
        tabState.update(baseState)
        assertTrue(tabState.staffTab)

        // User manually taps Customer tab
        tabState.selectTab(false)
        assertFalse(tabState.staffTab)

        tabState.update(baseState)
        assertFalse(tabState.staffTab)
    }
}
