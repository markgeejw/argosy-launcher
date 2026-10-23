package com.nendo.argosy.util

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.data.model.RootScript
import com.nendo.argosy.data.model.RootScriptResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

class RootScriptRunnerTest {
    @After
    fun cleanup() = unmockkAll()

    @Test
    fun `fork export denies systemize before opening its asset`() {
        val context = forkContext()

        val result = RootScriptRunner.write(context, RootScript.SYSTEMIZE)

        assertTrue(result is RootScriptResult.Error && !result.duringRun)
        verify(exactly = 0) { context.assets }
    }

    @Test
    fun `fork apply denies systemize before invoking the root shell`() {
        val context = forkContext()
        mockkObject(RootShell)
        every { RootShell.run(any(), any()) } returns null

        val result = RootScriptRunner.run(context, RootScript.SYSTEMIZE)

        assertTrue(result is RootScriptResult.Error && result.duringRun)
        verify(exactly = 0) { RootShell.run(any(), any()) }
        verify(exactly = 0) { context.assets }
    }

    private fun forkContext(): Context = mockk(relaxed = true) {
        every { packageName } returns "com.nendo.argosy.kaegr"
        every { getString(R.string.settings_root_script_unavailable) } returns "Unavailable"
    }
}
