package com.nendo.argosy.ui.screens.gamedetail

import android.content.ActivityNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamLaunchBoundaryTest {
    @Test
    fun `handoff starts the supplied external activity once`() {
        var starts = 0

        assertTrue(tryStartStream { starts++ })

        assertEquals(1, starts)
    }

    @Test
    fun `missing activity reports failed handoff`() {
        assertFalse(tryStartStream { throw ActivityNotFoundException() })
    }

    @Test
    fun `inaccessible activity reports failed handoff`() {
        assertFalse(tryStartStream { throw SecurityException() })
    }

    @Test(expected = IllegalStateException::class)
    fun `programming errors propagate`() {
        tryStartStream { throw IllegalStateException("bad caller state") }
    }
}
