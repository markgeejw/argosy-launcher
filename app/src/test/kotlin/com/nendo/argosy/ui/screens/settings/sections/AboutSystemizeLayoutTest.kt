package com.nendo.argosy.ui.screens.settings.sections

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutSystemizeLayoutTest {
    @Test
    fun `kaegr replaces the systemize action with an unfocusable explanation`() {
        val items = AboutItem.ALL.filter { it.visibleWhen(AboutLayoutState(false, false, systemizeAvailable = false)) }

        assertTrue(AboutItem.SystemizeUnavailable in items)
        assertFalse(AboutItem.SystemizeUnavailable.isFocusable)
        assertFalse(AboutItem.SystemizeHelper in items)
    }

    @Test
    fun `upstream retains the systemize action`() {
        val items = AboutItem.ALL.filter { it.visibleWhen(AboutLayoutState(false, false, systemizeAvailable = true)) }

        assertTrue(AboutItem.SystemizeHelper in items)
        assertFalse(AboutItem.SystemizeUnavailable in items)
    }
}
