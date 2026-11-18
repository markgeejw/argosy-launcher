package com.nendo.argosy.data.stream

import com.nendo.argosy.data.preferences.StreamPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SunshineInventoryRefresh internal constructor(
    private val repository: StreamInventoryRepository,
    private val preferences: StreamPreferencesRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val fetch: suspend (String) -> String?
) {
    @Inject constructor(repository: StreamInventoryRepository, preferences: StreamPreferencesRepository) :
        this(repository, preferences, fetch = { fetchInventory(it) })

    private val mutex = Mutex()

    suspend fun refresh() = refresh(clock())

    suspend fun refresh(nowMillis: Long) = mutex.withLock {
        val host = preferences.settings.first().host
        if (host.isBlank()) return@withLock
        val snapshot = repository.snapshot(host, nowMillis)
        if (snapshot != null && nowMillis - snapshot.fetchedAt < REFRESH_MILLIS) return@withLock
        val payload = fetch(host) ?: return@withLock
        repository.storeIfCurrentHost(host, payload, nowMillis)
    }

    companion object {
        private const val REFRESH_MILLIS = 24 * 60 * 60 * 1000L
        private val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()

        internal suspend fun fetchInventory(host: String, port: Int = 47991): String? = withContext(Dispatchers.IO) {
            try {
                val url = "http://localhost:47991/inventory.json".toHttpUrl().newBuilder()
                    .host(host).port(port).build()
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (response.code == 200) response.body?.string() else null
                }
            } catch (_: IOException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
