package com.example.ytclient.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.subStore: DataStore<Preferences> by preferencesDataStore("ytmaterial_subs")

/** Local subscriptions (no login needed): just a set of channel names. */
object SubscriptionManager {
    private val KEY = stringSetPreferencesKey("channels")
    private lateinit var store: DataStore<Preferences>

    fun init(context: Context) {
        store = context.applicationContext.subStore
    }

    val channels: Flow<List<String>> by lazy {
        store.data.map { p -> (p[KEY] ?: emptySet()).sortedBy { it.lowercase() } }
    }

    suspend fun toggle(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        store.edit { p ->
            val cur = (p[KEY] ?: emptySet()).toMutableSet()
            if (!cur.remove(n)) cur.add(n)
            p[KEY] = cur
        }
    }
}
