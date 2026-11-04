package com.example.ytclient.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.authStore: DataStore<Preferences> by preferencesDataStore("ytmaterial_auth")

object AuthManager {
    private val KEY_ACCESS = stringPreferencesKey("access_token")
    private val KEY_REFRESH = stringPreferencesKey("refresh_token")
    private val KEY_EXPIRES_AT = longPreferencesKey("expires_at")

    private lateinit var store: DataStore<Preferences>

    fun init(context: Context) {
        store = context.applicationContext.authStore
    }

    val signedIn: Flow<Boolean> by lazy { store.data.map { it[KEY_REFRESH] != null } }

    suspend fun save(tokens: Tokens) {
        store.edit {
            it[KEY_ACCESS] = tokens.accessToken
            it[KEY_REFRESH] = tokens.refreshToken
            it[KEY_EXPIRES_AT] = System.currentTimeMillis() + tokens.expiresInSec * 1000L
        }
    }

    /** A valid access token, refreshed when it is about to expire; null when signed out. */
    suspend fun accessToken(): String? {
        val p = store.data.first()
        val refresh = p[KEY_REFRESH] ?: return null
        val access = p[KEY_ACCESS]
        val expiresAt = p[KEY_EXPIRES_AT] ?: 0L
        if (access != null && System.currentTimeMillis() < expiresAt - 60_000L) return access
        return refreshNow(refresh)
    }

    /** Ignores the cached expiry (used after the server answered 401/403). */
    suspend fun forceRefresh(): String? {
        val refresh = store.data.first()[KEY_REFRESH] ?: return null
        return refreshNow(refresh)
    }

    private suspend fun refreshNow(refresh: String): String? {
        val t = runCatching { DeviceCodeAuth.refresh(refresh) }.getOrNull() ?: return null
        store.edit {
            it[KEY_ACCESS] = t.accessToken
            it[KEY_EXPIRES_AT] = System.currentTimeMillis() + t.expiresInSec * 1000L
        }
        return t.accessToken
    }

    suspend fun signOut() {
        store.edit {
            it.remove(KEY_ACCESS)
            it.remove(KEY_REFRESH)
            it.remove(KEY_EXPIRES_AT)
        }
    }
}
