package com.uzuu.signbridge.data

import android.app.Application
import com.uzuu.signbridge.data.local.AppDatabase
import com.uzuu.signbridge.data.local.dao.ConversationDao
import com.uzuu.signbridge.data.repository.ConversationRepositoryImpl
import com.uzuu.signbridge.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ConversationStorageTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ConversationRepositoryImpl

    @Before fun setup() {
        RuntimeEnvironment.getApplication().deleteDatabase("storage_test.db")
        reopen()
    }
    private fun reopen() {
        db = AppDatabase(RuntimeEnvironment.getApplication(), "storage_test.db")
        repo = ConversationRepositoryImpl(ConversationDao(db))
    }
    @After fun cleanup() {
        db.close()
        RuntimeEnvironment.getApplication().deleteDatabase("storage_test.db")
    }

    @Test fun customChipsTurnsAndUsageSurviveDatabaseReopen() = runBlocking {
        ServiceContext.entries.forEach { assertTrue(repo.getChips(it).isNotEmpty()) }
        val chip = repo.addChip(ServiceContext.PHARMACY, "Dị ứng penicillin", ChipCategory.DETAIL)
        val session = repo.createSession(ServiceContext.PHARMACY)
        repo.appendTurn(session.id, Direction.OUTGOING, chip.label, chip.label, usedChipIds = listOf(chip.id))
        db.close()
        reopen()
        val restored = repo.getChips(ServiceContext.PHARMACY).single { it.id == chip.id }
        assertEquals(1, restored.usageCount)
        assertTrue(restored.isPersonal)
        assertEquals(chip.label, repo.getHistory().single().processedText)
        repo.deletePersonalChip(chip.id)
        assertFalse(repo.getChips(ServiceContext.PHARMACY).any { it.id == chip.id })
        assertEquals(1, repo.getHistory().size)
    }

    @Test fun failedUsageUpdateRollsBackTurnInsert() = runBlocking {
        val session = repo.createSession(ServiceContext.CAFE)
        val chip = repo.getChips(ServiceContext.CAFE).first()
        try {
            repo.appendTurn(session.id, Direction.OUTGOING, chip.label, "Câu đã gửi",
                usedChipIds = listOf(chip.id, "missing-chip"))
            fail("Expected storage rollback")
        } catch (error: BridgeException) { assertEquals(BridgeError.STORAGE, error.code) }
        assertTrue(repo.getHistory().isEmpty())
        assertEquals(0, repo.getChips(ServiceContext.CAFE).single { it.id == chip.id }.usageCount)
    }

    @Test fun seedCannotBeDeletedAndDuplicatesAreRejected() = runBlocking {
        val seed = repo.getChips(ServiceContext.CAFE).first()
        try { repo.deletePersonalChip(seed.id); fail("Expected seed protection")
        } catch (error: BridgeException) { assertEquals(BridgeError.INVALID_REQUEST, error.code) }
        try { repo.addChip(ServiceContext.CAFE, seed.label.uppercase(), ChipCategory.DETAIL); fail("Expected duplicate")
        } catch (error: BridgeException) { assertEquals(BridgeError.INVALID_REQUEST, error.code) }
    }
}

