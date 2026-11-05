package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.ui.screens.home.HomeGameUi
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeGameMenuStreamTest {

    private val delegate = HomeGameMenuDelegate(
        context = mockk(relaxed = true),
        gameActions = mockk(relaxed = true),
        gameRepository = mockk(relaxed = true),
        soundManager = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true)
    )

    private val game = HomeGameUi(
        id = 1,
        title = "The Legend of Zelda: Tears of the Kingdom",
        platformId = 12,
        platformSlug = "switch",
        platformDisplayName = "Switch",
        coverPath = null,
        backgroundPath = null,
        developer = null,
        releaseYear = null,
        genre = null,
        isFavorite = false,
        isDownloaded = true,
        isRommGame = true,
        rommId = 29
    )

    @Test
    fun `the stream row is hidden when the game cannot stream`() {
        val actions = (0 until 12).map { delegate.resolveMenuAction(it, game, isPlatformRow = false) }

        assertFalse(actions.any { it is GameMenuAction.Stream })
    }

    @Test
    fun `the stream row follows play`() {
        assertEquals(
            GameMenuAction.Stream(1),
            delegate.resolveMenuAction(1, game, isPlatformRow = false, hasStream = true)
        )
        assertEquals(
            GameMenuAction.ToggleFavorite(1),
            delegate.resolveMenuAction(2, game, isPlatformRow = false, hasStream = true)
        )
    }

    @Test
    fun `the stream row follows the play on screen rows`() {
        assertEquals(
            GameMenuAction.Stream(1),
            delegate.resolveMenuAction(3, game, isPlatformRow = false, playDisplays = listOf(0, 1), hasStream = true)
        )
    }

    @Test
    fun `focus reaches the last row once the stream row is added`() {
        delegate.moveGameMenuFocus(20, game, isPlatformRow = false, hasStream = true)

        assertEquals(
            GameMenuAction.Hide(1),
            delegate.resolveMenuAction(delegate.state.value.gameMenuFocusIndex, game, isPlatformRow = false, hasStream = true)
        )
    }
}
