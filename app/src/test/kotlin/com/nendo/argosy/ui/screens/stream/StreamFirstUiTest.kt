package com.nendo.argosy.ui.screens.stream

import com.nendo.argosy.data.model.stream.StreamSettings
import com.nendo.argosy.data.preferences.NotOnDeviceAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamFirstUiTest {

    private val streamMode = StreamFirstUi(NotOnDeviceAction.STREAM, setOf("psx"))

    @Test
    fun `a romm game on a served platform leads with stream whatever the slug case`() {
        assertTrue(streamMode.leads("PSX", isRommGame = true, isOnDevice = false))
    }

    @Test
    fun `a platform without a container keeps download`() {
        assertFalse(streamMode.leads("snes", isRommGame = true, isOnDevice = false))
        assertFalse(streamMode.leads(null, isRommGame = true, isOnDevice = false))
    }

    @Test
    fun `a game on the device or outside romm never leads with stream`() {
        assertFalse(streamMode.leads("psx", isRommGame = true, isOnDevice = true))
        assertFalse(streamMode.leads("psx", isRommGame = false, isOnDevice = false))
    }

    @Test
    fun `moonlight eligibility follows host platform and steam title`() {
        val first = StreamFirstUi(NotOnDeviceAction.STREAM, emptySet(), StreamSettings(host = "gamer", platforms = setOf("switch", "pc")))
        assertTrue(first.leads("switch", true, false, rommId = 29, title = "Zelda"))
        assertTrue(first.leads("windows_pc", true, false, rommId = 30, title = "Doom"))
        assertTrue(first.leads("other", false, false, title = "Hades", isSteamGame = true))
        assertFalse(first.leads("switch", true, true, rommId = 29, title = "Zelda"))
        assertFalse(first.leads("switch", true, false, title = "Zelda"))
        assertFalse(first.leads("other", false, false, title = " ", isSteamGame = true))
        assertFalse(first.copy(settings = first.settings.copy(host = "")).leads("switch", true, false, rommId = 29))
        assertFalse(first.copy(settings = first.settings.copy(platforms = emptySet())).leads("switch", true, false, rommId = 29))
    }

    @Test
    fun `download mode never leads with stream`() {
        val downloadMode = StreamFirstUi(NotOnDeviceAction.DOWNLOAD, setOf("psx"))

        assertFalse(downloadMode.leads("psx", isRommGame = true, isOnDevice = false))
    }
}
