package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nendo.argosy.data.model.stream.StreamSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val HOST = stringPreferencesKey("stream_host")
        val MOONLIGHT_PACKAGE = stringPreferencesKey("stream_moonlight_package")
        val PLATFORMS = stringSetPreferencesKey("stream_platforms")
    }

    val settings: Flow<StreamSettings> = dataStore.data.map { prefs ->
        StreamSettings(
            host = prefs[Keys.HOST] ?: StreamSettings.DEFAULT_HOST,
            moonlightPackage = prefs[Keys.MOONLIGHT_PACKAGE] ?: StreamSettings.DEFAULT_MOONLIGHT_PACKAGE,
            platforms = prefs[Keys.PLATFORMS] ?: StreamSettings.DEFAULT_PLATFORMS
        )
    }

    suspend fun setHost(host: String) {
        dataStore.edit { it[Keys.HOST] = host.trim() }
    }

    suspend fun setMoonlightPackage(packageName: String) {
        dataStore.edit { it[Keys.MOONLIGHT_PACKAGE] = packageName.trim() }
    }

    suspend fun setPlatformEnabled(slug: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.PLATFORMS] ?: StreamSettings.DEFAULT_PLATFORMS
            prefs[Keys.PLATFORMS] = if (enabled) current + slug else current - slug
        }
    }
}
