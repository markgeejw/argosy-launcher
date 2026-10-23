package com.nendo.argosy.data.stream

import com.nendo.argosy.data.model.stream.StreamRequest
import com.nendo.argosy.data.model.stream.StreamSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonlightStreamTest {

    @Test
    fun `app ids match the ids gamer's sunshine computes`() {
        assertEquals(1494549554, MoonlightStream.appId("The Legend of Zelda: Tears of the Kingdom [29]"))
        assertEquals(1097037181, MoonlightStream.appId("Pokémon Y [12]"))
    }

    @Test
    fun `a request names the gamer host, the app id of the rom and the rom title`() {
        val request = MoonlightStream.request("arceus", "The Legend of Zelda: Tears of the Kingdom", 29)

        assertEquals(
            StreamRequest("arceus", "1494549554", "The Legend of Zelda: Tears of the Kingdom", hdr = true),
            request
        )
    }

    @Test
    fun `a romm game on an enabled platform can stream whether or not it is downloaded`() {
        assertTrue(MoonlightStream.canStream(StreamSettings(), 29, "switch", isSteamGame = false, isAndroidApp = false))
    }

    @Test
    fun `platform aliases count as their canonical platform`() {
        assertTrue(MoonlightStream.canStream(StreamSettings(), 29, "nintendo-switch", isSteamGame = false, isAndroidApp = false))
    }

    @Test
    fun `games without a romm id, on disabled platforms or without a host cannot stream`() {
        assertFalse(MoonlightStream.canStream(StreamSettings(), null, "switch", isSteamGame = false, isAndroidApp = false))
        assertFalse(MoonlightStream.canStream(StreamSettings(), 12, "3ds", isSteamGame = false, isAndroidApp = false))
        assertFalse(MoonlightStream.canStream(StreamSettings(host = " "), 29, "switch", isSteamGame = false, isAndroidApp = false))
    }

    @Test
    fun `steam games and android apps cannot stream`() {
        assertFalse(MoonlightStream.canStream(StreamSettings(), 29, "switch", isSteamGame = true, isAndroidApp = false))
        assertFalse(MoonlightStream.canStream(StreamSettings(), 29, "switch", isSteamGame = false, isAndroidApp = true))
    }
}
