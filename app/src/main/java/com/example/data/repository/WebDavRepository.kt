package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "webdav_prefs")

class WebDavRepository(private val context: Context) {
    private object PreferencesKeys {
        val WEBDAV_URL = stringPreferencesKey("webdav_url")
        val WEBDAV_USER = stringPreferencesKey("webdav_user")
        val WEBDAV_PASS = stringPreferencesKey("webdav_pass")
    }

    val webDavUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.WEBDAV_URL] ?: ""
    }

    val webDavUser: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.WEBDAV_USER] ?: ""
    }

    suspend fun saveConfig(url: String, user: String, pass: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.WEBDAV_URL] = url
            prefs[PreferencesKeys.WEBDAV_USER] = user
            prefs[PreferencesKeys.WEBDAV_PASS] = pass
        }
    }

    suspend fun testConnection(url: String, user: String, pass: String): Boolean {
        // Simulate WebDAV connection test
        kotlinx.coroutines.delay(1200L)
        return url.isNotBlank() && (user.isNotBlank() || url.contains("localhost"))
    }
}
