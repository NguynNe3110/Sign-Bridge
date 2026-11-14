package com.uzuu.signbridge.data.session

import android.content.SharedPreferences
import java.util.UUID

class UserSessionManager(private val preferences: SharedPreferences) {

    val userId: String
        get() {
            val existing = preferences.getString(KEY_USER_ID, null)
            if (!existing.isNullOrBlank()) return existing
            val generated = UUID.randomUUID().toString()
            preferences.edit().putString(KEY_USER_ID, generated).commit()
            return generated
        }

    companion object {
        private const val KEY_USER_ID = "pref_user_id"
    }
}
