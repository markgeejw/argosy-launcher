package com.nendo.argosy.ui.screens.settings.sections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamingSectionLayoutTest {
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
