package com.example.ytclient.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.historyStore: DataStore<Preferences> by preferencesDataStore("ytmaterial_history")

/** Recent searches, newest first, stored locally (max 20). */
object SearchHistory {
    private val KEY = stringPreferencesKey("items")
    private const val MAX = 20
    private lateinit var store: DataStore<Preferences>

    fun init(context: Context) {
        store = context.applicationContext.historyStore
    }

    val items: Flow<List<String>> by lazy {
        store.data.map { p -> p[KEY].orEmpty().split('\n').filter { it.isNotBlank() } }
    }

    private fun decode(p: Preferences) = p[KEY].orEmpty().split('\n').filter { it.isNotBlank() }

    suspend fun add(query: String) {
        val q = query.trim().replace('\n', ' ')
        if (q.isEmpty()) return
        store.edit { p ->
            val cur = decode(p).filterNot { it.equals(q, ignoreCase = true) }
            p[KEY] = (listOf(q) + cur).take(MAX).joinToString("\n")
        }
    }

    suspend fun remove(query: String) {
        store.edit { p -> p[KEY] = decode(p).filterNot { it == query }.joinToString("\n") }
    }

    suspend fun clear() {
        store.edit { it.remove(KEY) }
    }
}
