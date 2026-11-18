package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.ui.screens.settings.sections.RomMItem
import com.nendo.argosy.ui.screens.settings.sections.RomMLayoutState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamFirstAvailabilityTest {
    @Test
    fun `moonlight host exposes stream first without container availability`() {
        val base = SettingsUiState()
        val moonlight = base.copy(streaming = base.streaming.copy(
            settings = base.streaming.settings.copy(host = "gamer")
        ))
        assertFalse(base.copy(streaming = base.streaming.copy(
            settings = base.streaming.settings.copy(host = " ")
        )).streamFirstAvailable)
        assertTrue(moonlight.streamFirstAvailable)
        assertTrue(RomMItem.NotOnDevice.visibleWhen(RomMLayoutState.from(moonlight)))
        assertTrue(base.copy(server = base.server.copy(streamingAvailable = true)).streamFirstAvailable)
    }
}
