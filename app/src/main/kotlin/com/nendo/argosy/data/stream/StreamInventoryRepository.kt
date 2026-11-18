package com.nendo.argosy.data.stream

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nendo.argosy.data.model.stream.InventoryEntry
import com.nendo.argosy.data.model.stream.InventorySnapshot
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamInventoryRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    internal object Keys {
        val JSON = stringPreferencesKey("stream_inventory_json")
        val FETCHED_AT = longPreferencesKey("stream_inventory_fetched_at")
        val HOST = stringPreferencesKey("stream_inventory_host")
    }

    private val jsonAdapter = Moshi.Builder().build().adapter(Any::class.java)

    fun parse(payload: String): Set<InventoryEntry>? {
        val values = try {
            jsonAdapter.fromJson(payload) as? List<*> ?: return null
        } catch (_: java.io.IOException) {
            return null
        } catch (_: JsonDataException) {
            return null
        }
        if (values.isEmpty()) return null
        return values.map { value ->
            val entry = value as? Map<*, *> ?: return null
            val name = entry["name"] as? String ?: return null
            val appId = entry["appId"] as? String ?: return null
            if (name.isBlank() || appId.isEmpty() || appId.any { it !in '0'..'9' }) return null
            InventoryEntry(name, appId)
        }.toSet()
    }

    suspend fun store(host: String, payload: String, fetchedAt: Long) {
        if (parse(payload) == null) return
        dataStore.edit { prefs ->
            prefs[Keys.JSON] = payload
            prefs[Keys.FETCHED_AT] = fetchedAt
            prefs[Keys.HOST] = host
        }
    }

    suspend fun storeIfCurrentHost(host: String, payload: String, fetchedAt: Long) {
        if (parse(payload) == null) return
        dataStore.edit { prefs ->
            val currentHost = prefs[stringPreferencesKey("stream_host")]
                ?: com.nendo.argosy.data.model.stream.StreamSettings.DEFAULT_HOST
            if (currentHost == host) {
                prefs[Keys.JSON] = payload
                prefs[Keys.FETCHED_AT] = fetchedAt
                prefs[Keys.HOST] = host
            }
        }
    }

    suspend fun snapshot(host: String, nowMillis: Long): InventorySnapshot? {
        val prefs = dataStore.data.first()
        if (prefs[Keys.HOST] != host) return null
        val fetchedAt = prefs[Keys.FETCHED_AT] ?: return null
        if (nowMillis - fetchedAt >= EXPIRY_MILLIS) return null
        val entries = parse(prefs[Keys.JSON] ?: return null) ?: return null
        return InventorySnapshot(entries, fetchedAt)
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.JSON)
            prefs.remove(Keys.FETCHED_AT)
            prefs.remove(Keys.HOST)
        }
    }

    companion object {
        private const val EXPIRY_MILLIS = 7 * 24 * 60 * 60 * 1000L
    }
}
