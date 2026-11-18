package com.nendo.argosy.ui.screens.stream

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nendo.argosy.data.model.stream.StreamSettings
import com.nendo.argosy.data.preferences.NotOnDeviceAction
import com.nendo.argosy.data.preferences.StreamPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.remote.romm.RomMStreamingContainer
import com.nendo.argosy.data.stream.StreamInventoryRepository
import com.nendo.argosy.data.stream.StreamKind
import com.nendo.argosy.data.streaming.StreamingConfigStore
import com.nendo.argosy.data.streaming.StreamSessionManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StreamInventoryExpiryFlowTest {
    @get:Rule val temp = TemporaryFolder()
    private val week = 7 * 24 * 60 * 60 * 1000L
    private val age = 25 * 60 * 60 * 1000L
    private val payload = """[{"name":"Other","appId":"1"}]"""

    @Test fun `real expiry changes offers and takeover with and without container`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { temp.newFile("expiry.preferences_pb") }
        val repo = StreamInventoryRepository(store)
        val now = System.currentTimeMillis()
        repo.store("gamer", payload, now - age)
        val preferences = MutableStateFlow(UserPreferences(notOnDeviceAction = NotOnDeviceAction.STREAM,
            streamSettings = StreamSettings(host = "gamer")))
        val sessions = mockk<StreamSessionManager> { every { session } returns MutableStateFlow(null) }
        fun controller(container: Boolean): StreamLaunchController {
            val config = mockk<StreamingConfigStore> {
                every { availability } returns MutableStateFlow(mockk(relaxed = true))
                every { containerFor(any()) } returns if (container) RomMStreamingContainer(platform = "switch", label = "eden") else null
                every { streamablePlatforms() } returns if (container) setOf("switch") else emptySet()
            }
            return StreamLaunchController.Factory(mockk(), mockk(), config, sessions, mockk(), mockk(), mockk(), mockk(),
                mockk { every { userPreferences } returns preferences },
                mockk { every { snapshots() } returns repo.snapshots { now + testScheduler.currentTime } }
            ).create(backgroundScope)
        }
        val noContainer = controller(false)
        val fallback = controller(true)
        fun offer(c: StreamLaunchController) = c.offerFor(7, "switch", 29, "Hades", false, false, preferences.value.streamSettings)?.kind
        fun leads(c: StreamLaunchController) = c.streamFirst.value.leads("switch", true, false, 29, "Hades")
        runCurrent()
        assertNull(offer(noContainer))
        assertEquals(false, leads(noContainer))
        assertEquals(StreamKind.CONTAINER, offer(fallback))
        advanceTimeBy(week - age - 1)
        runCurrent()
        assertNull(offer(noContainer))
        assertEquals(false, leads(noContainer))
        assertEquals(StreamKind.CONTAINER, offer(fallback))
        advanceTimeBy(1)
        runCurrent()
        assertEquals(StreamKind.MOONLIGHT, offer(noContainer))
        assertEquals(true, leads(noContainer))
        assertEquals(StreamKind.MOONLIGHT, offer(fallback))
    }

    @Test fun `host replacement atomically invalidates the real snapshot flow`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { temp.newFile("host.preferences_pb") }
        val repo = StreamInventoryRepository(store)
        val hosts = StreamPreferencesRepository(store)
        hosts.setHost("gamer")
        repo.store("gamer", payload, 0)
        val emissions = mutableListOf<Pair<String, com.nendo.argosy.data.model.stream.InventorySnapshot>?>()
        backgroundScope.launch { repo.snapshots { age + testScheduler.currentTime }.collect { emissions.add(it) } }
        runCurrent()
        assertEquals("gamer", emissions.single()?.first)
        hosts.setHost("other")
        runCurrent()
        assertEquals(2, emissions.size)
        assertNull(emissions.last())
        val prefs = store.data.first()
        assertNull(prefs[StreamInventoryRepository.Keys.HOST])
        assertNull(prefs[StreamInventoryRepository.Keys.JSON])
        assertNull(prefs[StreamInventoryRepository.Keys.FETCHED_AT])
    }
}
