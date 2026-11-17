package com.nendo.argosy.ui.screens.gamedetail

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.stream.StreamSettings
import com.nendo.argosy.ui.screens.gamedetail.components.MenuItem
import com.nendo.argosy.ui.screens.gamedetail.components.menuLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import android.content.Intent
import io.mockk.mockkStatic
import io.mockk.mockk
import io.mockk.every
import com.nendo.argosy.data.stream.toIntent
import io.mockk.unmockkStatic
import io.mockk.verify

class GameDetailStreamTest {

    private fun detail(
        platformSlug: String = "switch",
        source: GameSource = GameSource.ROMM_REMOTE,
        title: String = "The Legend of Zelda: Tears of the Kingdom",
        rommId: Long? = 29L
    ) = GameEntity(
        id = 7L,
        platformId = 1L,
        platformSlug = platformSlug,
        title = title,
        sortTitle = "legend of zelda tears of the kingdom",
        localPath = null,
        rommId = rommId,
        igdbId = null,
        source = source
    ).toGameDetailUi(
        art = null,
        screenshotRows = emptyList(),
        platformName = "Switch",
        emulatorName = null,
        canPlay = false
    )

    @Test
    fun `the detail carries the romm id`() {
        assertEquals(29L, detail().rommId)
    }

    @Test
    fun `moonlight offer appears immediately below play`() {
        val offer = com.nendo.argosy.ui.screens.stream.StreamOffer(
            "", false, kind = com.nendo.argosy.data.stream.StreamKind.MOONLIGHT
        )
        val layout = GameDetailUiState(game = detail(), stream = offer).menuLayoutState
        assertEquals(offer, layout.stream)
        assertEquals(MenuItem.Stream, menuLayout.itemAtFocusIndex(menuLayout.focusIndexOf(MenuItem.Play, layout) + 1, layout))
    }

    @Test
    fun `stream action emits only an external stream event and rechecks stale eligibility`() {
        val enabled = StreamSettings()
        val event = streamLaunchEvent(detail(), enabled)

        assertTrue(event is LaunchEvent.StreamIntent)
        assertEquals(enabled.moonlightPackage, event?.moonlightPackage)
        assertNull(streamLaunchEvent(detail(), enabled.copy(platforms = emptySet())))
    }

    @Test
    fun `a steam tap resolves the bare-name intent`() {
        val event = streamLaunchEvent(
            detail(source = GameSource.STEAM, title = "Hades", rommId = null),
            StreamSettings()
        )

        assertTrue(event is LaunchEvent.StreamIntent)
        assertEquals(StreamSettings.DEFAULT_MOONLIGHT_PACKAGE, event?.moonlightPackage)
    }

    @Test
    fun `moonlight events carry requests for the selected games`() {
        mockkStatic("com.nendo.argosy.data.stream.MoonlightStreamKt")
        try {
            val intent = mockk<Intent>()
            every { any<com.nendo.argosy.data.model.stream.StreamRequest>().toIntent(any()) } returns intent
            assertEquals(intent, streamLaunchEvent(detail(), StreamSettings())?.intent)
            verify { match<com.nendo.argosy.data.model.stream.StreamRequest> { it.appId == "1494549554" }.toIntent("com.limelight") }
            assertEquals(intent, streamLaunchEvent(detail(source = GameSource.STEAM, title = "Hades", rommId = null), StreamSettings())?.intent)
            verify { match<com.nendo.argosy.data.model.stream.StreamRequest> { it.appId == "1285314453" }.toIntent("com.limelight") }
        } finally {
            unmockkStatic("com.nendo.argosy.data.stream.MoonlightStreamKt")
        }
    }

    @Test
    fun `a blank steam title resolves nothing`() {
        assertNull(
            streamLaunchEvent(
                detail(source = GameSource.STEAM, title = "  ", rommId = null),
                StreamSettings()
            )
        )
    }

    @Test
    fun `without a game there is no stream row`() {
        assertNull(GameDetailUiState().menuLayoutState.stream)
    }
}
