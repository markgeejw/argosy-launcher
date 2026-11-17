package com.nendo.argosy.data.stream

import com.nendo.argosy.data.model.stream.StreamSettings
import com.nendo.argosy.data.remote.romm.RomMStreamingContainer
import org.junit.Assert.assertEquals
import org.junit.Test

private fun FakeContainer(platform: String) = RomMStreamingContainer(platform = platform)

class UnifiedStreamRoutingTest {
    private val settings = StreamSettings(host = "arceus", platforms = setOf("switch", "pc"))

    @Test fun `switch romm game routes moonlight with the suffixed id`() {
        val route = StreamRoute.resolve(settings, container = null, rommId = 29L,
            platformSlug = "switch", isSteamGame = false, isAndroidApp = false, title = "Hades")
        assertEquals(StreamKind.MOONLIGHT, route?.kind)
        assertEquals("1004913149", route?.appId)
        assertEquals("1004913149", MoonlightStream.appId("Hades [29]").toString())
    }

    @Test fun `steam game routes moonlight on the bare title without consulting the set`() {
        val noPc = settings.copy(platforms = setOf("switch"))
        val route = StreamRoute.resolve(noPc, container = null, rommId = null,
            platformSlug = "windows", isSteamGame = true, isAndroidApp = false, title = "Hades")
        assertEquals(StreamKind.MOONLIGHT, route?.kind)
        assertEquals("1285314453", route?.appId)
    }

    @Test fun `pc, windows and alias slugs all match the one pc toggle`() {
        for (slug in listOf("pc", "windows", "windows_pc")) {
            val route = StreamRoute.resolve(settings, container = null, rommId = 7L,
                platformSlug = slug, isSteamGame = false, isAndroidApp = false, title = "Doom")
            assertEquals(StreamKind.MOONLIGHT, route?.kind)
        }
    }

    @Test fun `moonlight wins when a container also exists`() {
        val route = StreamRoute.resolve(settings, container = FakeContainer("eden"),
            rommId = 29L, platformSlug = "switch", isSteamGame = false, isAndroidApp = false, title = "Hades")
        assertEquals(StreamKind.MOONLIGHT, route?.kind)
    }

    @Test fun `light platform falls through to the container`() {
        val route = StreamRoute.resolve(settings, container = FakeContainer("azahar"),
            rommId = 5L, platformSlug = "3ds", isSteamGame = false, isAndroidApp = false, title = "Bravely Default")
        assertEquals(StreamKind.CONTAINER, route?.kind)
    }

    @Test fun `no moonlight and no container offers nothing`() {
        val route = StreamRoute.resolve(settings, container = null,
            rommId = 5L, platformSlug = "3ds", isSteamGame = false, isAndroidApp = false, title = "Bravely Default")
        assertEquals(null, route)
    }

    @Test fun `blank title kills the steam row`() {
        val route = StreamRoute.resolve(settings, container = null, rommId = null,
            platformSlug = "windows", isSteamGame = true, isAndroidApp = false, title = "  ")
        assertEquals(null, route)
    }

    @Test fun `blank host kills both transports`() {
        val route = StreamRoute.resolve(settings.copy(host = " "), container = FakeContainer("azahar"),
            rommId = 5L, platformSlug = "3ds", isSteamGame = false, isAndroidApp = false, title = "Bravely Default")
        assertEquals(null, route)
    }

    @Test fun `android app with a container offers nothing`() {
        val route = StreamRoute.resolve(settings, container = FakeContainer("eden"),
            rommId = null, platformSlug = "switch", isSteamGame = false, isAndroidApp = true, title = "Hades")
        assertEquals(null, route)
    }
}
