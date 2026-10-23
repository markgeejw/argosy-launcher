package com.nendo.argosy.debugtools

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamProbeActivityTest {

    @Test
    fun `probe accepts only launchable user input`() {
        assertTrue(isValidStreamProbeInput("arceus", "com.limelight", "1494549554", "Tears of the Kingdom"))
        assertFalse(isValidStreamProbeInput(" ", "com.limelight", "1494549554", "Tears of the Kingdom"))
        assertFalse(isValidStreamProbeInput("arceus", " ", "1494549554", "Tears of the Kingdom"))
        assertFalse(isValidStreamProbeInput("arceus", "com.limelight", "0", "Tears of the Kingdom"))
        assertFalse(isValidStreamProbeInput("arceus", "com.limelight", "2147483648", "Tears of the Kingdom"))
        assertFalse(isValidStreamProbeInput("arceus", "com.limelight", "1494549554", " "))
    }

    @Test
    fun `debug probe is registered and handles only expected client failures`() {
        val manifest = debugFile("AndroidManifest.xml").readText()
        val activity = debugFile("kotlin/com/nendo/argosy/debugtools/StreamProbeActivity.kt").readText()
        val releaseManifest = mainFile("AndroidManifest.xml").readText()

        assertTrue(manifest.contains(".debugtools.StreamProbeActivity"))
        assertFalse(releaseManifest.contains("StreamProbeActivity"))
        assertTrue(manifest.contains("android.intent.action.MAIN"))
        assertTrue(manifest.contains("android.intent.category.LAUNCHER"))
        assertTrue(activity.contains("startActivity(intent)"))
        assertTrue(activity.contains("ActivityNotFoundException"))
        assertTrue(activity.contains("SecurityException"))
        assertFalse(activity.contains("resolveActivity"))
    }

    private fun debugFile(relativePath: String): File = sourceFile("debug", relativePath)

    private fun mainFile(relativePath: String): File = sourceFile("main", relativePath)

    private fun sourceFile(sourceSet: String, relativePath: String): File {
        val roots = listOf(File("src/$sourceSet"), File("app/src/$sourceSet"))
        return roots.firstOrNull { it.isDirectory }?.resolve(relativePath)
            ?: error("Could not locate $sourceSet source root")
    }
}
