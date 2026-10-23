package com.nendo.argosy.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootScriptTest {
    @Test
    fun `systemize is unavailable to kaegr release and debug packages`() {
        assertFalse(RootScript.SYSTEMIZE.isAvailableForPackage("com.nendo.argosy.kaegr"))
        assertFalse(RootScript.SYSTEMIZE.isAvailableForPackage("com.nendo.argosy.kaegr.debug"))
    }

    @Test
    fun `systemize remains available to upstream packages`() {
        assertTrue(RootScript.SYSTEMIZE.isAvailableForPackage("com.nendo.argosy"))
        assertTrue(RootScript.SYSTEMIZE.isAvailableForPackage("com.nendo.argosy.debug"))
    }
}
