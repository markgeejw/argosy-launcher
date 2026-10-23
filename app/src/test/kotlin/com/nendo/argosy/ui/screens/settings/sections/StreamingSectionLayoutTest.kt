package com.nendo.argosy.ui.screens.settings.sections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamingSectionLayoutTest {
    @Test
    fun `client picker indexes map to persisted packages`() {
        assertEquals("com.limelight", streamingClientPackageAt(0))
        assertEquals("com.limelight.noir", streamingClientPackageAt(1))
        assertNull(streamingClientPackageAt(2))
    }

    @Test
    fun `host and client precede the six platform controls`() {
        assertEquals(StreamingItem.Host, streamingItemAtFocusIndex(0))
        assertEquals(StreamingItem.Client, streamingItemAtFocusIndex(1))
        listOf("switch", "3ds", "nds", "snes", "psx", "ps2").forEachIndexed { index, slug ->
            assertEquals(StreamingItem.Platform(slug), streamingItemAtFocusIndex(index + 2))
        }
        assertEquals(7, streamingMaxFocusIndex())
        assertNull(streamingItemAtFocusIndex(8))
    }
}
