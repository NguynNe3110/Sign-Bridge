package com.uzuu.signbridge.data

import android.app.Application
import android.content.Context
import com.uzuu.signbridge.data.local.AppDatabase
import com.uzuu.signbridge.data.local.dao.ConversationDao
import com.uzuu.signbridge.data.local.entity.QuickChipEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ConversationDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ConversationDao

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        val dbName = "test_conversation_dao_${UUID.randomUUID()}.db"
        db = AppDatabase(context, dbName)
        dao = ConversationDao(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun databaseSeedHas15ChipsPerDomain() {
        listOf("CAFE", "PHARMACY", "PUBLIC", "CUSTOM").forEach { contextId ->
            val chips = dao.chips(contextId)
            assertEquals("Domain $contextId should have 15 seed chips", 15, chips.size)
        }
    }

    @Test
    fun syncChipsMergesWithoutRemovingDefaultsOrOtherContexts() {
        val initialPharmacy = dao.chips("PHARMACY")

        val newCafeChips = listOf(
            QuickChipEntity("sync-1", "CAFE", "Trà đào cam sả", "DETAIL", 3, false),
            QuickChipEntity("sync-2", "CAFE", "Bánh ngọt", "DETAIL", 0, true),
        )

        dao.syncChips("CAFE", newCafeChips)

        val updatedCafe = dao.chips("CAFE")
        assertEquals(17, updatedCafe.size)
        assertEquals("Trà đào cam sả", updatedCafe[0].label)
        assertTrue(updatedCafe.any { it.label == "Bánh ngọt" })
        assertEquals(15, updatedCafe.count { it.id.startsWith("seed_") })

        val pharmacyAfter = dao.chips("PHARMACY")
        assertEquals(initialPharmacy.size, pharmacyAfter.size)
        assertEquals(initialPharmacy.map { it.id }, pharmacyAfter.map { it.id })
    }
}
