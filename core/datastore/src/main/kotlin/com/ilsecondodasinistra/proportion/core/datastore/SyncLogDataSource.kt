package com.ilsecondodasinistra.proportion.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ilsecondodasinistra.proportion.core.model.SyncLogEntry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The folder sync (phase 10) activity log: capped to the most recent [MAX_ENTRIES], persisted
 * because sync also runs from a background `WorkManager` job, not only while the app is open.
 *
 * Encoded by hand (one line per entry, ``-separated fields) rather than JSON, so this module
 * does not need a serialization dependency for a handful of short strings.
 */
@Singleton
class SyncLogDataSource @Inject constructor(
    private val store: DataStore<Preferences>,
) {

    val entries: Flow<List<SyncLogEntry>> = store.data.map { stored -> decode(stored[LOG].orEmpty()) }

    suspend fun append(entry: SyncLogEntry) {
        store.edit { prefs ->
            val updated = (decode(prefs[LOG].orEmpty()) + entry).takeLast(MAX_ENTRIES)
            prefs[LOG] = encode(updated)
        }
    }

    private fun encode(entries: List<SyncLogEntry>): String = entries.joinToString("\n") { entry ->
        listOf(entry.timestamp.toString(), if (entry.isError) "1" else "0", entry.message.replace("\n", " "))
            .joinToString(SEPARATOR)
    }

    private fun decode(raw: String): List<SyncLogEntry> = raw.lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split(SEPARATOR, limit = FIELD_COUNT)
            val timestamp = parts.getOrNull(0)?.toLongOrNull()
            if (parts.size != FIELD_COUNT || timestamp == null) return@mapNotNull null
            SyncLogEntry(timestamp = timestamp, isError = parts[1] == "1", message = parts[2])
        }
        .toList()

    private companion object {
        val LOG = stringPreferencesKey("sync_log")
        const val SEPARATOR = ""
        const val FIELD_COUNT = 3
        const val MAX_ENTRIES = 50
    }
}
