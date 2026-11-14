package com.uzuu.signbridge.domain

import com.uzuu.signbridge.domain.model.*
import com.uzuu.signbridge.domain.usecase.ComposeSignUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ComposeSignUseCaseTest {
    private val conversation = MemoryConversation().apply {
        chips += QuickChip("p1", ServiceContext.PHARMACY, "Dị ứng penicillin", isPersonal = true)
        chips += QuickChip("s1", ServiceContext.PHARMACY, "Sốt", isPersonal = false)
    }
    private val translation = StubTranslation()
    private val useCase = ComposeSignUseCase(conversation, translation)
    private val session = ConversationSession("session", ServiceContext.PHARMACY, 1)

    @Test fun serverSentenceIsSavedAndPersonalChipsForwarded() = runBlocking {
        translation.glossesAction = { SignResult(listOf("ĐAU BỤNG"), 0.93, "Mình bị đau bụng ạ.", listOf("đau bụng")) }
        val result = useCase(session, listOf(GlossPrediction("đau bụng", 0.93f)), null)
        assertFalse(result.needsConfirmation)
        assertFalse(result.isOffline)
        assertEquals(listOf("Dị ứng penicillin"), translation.lastPersonalChips)
        assertEquals("Mình bị đau bụng ạ.", conversation.turns.single().processedText)
    }

    @Test fun offlineFallbackOnlyJoinsLabelsLikeServerTemplate() = runBlocking {
        translation.glossesAction = { throw BridgeException(BridgeError.NETWORK, "offline") }
        val result = useCase(session,
            listOf(GlossPrediction("đau bụng", 0.93f), GlossPrediction("hiệu thuốc", 0.81f)), null)
        assertTrue(result.isOffline)
        assertFalse(result.needsConfirmation)
        assertEquals("Đau bụng, hiệu thuốc.", result.sentence)
        assertEquals(listOf("ĐAU BỤNG", "HIỆU THUỐC"), result.glosses)
        assertTrue(conversation.turns.single().isOffline)
    }

    @Test fun offlineLowConfidenceAsksForConfirmationWithDoubtfulLabelFirst() = runBlocking {
        translation.glossesAction = { throw BridgeException(BridgeError.AI_TIMEOUT, "slow") }
        val result = useCase(session,
            listOf(GlossPrediction("đau bụng", 0.93f), GlossPrediction("hiệu thuốc", 0.42f)), null)
        assertTrue(result.needsConfirmation)
        assertEquals("", result.sentence)
        assertEquals(listOf("ĐAU BỤNG", SignResult.UNKNOWN_GLOSS), result.glosses)
        assertEquals(listOf("hiệu thuốc", "đau bụng"), result.suggestedChips)
        assertTrue(result.confidence < 0.7)
        assertTrue(conversation.turns.isEmpty())
    }

    @Test fun invalidInputIsNotSentOrFallenBack() = runBlocking {
        var called = false
        translation.glossesAction = { called = true; throw IllegalStateException() }
        for (glosses in listOf(emptyList(), List(11) { GlossPrediction("a$it", 0.9f) },
            listOf(GlossPrediction(" ", 0.9f)), listOf(GlossPrediction("a", 1.5f)))) {
            try { useCase(session, glosses, null); fail("Expected INVALID_REQUEST")
            } catch (error: BridgeException) { assertEquals(BridgeError.INVALID_REQUEST, error.code) }
        }
        assertFalse(called)
    }

    @Test fun nonRecoverableServerErrorPropagates() = runBlocking {
        translation.glossesAction = { throw BridgeException(BridgeError.INVALID_REQUEST, "bad") }
        try { useCase(session, listOf(GlossPrediction("đau bụng", 0.9f)), null); fail("Expected error")
        } catch (error: BridgeException) { assertEquals(BridgeError.INVALID_REQUEST, error.code) }
        assertTrue(conversation.turns.isEmpty())
    }
}
