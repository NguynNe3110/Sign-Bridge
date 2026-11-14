package com.uzuu.signbridge.domain

import com.uzuu.signbridge.domain.model.*
import com.uzuu.signbridge.domain.usecase.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ConversationUseCaseTest {
    private val session = ConversationSession("session", ServiceContext.CAFE, 1)
    private val frames get() = List(4) { SignFrame(byteArrayOf(1), 640, 480) }

    @Test fun offlinePreservesLabelsAndSavesUsageOnce() = runTest {
        val local = MemoryConversation()
        val remote = StubTranslation().apply {
            expandAction = { throw BridgeException(BridgeError.NETWORK, "offline") }
        }
        val result = SendChipsUseCase(local, remote)(session, local.chips, null)
        assertEquals("Cà phê sữa", result.sentence)
        assertTrue(result.isOffline)
        assertEquals(1, local.turns.size)
        assertEquals(1, local.usage)
        assertTrue(local.turns.single().isOffline)
    }

    @Test fun invalidRequestDoesNotBecomeOfflineSuccess() = runTest {
        val local = MemoryConversation()
        val remote = StubTranslation().apply {
            expandAction = { throw BridgeException(BridgeError.INVALID_REQUEST, "invalid") }
        }
        try {
            SendChipsUseCase(local, remote)(session, local.chips, null)
            fail("Expected invalid request")
        } catch (error: BridgeException) { assertEquals(BridgeError.INVALID_REQUEST, error.code) }
        assertTrue(local.turns.isEmpty())
        assertEquals(0, local.usage)
    }

    @Test fun cancellationIsNotSavedAsOfflineTurn() = runTest {
        val local = MemoryConversation()
        val remote = StubTranslation().apply { expandAction = { throw CancellationException("cancel") } }
        try {
            SendChipsUseCase(local, remote)(session, local.chips, null)
            fail("Expected cancellation")
        } catch (_: CancellationException) { /* expected */ }
        assertTrue(local.turns.isEmpty())
    }

    @Test fun unknownSignCannotBePersistedAsConfirmedSpeech() = runTest {
        val local = MemoryConversation()
        val remote = StubTranslation().apply {
            sign = SignResult(listOf("UNKNOWN"), 0.99, "Câu đoán không được phát.", listOf("Có", "Không", "Cảm ơn"))
        }
        val result = RecognizeSignUseCase(local, remote)(session, frames, null)
        assertTrue(result.needsConfirmation)
        assertTrue(local.turns.isEmpty())
    }

    @Test fun quickReplySavesAndSpeaksWithoutTranslation() = runTest {
        val local = MemoryConversation()
        val device = StubDevice()
        SpeakQuickReplyUseCase(local, device)(session, "Cảm ơn")
        assertEquals("Cảm ơn", device.spoken)
        assertEquals("Cảm ơn", local.turns.single().processedText)
    }

    @Test fun contextMismatchIsRejectedBeforeCallingApi() = runTest {
        val local = MemoryConversation()
        val remote = StubTranslation()
        try {
            SendChipsUseCase(local, remote)(session.copy(context = ServiceContext.PUBLIC), local.chips, null)
            fail("Expected invalid selection")
        } catch (error: BridgeException) { assertEquals(BridgeError.INVALID_REQUEST, error.code) }
        assertEquals(0, remote.calls)
    }

    @Test fun sendChipsValidatesAllowedRangeOneToFive() = runTest {
        val local = MemoryConversation()
        val remote = StubTranslation()
        val useCase = SendChipsUseCase(local, remote)

        val chips5 = (1..5).map { QuickChip("chip-$it", ServiceContext.CAFE, "Thẻ $it", ChipCategory.DETAIL) }
        val result = useCase(session, chips5, null)
        assertEquals("Cho mình cà phê sữa.", result.sentence)

        val chips6 = (1..6).map { QuickChip("chip-$it", ServiceContext.CAFE, "Thẻ $it", ChipCategory.DETAIL) }
        try {
            useCase(session, chips6, null)
            fail("Expected invalid request for 6 chips")
        } catch (error: BridgeException) {
            assertEquals(BridgeError.INVALID_REQUEST, error.code)
            assertEquals("Chọn 1–5 thẻ trong ngữ cảnh hiện tại.", error.message)
        }
    }

    @Test fun framePolicyRejectsShortOversizedAndPartialClips() {
        assertEquals(0, CapturePolicy.usableFrameCount(4, 1499))
        assertEquals(4, CapturePolicy.usableFrameCount(7, 2999))
        assertEquals(12, CapturePolicy.usableFrameCount(15, 6000))
        assertEquals(16, CapturePolicy.usableFrameCount(20, 7000))
        assertThrows(BridgeException::class.java) { CapturePolicy.validateFrames(frames.take(3)) }
        assertThrows(BridgeException::class.java) {
            CapturePolicy.validateFrames(frames + SignFrame(byteArrayOf(1), 640, 480))
        }
        assertThrows(BridgeException::class.java) {
            CapturePolicy.validateFrames(List(4) { SignFrame(ByteArray(250 * 1024 + 1), 640, 480) })
        }
        assertThrows(BridgeException::class.java) {
            CapturePolicy.validateFrames(List(4) { SignFrame(byteArrayOf(1), 641, 480) })
        }
    }

    @Test fun audioPolicyRejectsOversizeDurationAndEmptyInput() {
        assertThrows(BridgeException::class.java) { CapturePolicy.validateAudio(RecordedAudio(byteArrayOf(1), 299)) }
        assertThrows(BridgeException::class.java) { CapturePolicy.validateAudio(RecordedAudio(byteArrayOf(1), 60001)) }
        assertThrows(BridgeException::class.java) { CapturePolicy.validateAudio(RecordedAudio(byteArrayOf(), 1000)) }
        assertThrows(BridgeException::class.java) {
            CapturePolicy.validateAudio(RecordedAudio(ByteArray(2 * 1024 * 1024 + 1), 1000))
        }
    }
}

