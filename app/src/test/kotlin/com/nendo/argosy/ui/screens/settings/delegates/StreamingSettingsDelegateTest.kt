package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import com.nendo.argosy.ui.components.TEXT_ENTRY_CANCEL_BUTTON
import com.nendo.argosy.ui.components.TextEntryRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamingSettingsDelegateTest {
    private fun TestScope.delegate(): StreamingSettingsDelegate {
        val prefs = MutableStateFlow(UserPreferences())
        val repository = mockk<UserPreferencesRepository>(relaxed = true)
        every { repository.userPreferences } returns prefs
        coEvery { repository.setStreamHost(any()) } answers {
            val host = firstArg<String>().trim()
            prefs.update { it.copy(streamSettings = it.streamSettings.copy(host = host)) }
        }
        coEvery { repository.setStreamMoonlightPackage(any()) } answers {
            val client = firstArg<String>()
            prefs.update { it.copy(streamSettings = it.streamSettings.copy(moonlightPackage = client)) }
        }
        coEvery { repository.setStreamPlatformEnabled(any(), any()) } answers {
            val slug = firstArg<String>()
            val enabled = secondArg<Boolean>()
            prefs.update {
                val platforms = it.streamSettings.platforms
                it.copy(streamSettings = it.streamSettings.copy(platforms = if (enabled) platforms + slug else platforms - slug))
            }
        }
        return StreamingSettingsDelegate(repository).also { it.observe(backgroundScope) }
    }

    private suspend fun StreamingSettingsDelegate.settled(done: (StreamingState) -> Boolean) = state.first(done)

    @Test
    fun `a saved host replaces the default`() = runTest {
        val delegate = delegate()
        delegate.openHostModal()
        delegate.updateHostDraft(" gamer ")
        delegate.submitHost(this)

        assertEquals("gamer", delegate.settled { it.settings.host != "arceus" }.settings.host)
        assertFalse(delegate.state.value.showHostModal)
    }

    @Test
    fun `a blank host is not saved`() = runTest {
        val delegate = delegate()
        delegate.openHostModal()
        delegate.updateHostDraft("  ")
        delegate.submitHost(this)
        advanceUntilIdle()

        assertEquals("arceus", delegate.state.value.settings.host)
        assertTrue(delegate.state.value.showHostModal)
    }

    @Test
    fun `confirm on the field moves to the buttons and cancel closes without saving`() = runTest {
        val delegate = delegate()
        delegate.openHostModal()
        delegate.updateHostDraft("other")
        delegate.confirmHost(this)
        assertEquals(TextEntryRow.BUTTONS, delegate.state.value.hostFocus.row)

        delegate.moveHostButton(-1)
        assertEquals(TEXT_ENTRY_CANCEL_BUTTON, delegate.state.value.hostFocus.buttonIndex)
        delegate.confirmHost(this)
        advanceUntilIdle()

        assertFalse(delegate.state.value.showHostModal)
        assertEquals("arceus", delegate.state.value.settings.host)
    }

    @Test
    fun `the client cycles through moonlight and artemis in both directions`() = runTest {
        val delegate = delegate()
        advanceUntilIdle()
        delegate.cycleClient(this, 1)
        delegate.settled { it.settings.moonlightPackage == "com.limelight.noir" }
        delegate.cycleClient(this, 1)
        delegate.settled { it.settings.moonlightPackage == "com.limelight" }
        delegate.cycleClient(this, -1)
        assertEquals("com.limelight.noir", delegate.settled { it.settings.moonlightPackage != "com.limelight" }.settings.moonlightPackage)
    }

    @Test
    fun `selecting a client writes only the selected package`() = runTest {
        val delegate = delegate()
        advanceUntilIdle()

        delegate.setClient(this, "com.limelight.noir")

        assertEquals("com.limelight.noir", delegate.state.value.settings.moonlightPackage)
    }

    @Test
    fun `rapid client edits use the pending selection`() = runTest {
        val delegate = delegate()
        advanceUntilIdle()

        delegate.cycleClient(this, 1)
        delegate.cycleClient(this, 1)
        advanceUntilIdle()

        assertEquals("com.limelight", delegate.state.value.settings.moonlightPackage)
    }

    @Test
    fun `toggling a platform flips whether it streams`() = runTest {
        val delegate = delegate()
        advanceUntilIdle()
        delegate.togglePlatform(this, "3ds")
        delegate.settled { "3ds" in it.settings.platforms }
        delegate.togglePlatform(this, "switch")
        assertEquals(setOf("3ds"), delegate.settled { "switch" !in it.settings.platforms }.settings.platforms)
    }
}
