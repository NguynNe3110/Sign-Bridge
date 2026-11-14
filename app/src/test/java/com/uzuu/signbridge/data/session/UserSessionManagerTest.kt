package com.uzuu.signbridge.data.session

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class UserSessionManagerTest {

    @Test
    fun generatesAndPersistsStableUserId() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_session_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val manager1 = UserSessionManager(prefs)
        val id1 = manager1.userId
        assertTrue("UserId should not be blank", id1.isNotBlank())

        val id2 = manager1.userId
        assertEquals("Subsequent calls on same manager should return same id", id1, id2)

        val manager2 = UserSessionManager(prefs)
        val id3 = manager2.userId
        assertEquals("New manager instance with same prefs should restore persisted id", id1, id3)
    }

    @Test
    fun reusesExistingUserIdInPrefs() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("test_session_existing", Context.MODE_PRIVATE)
        prefs.edit().putString("pref_user_id", "custom-pre-existing-user-uuid").commit()

        val manager = UserSessionManager(prefs)
        assertEquals("custom-pre-existing-user-uuid", manager.userId)
    }
}
