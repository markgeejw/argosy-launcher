package com.nendo.argosy.data.stream

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import com.nendo.argosy.data.preferences.StreamPreferencesRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StreamInventoryFetchTest {
    @get:Rule val temp = TemporaryFolder()
    private val old = """[{"name":"H","appId":"1"}]"""
    private val fresh = """[{"name":"N","appId":"2"}]"""
    private val day = 24 * 60 * 60 * 1000L

    @Test fun `refresh respects inclusive ttl and rejects invalid responses`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val store = PreferenceDataStoreFactory.create(scope = scope) { temp.newFile("fetch.preferences_pb") }
        val prefs = StreamPreferencesRepository(store)
        prefs.setHost("g")
        val repo = StreamInventoryRepository(store)
        repo.store("g", old, 0)
        var calls = 0
        var response: String? = fresh
        val refresh = SunshineInventoryRefresh(repo, prefs) { calls++; response }
        refresh.refresh( day - 1)
        assertEquals(0, calls)
        response = null
        refresh.refresh(day)
        assertEquals(1, calls)
        assertEquals(0L, repo.snapshot("g", day)?.fetchedAt)
        response = "[]"
        refresh.refresh(day + 1)
        assertEquals(0L, repo.snapshot("g", day)?.fetchedAt)
        response = fresh
        refresh.refresh(day + 2)
        assertEquals(day + 2, repo.snapshot("g", day + 2)?.fetchedAt)
        scope.cancel()
    }

    @Test fun `host changed during fetch prevents commit`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val store = PreferenceDataStoreFactory.create(scope = scope) { temp.newFile("host.preferences_pb") }
        val prefs = StreamPreferencesRepository(store)
        prefs.setHost("g")
        val repo = StreamInventoryRepository(store)
        repo.store("g", old, 0)
        val refresh = SunshineInventoryRefresh(repo, prefs) { prefs.setHost("other"); fresh }
        refresh.refresh(day)
        assertNull(repo.snapshot("g", day))
        assertNull(repo.snapshot("other", day))
        scope.cancel()
    }
    @Test fun `invalid payloads preserve complete previous cache`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val store = PreferenceDataStoreFactory.create(scope = scope) { temp.newFile("invalid.preferences_pb") }
        val prefs = StreamPreferencesRepository(store)
        prefs.setHost("g")
        val repo = StreamInventoryRepository(store)
        repo.store("g", old, 0)
        var response: String? = null
        val refresh = SunshineInventoryRefresh(repo, prefs) { response }
        val invalid = listOf(null, "{", "[]", "[{}]", """[{"name":" ","appId":"1"}]""",
            """[{"name":1,"appId":"1"}]""", """[{"name":"H"}]""",
            """[{"name":"H","appId":1}]""", """[{"name":"H","appId":"x"}]""",
            """[{"name":"H","appId":"1"},{}]""")
        for (payload in invalid) {
            response = payload
            refresh.refresh(day + 60 * 60 * 1000)
            assertEquals(payload, 0L, repo.snapshot("g", day)?.fetchedAt)
            assertEquals(payload, old, store.data.first()[stringPreferencesKey("stream_inventory_json")])
        }
        scope.cancel()
    }

    @Test fun `host setter removes all fields in same edit`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val store = PreferenceDataStoreFactory.create(scope = scope) { temp.newFile("atomic.preferences_pb") }
        val prefs = StreamPreferencesRepository(store)
        prefs.setHost("g")
        val repo = StreamInventoryRepository(store)
        repo.store("g", old, 0)
        prefs.setHost("other")
        val result = store.data.first()
        assertEquals("other", result[stringPreferencesKey("stream_host")])
        assertNull(result[stringPreferencesKey("stream_inventory_json")])
        assertNull(result[stringPreferencesKey("stream_inventory_host")])
        assertNull(result[longPreferencesKey("stream_inventory_fetched_at")])
        scope.cancel()
    }

    @Test fun `http requires status 200 and requests inventory path`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            for (status in listOf(204, 500, 302)) {
                server.enqueue(MockResponse().setResponseCode(status))
                assertNull(SunshineInventoryRefresh.fetchInventory("127.0.0.1", server.port))
                assertEquals("/inventory.json", server.takeRequest().path)
            }
            server.enqueue(MockResponse().setResponseCode(200).setBody(fresh))
            assertEquals(fresh, SunshineInventoryRefresh.fetchInventory("127.0.0.1", server.port))
            assertEquals("/inventory.json", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    private class InstrumentedStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
        override val data = delegate.data
        val updates = mutableListOf<Preferences>()
        var beforeUpdate: (suspend () -> Unit)? = null

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            beforeUpdate?.invoke()
            return delegate.updateData { transform(it).also(updates::add) }
        }
    }

    @Test fun `host replacement clears cache in the host update itself`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val store = InstrumentedStore(PreferenceDataStoreFactory.create(scope = scope) { temp.newFile("updates.preferences_pb") })
        val prefs = StreamPreferencesRepository(store)
        val repo = StreamInventoryRepository(store)
        prefs.setHost("g")
        repo.store("g", old, 0)
        store.updates.clear()
        prefs.setHost("other")
        assertEquals(1, store.updates.size)
        val changed = store.updates.single()
        assertEquals("other", changed[stringPreferencesKey("stream_host")])
        assertNull(changed[stringPreferencesKey("stream_inventory_json")])
        assertNull(changed[stringPreferencesKey("stream_inventory_host")])
        assertNull(changed[longPreferencesKey("stream_inventory_fetched_at")])
        scope.cancel()
    }

    @Test fun `host change between response and cache transform cannot commit old response`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val backing = PreferenceDataStoreFactory.create(scope = scope) { temp.newFile("race.preferences_pb") }
        val store = InstrumentedStore(backing)
        val prefs = StreamPreferencesRepository(store)
        val repo = StreamInventoryRepository(store)
        prefs.setHost("g")
        repo.store("g", old, 0)
        val waiting = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        store.beforeUpdate = { waiting.complete(Unit); resume.await() }
        val refresh = SunshineInventoryRefresh(repo, prefs) { fresh }
        val job = async { refresh.refresh(day) }
        waiting.await()
        StreamPreferencesRepository(backing).setHost("other")
        resume.complete(Unit)
        job.await()
        val result = backing.data.first()
        assertEquals("other", result[stringPreferencesKey("stream_host")])
        assertNull(result[stringPreferencesKey("stream_inventory_json")])
        assertNull(result[stringPreferencesKey("stream_inventory_host")])
        assertNull(result[longPreferencesKey("stream_inventory_fetched_at")])
        scope.cancel()
    }

}
