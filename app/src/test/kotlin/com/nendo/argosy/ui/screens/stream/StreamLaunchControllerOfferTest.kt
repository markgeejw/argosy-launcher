package com.nendo.argosy.ui.screens.stream

import com.nendo.argosy.data.model.stream.StreamSettings
import com.nendo.argosy.data.model.stream.InventoryEntry
import com.nendo.argosy.data.model.stream.InventorySnapshot
import com.nendo.argosy.data.preferences.NotOnDeviceAction
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.remote.romm.RomMStreamingContainer
import com.nendo.argosy.data.stream.StreamKind
import com.nendo.argosy.data.streaming.StreamingConfigStore
import com.nendo.argosy.data.streaming.StreamSessionManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamLaunchControllerOfferTest {
    @Test
    fun `offer selects moonlight before container and preserves container fallback`() = runTest {
        val container = RomMStreamingContainer(platform = "switch", label = "eden")
        val config = mockk<StreamingConfigStore> {
            every { availability } returns MutableStateFlow(mockk(relaxed = true))
            every { containerFor(any()) } returns container
            every { streamablePlatforms() } returns emptySet()
        }
        val sessions = mockk<StreamSessionManager> {
            every { session } returns MutableStateFlow(null)
        }
        val preferences = mockk<com.nendo.argosy.data.preferences.UserPreferencesRepository> {
            every { userPreferences } returns flowOf(UserPreferences())
        }
        val factory = StreamLaunchController.Factory(
            mockk(), mockk(), config, sessions, mockk(), mockk(), mockk(), mockk(), preferences,
            mockk { every { snapshots() } returns flowOf(null) }
        )
        val controller = factory.create(backgroundScope)
        val settings = StreamSettings()
        val moonlight = controller.offerFor(7, "switch", 29, "Zelda", false, false, settings)
        assertEquals(StreamKind.MOONLIGHT, moonlight?.kind)
        val fallback = controller.offerFor(7, "switch", 29, "Zelda", false, false, settings.copy(platforms = emptySet()))
        assertEquals(StreamKind.CONTAINER, fallback?.kind)
        assertEquals("eden", fallback?.containerLabel)
        assertNull(controller.offerFor(7, "switch", 29, "Zelda", false, false, settings.copy(host = "")))
        assertEquals(StreamKind.MOONLIGHT, controller.offerFor(7, "pc", null, "Hades", true, false, settings)?.kind)
    }
    @Test
    fun `inventory changes update offers and stream first and host replacement fails open`() = runTest {
        val config = mockk<StreamingConfigStore> {
            every { availability } returns MutableStateFlow(mockk(relaxed = true))
            every { containerFor(any()) } returns RomMStreamingContainer(platform = "switch", label = "eden")
            every { streamablePlatforms() } returns setOf("switch")
        }
        val sessions = mockk<StreamSessionManager> { every { session } returns MutableStateFlow(null) }
        val prefs = MutableStateFlow(UserPreferences(notOnDeviceAction = NotOnDeviceAction.STREAM,
            streamSettings = StreamSettings(host = "gamer")))
        val inventory = MutableStateFlow<Pair<String, InventorySnapshot>?>(null)
        val controller = StreamLaunchController.Factory(
            mockk(), mockk(), config, sessions, mockk(), mockk(), mockk(), mockk(),
            mockk { every { userPreferences } returns prefs },
            mockk { every { snapshots() } returns inventory }
        ).create(backgroundScope)
        runCurrent()
        fun offer() = controller.offerFor(7, "switch", 29, "Hades", false, false, prefs.value.streamSettings)
        assertEquals(StreamKind.MOONLIGHT, offer()?.kind)
        inventory.value = "gamer" to InventorySnapshot(setOf(InventoryEntry("Other", "1")), System.currentTimeMillis())
        runCurrent()
        assertEquals(StreamKind.CONTAINER, offer()?.kind)
        assertEquals(true, controller.streamFirst.value.leads("switch", true, false, 29, "Hades"))
        inventory.value = "gamer" to InventorySnapshot(setOf(InventoryEntry("Hades [29]", "1004913149")), System.currentTimeMillis())
        runCurrent()
        assertEquals(StreamKind.MOONLIGHT, offer()?.kind)
        prefs.value = prefs.value.copy(streamSettings = prefs.value.streamSettings.copy(host = "other"))
        inventory.value = "gamer" to InventorySnapshot(setOf(InventoryEntry("Other", "1")), System.currentTimeMillis())
        runCurrent()
        assertEquals(StreamKind.MOONLIGHT, offer()?.kind)
    }

    @Test
    fun `inventory removal stops stream first without a container`() = runTest {
        val config = mockk<StreamingConfigStore> {
            every { availability } returns MutableStateFlow(mockk(relaxed = true))
            every { containerFor(any()) } returns null
            every { streamablePlatforms() } returns emptySet()
        }
        val sessions = mockk<StreamSessionManager> { every { session } returns MutableStateFlow(null) }
        val prefs = MutableStateFlow(UserPreferences(notOnDeviceAction = NotOnDeviceAction.STREAM,
            streamSettings = StreamSettings(host = "gamer")))
        val inventory = MutableStateFlow<Pair<String, InventorySnapshot>?>(null)
        val controller = StreamLaunchController.Factory(
            mockk(), mockk(), config, sessions, mockk(), mockk(), mockk(), mockk(),
            mockk { every { userPreferences } returns prefs },
            mockk { every { snapshots() } returns inventory }
        ).create(backgroundScope)
        runCurrent()
        fun leads() = controller.streamFirst.value.leads("switch", true, false, 29, "Hades")
        assertEquals(true, leads())
        inventory.value = "gamer" to InventorySnapshot(setOf(InventoryEntry("Other", "1")), System.currentTimeMillis())
        runCurrent()
        assertEquals(false, leads())
        assertNull(controller.offerFor(7, "switch", 29, "Hades", false, false, prefs.value.streamSettings))
        inventory.value = "gamer" to InventorySnapshot(setOf(InventoryEntry("Hades [29]", "1004913149")), System.currentTimeMillis())
        runCurrent()
        assertEquals(true, leads())
    }

    @Test
    fun `offers react to preference changes and lead with moonlight`() = runTest {
        val config = mockk<StreamingConfigStore> {
            every { availability } returns MutableStateFlow(mockk(relaxed = true))
            every { containerFor(any()) } returns null
            every { streamablePlatforms() } returns emptySet()
        }
        val sessions = mockk<StreamSessionManager> { every { session } returns MutableStateFlow(null) }
        val prefs = MutableStateFlow(UserPreferences(notOnDeviceAction = NotOnDeviceAction.STREAM,
            streamSettings = StreamSettings(host = "gamer", platforms = setOf("switch"))))
        val preferences = mockk<com.nendo.argosy.data.preferences.UserPreferencesRepository> {
            every { userPreferences } returns prefs
        }
        val controller = StreamLaunchController.Factory(
            mockk(), mockk(), config, sessions, mockk(), mockk(), mockk(), mockk(), preferences,
            mockk { every { snapshots() } returns flowOf(null) }
        ).create(backgroundScope)
        runCurrent()
        fun offer() = controller.offerFor(7, "switch", 29, "Zelda", false, false, prefs.value.streamSettings)
        assertEquals(true, offer()?.leads)
        assertEquals(false, controller.offerFor(7, "switch", 29, "Zelda", false, false,
            prefs.value.streamSettings, isOnDevice = true)?.leads)
        assertEquals(true, controller.offerFor(8, "pc", null, "Hades", true, false,
            prefs.value.streamSettings)?.leads)
        prefs.value = prefs.value.copy(streamSettings = prefs.value.streamSettings.copy(platforms = emptySet()))
        runCurrent()
        assertNull(offer())
        prefs.value = prefs.value.copy(streamSettings = prefs.value.streamSettings.copy(host = ""))
        runCurrent()
        assertNull(controller.offerFor(8, "pc", null, "Hades", true, false, prefs.value.streamSettings))
    }

}
