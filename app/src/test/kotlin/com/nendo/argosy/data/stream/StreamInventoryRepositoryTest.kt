package com.nendo.argosy.data.stream

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nendo.argosy.data.model.stream.InventoryEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StreamInventoryRepositoryTest {
    @get:Rule val temp = TemporaryFolder()
    private val jsonKey = stringPreferencesKey("stream_inventory_json")
    private val timeKey = longPreferencesKey("stream_inventory_fetched_at")
    private val hostKey = stringPreferencesKey("stream_inventory_host")
    private val hades = """[{"name":"Hades","appId":"1285314453"}]"""
    private val week = 7 * 24 * 60 * 60 * 1000L

    @Test fun `stores the three fields together and reads the typed pair`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val store = PreferenceDataStoreFactory.create(scope = scope) { temp.newFile("one.preferences_pb") }
        val repo = StreamInventoryRepository(store)
        repo.store("gamer", hades, 1000)
        val prefs = store.data.first()
        assertEquals(hades, prefs[jsonKey]); assertEquals(1000L, prefs[timeKey]); assertEquals("gamer", prefs[hostKey])
        assertEquals(setOf(InventoryEntry("Hades", "1285314453")), repo.snapshot("gamer", 2000)?.entries)
        assertNull(repo.snapshot("other", 2000))
        scope.cancel()
    }

    @Test fun `new host supersedes old host and clear removes every field`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val store = PreferenceDataStoreFactory.create(scope = scope) { temp.newFile("two.preferences_pb") }
        val repo = StreamInventoryRepository(store)
        repo.store("gamer", hades, 1000)
        repo.store("other", """[{"name":"Doom","appId":"7"}]""", 2000)
        repo.store("other", "[]", 2500)
        assertNull(repo.snapshot("gamer", 3000))
        assertEquals(setOf(InventoryEntry("Doom", "7")), repo.snapshot("other", 3000)?.entries)
        repo.clear()
        val prefs = store.data.first()
        assertNull(prefs[jsonKey]); assertNull(prefs[timeKey]); assertNull(prefs[hostKey])
        assertNull(repo.snapshot("other", 3000))
        scope.cancel()
    }

    @Test fun `dispatch snapshot checks the persisted host and expiry together`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val store = PreferenceDataStoreFactory.create(scope = scope) { temp.newFile("dispatch.preferences_pb") }
        val repo = StreamInventoryRepository(store)
        store.edit { it[stringPreferencesKey("stream_host")] = "gamer" }
        repo.store("gamer", hades, 0)
        assertEquals(setOf(InventoryEntry("Hades", "1285314453")), repo.currentHostSnapshot("gamer", week - 1).second?.entries)
        assertEquals(true to null, repo.currentHostSnapshot("gamer", week))
        store.edit { it[stringPreferencesKey("stream_host")] = "other" }
        assertEquals(false to null, repo.currentHostSnapshot("gamer", 1000))
        assertEquals(true to null, repo.currentHostSnapshot("other", 1000))
        scope.cancel()
    }

    @Test fun `expiry is inclusive at seven days`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val store = PreferenceDataStoreFactory.create(scope = scope) { temp.newFile("three.preferences_pb") }
        val repo = StreamInventoryRepository(store)
        repo.store("gamer", hades, 0)
        assertNotNull(repo.snapshot("gamer", week - 1))
        assertNull(repo.snapshot("gamer", week))
        scope.cancel()
    }

    @Test fun `absent and malformed persisted snapshots fail open`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val store = PreferenceDataStoreFactory.create(scope = scope) { temp.newFile("four.preferences_pb") }
        val repo = StreamInventoryRepository(store)
        assertNull(repo.snapshot("gamer", 1000))
        val invalid = listOf("[]", "{", "{}", "[{}]", """[{"name":" ","appId":"1"}]""", """[{"name":"H","appId":1}]""", """[{"name":"H","appId":"١"}]""", """[{"name":"H","appId":"1"},{}]""", """[{"name":"H","name":"X","appId":"1"}]""")
        for (payload in invalid) {
            store.edit { it[hostKey] = "gamer"; it[timeKey] = 0; it[jsonKey] = payload }
            assertNull(payload, repo.snapshot("gamer", 1000))
        }
        store.edit { it[jsonKey] = hades; it.remove(timeKey) }
        assertNull(repo.snapshot("gamer", 1000))
        scope.cancel()
    }
}
