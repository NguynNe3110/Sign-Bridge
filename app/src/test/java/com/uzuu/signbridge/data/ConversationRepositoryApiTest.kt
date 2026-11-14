package com.uzuu.signbridge.data

import android.app.Application
import android.content.Context
import com.uzuu.signbridge.data.local.AppDatabase
import com.uzuu.signbridge.data.local.dao.ConversationDao
import com.uzuu.signbridge.data.remote.api.BridgeApi
import com.uzuu.signbridge.data.repository.ConversationRepositoryImpl
import com.uzuu.signbridge.data.session.UserSessionManager
import com.uzuu.signbridge.domain.model.BridgeError
import com.uzuu.signbridge.domain.model.BridgeException
import com.uzuu.signbridge.domain.model.ChipCategory
import com.uzuu.signbridge.domain.model.ServiceContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ConversationRepositoryApiTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var db: AppDatabase
    private lateinit var dao: ConversationDao
    private lateinit var session: UserSessionManager
    private lateinit var repo: ConversationRepositoryImpl

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build()

        val context = RuntimeEnvironment.getApplication()
        val dbName = "test_repo_api_${UUID.randomUUID()}.db"
        db = AppDatabase(context, dbName)
        dao = ConversationDao(db)

        val prefs = context.getSharedPreferences("test_session_${UUID.randomUUID()}", Context.MODE_PRIVATE)
        prefs.edit().putString("pref_user_id", "test-user-id-456").commit()
        session = UserSessionManager(prefs)

        val api = BridgeApi(client, server.url("/").toString(), allowHttp = true)
        repo = ConversationRepositoryImpl(dao, api, session, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        client.connectionPool.evictAll()
        server.shutdown()
        client.dispatcher.executorService.shutdown()
        db.close()
    }

    private fun jsonResponse(body: String, status: Int = 200) =
        MockResponse().setResponseCode(status)
            .setHeader("Content-Type", "application/json")
            .setBody(body)

    @Test
    fun getChipsFetchesRemoteAndSyncsToDaoCache() = runBlocking {
        server.enqueue(jsonResponse("""
            [
                {"id":"chip-1","text":"Trà sữa chân trâu","context":"CAFE","is_system":true,"user_id":"SYSTEM","usage_count":0},
                {"id":"chip-2","text":"Ít ngọt","context":"CAFE","is_system":false,"user_id":"test-user-id-456","usage_count":1}
            ]
        """.trimIndent()))

        val chips = repo.getChips(ServiceContext.CAFE)
        assertEquals(2, chips.size)
        assertEquals("Trà sữa chân trâu", chips[0].label)
        assertFalse(chips[0].isPersonal)
        assertEquals("Ít ngọt", chips[1].label)
        assertTrue(chips[1].isPersonal)

        // Verify request was sent with proper query and header
        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/chips?context=CAFE&user_id=test-user-id-456", recorded.path)
        assertEquals("test-user-id-456", recorded.getHeader("X-User-Id"))

        // Verify SQLite cache was synced
        val cached = dao.chips("CAFE")
        assertEquals(2, cached.size)
    }

    @Test
    fun getChipsFallsBackToLocalCacheWhenServerFails() = runBlocking {
        // Enqueue 500 error from server
        server.enqueue(jsonResponse("""{"detail":"Server error"}""", 500))

        // When server fails, getChips should not crash, but return cached seed chips from SQLite
        val chips = repo.getChips(ServiceContext.CAFE)
        assertTrue("Should fall back to seed chips", chips.isNotEmpty())
        assertEquals(15, chips.size)
    }

    @Test
    fun addChipCallsRemotePostAndCachesResult() = runBlocking {
        server.enqueue(jsonResponse("""
            {"id":"new-chip-uuid","text":"Trà lài","context":"CAFE","is_system":false,"user_id":"test-user-id-456","usage_count":0}
        """.trimIndent()))

        val chip = repo.addChip(ServiceContext.CAFE, "Trà lài", ChipCategory.DETAIL)
        assertEquals("new-chip-uuid", chip.id)
        assertEquals("Trà lài", chip.label)
        assertTrue(chip.isPersonal)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/chips", recorded.path)
        val body = JSONObject(recorded.body.readUtf8())
        assertEquals("Trà lài", body.getString("text"))
        assertEquals("CAFE", body.getString("context"))
        assertEquals("test-user-id-456", body.getString("user_id"))

        // Verify in SQLite cache
        val inCache = dao.chips("CAFE").firstOrNull { it.id == "new-chip-uuid" }
        assertEquals("Trà lài", inCache?.label)
    }

    @Test
    fun deletePersonalChipCallsRemoteDeleteAndRemovesFromCache() = runBlocking {
        // Seed a personal chip directly into cache
        val chipId = "personal-chip-to-delete"
        dao.insertChip(com.uzuu.signbridge.data.local.entity.QuickChipEntity(chipId, "CAFE", "Bánh su kem", "DETAIL", 0, true))

        // Server returns success: true for DELETE
        server.enqueue(jsonResponse("""{"success":true}"""))
        repo.deletePersonalChip(chipId)

        val recorded = server.takeRequest()
        assertEquals("DELETE", recorded.method)
        assertEquals("/api/chips/$chipId?user_id=test-user-id-456", recorded.path)

        // Verify deleted from cache
        val inCache = dao.chips("CAFE").firstOrNull { it.id == chipId }
        assertEquals(null, inCache)
    }

    @Test
    fun deletePersonalChipThrowsWhenServerReturnsSuccessFalse() = runBlocking {
        server.enqueue(jsonResponse("""{"success":false}"""))

        try {
            repo.deletePersonalChip("system-chip-id")
            fail("Expected BridgeException")
        } catch (e: BridgeException) {
            assertEquals(BridgeError.INVALID_REQUEST, e.code)
        }
    }

    @Test
    fun updateChipOnPersonalChipDeletesAndCreatesNewChip() = runBlocking {
        // Seed a personal chip in DB with unique label
        dao.insertChip(com.uzuu.signbridge.data.local.entity.QuickChipEntity("old-personal-id", "CAFE", "Trà ô long", "DETAIL", 0, true))

        server.enqueue(jsonResponse("""{"success":true}""")) // for DELETE
        server.enqueue(jsonResponse("""
            {"id":"new-personal-id","text":"Trà ô long đặc biệt","context":"CAFE","is_system":false,"user_id":"test-user-id-456","usage_count":0}
        """.trimIndent())) // for POST

        repo.updateChip("old-personal-id", "Trà ô long đặc biệt")

        val deleteReq = server.takeRequest()
        assertEquals("DELETE", deleteReq.method)
        assertEquals("/api/chips/old-personal-id?user_id=test-user-id-456", deleteReq.path)

        val postReq = server.takeRequest()
        assertEquals("POST", postReq.method)
        val postBody = JSONObject(postReq.body.readUtf8())
        assertEquals("Trà ô long đặc biệt", postBody.getString("text"))
    }
}
