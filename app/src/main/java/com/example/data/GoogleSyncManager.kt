package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * User Profile & Google Account Authentication State
 */
data class GoogleUser(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val lastSyncTimestamp: Long = System.currentTimeMillis()
)

/**
 * Manages Google Account synchronization state, onboarding status, and universal sync bundle export/import.
 */
class GoogleSyncManager private constructor(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("alter_sync_prefs", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<GoogleUser?>(loadUserFromPrefs())
    val currentUser: StateFlow<GoogleUser?> = _currentUser.asStateFlow()

    private val _isOnboardingCompleted = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING_DONE, false))
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    private val _syncStatus = MutableStateFlow("All notes synced locally")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private fun loadUserFromPrefs(): GoogleUser? {
        val email = prefs.getString(KEY_USER_EMAIL, null) ?: return null
        val id = prefs.getString(KEY_USER_ID, "user_${System.currentTimeMillis()}") ?: ""
        val name = prefs.getString(KEY_USER_NAME, email.substringBefore("@").replaceFirstChar { it.uppercase() }) ?: "User"
        val avatar = prefs.getString(KEY_USER_AVATAR, null)
        val syncTime = prefs.getLong(KEY_LAST_SYNC, System.currentTimeMillis())
        return GoogleUser(id, email, name, avatar, syncTime)
    }

    fun completeOnboarding(user: GoogleUser? = null) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
        _isOnboardingCompleted.value = true
        if (user != null) {
            signIn(user)
        }
    }

    fun signIn(user: GoogleUser) {
        prefs.edit()
            .putString(KEY_USER_ID, user.id)
            .putString(KEY_USER_EMAIL, user.email)
            .putString(KEY_USER_NAME, user.displayName)
            .putString(KEY_USER_AVATAR, user.avatarUrl)
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            .apply()
        _currentUser.value = user
        _syncStatus.value = "Synced with Google Drive (${user.email})"
    }

    fun signOut() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_AVATAR)
            .apply()
        _currentUser.value = null
        _syncStatus.value = "Offline storage active"
    }

    fun markSyncCompleted() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_SYNC, now).apply()
        val user = _currentUser.value
        if (user != null) {
            _currentUser.value = user.copy(lastSyncTimestamp = now)
            _syncStatus.value = "Synced: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now))}"
        }
    }

    /**
     * Generates a Universal Sync JSON package of all cards and canvas boards
     * for seamless cross-platform desktop / web interoperability.
     */
    fun createUniversalSyncBundle(cards: List<AlterCard>): String {
        val root = JSONObject()
        root.put("schemaVersion", 2)
        root.put("appName", "Alter Space")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("userEmail", _currentUser.value?.email ?: "local_user")

        val cardsArray = JSONArray()
        for (c in cards) {
            val cardObj = JSONObject().apply {
                put("id", c.id)
                put("type", c.type)
                put("rawInput", c.rawInput)
                put("processedContent", c.processedContent)
                put("personalNotes", c.personalNotes)
                put("timestamp", c.timestamp)
                put("isDeleted", c.isDeleted)
                if (!c.canvasData.isNullOrBlank()) {
                    put("canvasData", JSONObject(c.canvasData))
                }
            }
            cardsArray.put(cardObj)
        }
        root.put("cards", cardsArray)
        return root.toString(2)
    }

    companion object {
        private const val KEY_ONBOARDING_DONE = "onboarding_completed"
        private const val KEY_USER_ID = "google_user_id"
        private const val KEY_USER_EMAIL = "google_user_email"
        private const val KEY_USER_NAME = "google_user_name"
        private const val KEY_USER_AVATAR = "google_user_avatar"
        private const val KEY_LAST_SYNC = "google_last_sync"

        @Volatile
        private var INSTANCE: GoogleSyncManager? = null

        fun getInstance(context: Context): GoogleSyncManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GoogleSyncManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
