package com.nendo.argosy.data.stream

import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Test

class MoonlightIntentTest {

    @After
    fun cleanup() = unmockkAll()

    @Test
    fun `explicit client keeps the base trampoline class and typed extras`() {
        mockkConstructor(Intent::class)
        val ignored = mockk<Intent>(relaxed = true)
        every { anyConstructed<Intent>().setClassName(any<String>(), any<String>()) } returns ignored
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } returns ignored
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Boolean>()) } returns ignored
        every { anyConstructed<Intent>().addFlags(any()) } returns ignored

        MoonlightStream.request("arceus", "The Legend of Zelda: Tears of the Kingdom", 29)
            .toIntent("com.limelight.noir")

        verify(exactly = 1) {
            anyConstructed<Intent>().setClassName("com.limelight.noir", "com.limelight.ShortcutTrampoline")
        }
        verify(exactly = 1) { anyConstructed<Intent>().putExtra("Name", "arceus") }
        verify(exactly = 1) { anyConstructed<Intent>().putExtra("AppId", "1494549554") }
        verify(exactly = 1) {
            anyConstructed<Intent>().putExtra("AppName", "The Legend of Zelda: Tears of the Kingdom")
        }
        verify(exactly = 1) { anyConstructed<Intent>().putExtra("HDR", true) }
        verify(exactly = 1) { anyConstructed<Intent>().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }
}
