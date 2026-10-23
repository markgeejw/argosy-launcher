package com.nendo.argosy.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CyclePreferenceCallbacksTest {
    @Test
    fun `open previous and next keep distinct callbacks`() {
        var selectedClient = "com.limelight"
        var opens = 0
        val callbacks = cyclePreferenceCallbacks(
            onOpen = { opens++ },
            onPrev = { selectedClient = "com.limelight" },
            onNext = { selectedClient = "com.limelight.noir" }
        )

        callbacks.onOpen()
        assertEquals("com.limelight", selectedClient)
        assertEquals(1, opens)

        callbacks.onNext()
        assertEquals("com.limelight.noir", selectedClient)
        assertEquals(1, opens)

        callbacks.onPrev()
        assertEquals("com.limelight", selectedClient)
        assertEquals(1, opens)
    }

    @Test
    fun `next falls back to the existing row callback`() {
        var opens = 0
        val callbacks = cyclePreferenceCallbacks(onOpen = { opens++ })

        callbacks.onNext()

        assertEquals(1, opens)
    }
}
