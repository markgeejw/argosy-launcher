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

class GameDetailStreamTest {

    private fun detail(
        platformSlug: String = "switch",
        source: GameSource = GameSource.ROMM_REMOTE
    ) = GameEntity(
        id = 7L,
        platformId = 1L,
        platformSlug = platformSlug,
        title = "The Legend of Zelda: Tears of the Kingdom",
        sortTitle = "legend of zelda tears of the kingdom",
        localPath = null,
        rommId = 29L,
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
    fun `an undownloaded switch game shows the stream row right below play`() {
        val layout = GameDetailUiState(game = detail()).menuLayoutState

        assertTrue(layout.hasStream)
        assertEquals(
            MenuItem.Stream,
            menuLayout.itemAtFocusIndex(menuLayout.focusIndexOf(MenuItem.Play, layout) + 1, layout)
        )
    }

    @Test
    fun `a platform the user has not enabled shows no stream row`() {
        val state = GameDetailUiState(
            game = detail(),
            streamSettings = StreamSettings(platforms = setOf("3ds"))
        )

        assertFalse(state.menuLayoutState.hasStream)
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
    fun `steam and android games show no stream row`() {
        assertFalse(GameDetailUiState(game = detail(source = GameSource.STEAM)).menuLayoutState.hasStream)
        assertFalse(GameDetailUiState(game = detail(source = GameSource.ANDROID_APP)).menuLayoutState.hasStream)
    }

    @Test
    fun `without a game there is no stream row`() {
        assertFalse(GameDetailUiState().menuLayoutState.hasStream)
    }
}
