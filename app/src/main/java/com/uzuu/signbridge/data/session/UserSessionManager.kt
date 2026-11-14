package com.uzuu.signbridge.data.session

import android.content.SharedPreferences
import java.util.UUID

class UserSessionManager(private val preferences: SharedPreferences) {

    @Volatile
    private var cachedUserId: String? = null

    val userId: String
        @Synchronized
        get() {
            cachedUserId?.let { return it }
            val existing = preferences.getString(KEY_USER_ID, null)
            if (!existing.isNullOrBlank()) {
                cachedUserId = existing
                return existing
            }
            val generated = UUID.randomUUID().toString()
            preferences.edit().putString(KEY_USER_ID, generated).commit()
            cachedUserId = generated
            return generated
        }

    companion object {
        private const val KEY_USER_ID = "pref_user_id"
    }
}
