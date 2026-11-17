package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.data.stream.StreamKind
import com.nendo.argosy.ui.screens.stream.StreamOffer
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
        title = "Game",
        platformId = 7,
        platformSlug = "3ds",
        platformDisplayName = "3DS",
        coverPath = null,
        backgroundPath = null,
        developer = null,
        releaseYear = null,
        genre = null,
        isFavorite = false,
        isDownloaded = false,
        isRommGame = true
    )

    private val moonlightGame = game.copy(
        title = "The Legend of Zelda: Tears of the Kingdom",
        platformId = 12,
        platformSlug = "switch",
        platformDisplayName = "Switch",
        isDownloaded = true,
        rommId = 29
    )

    private val container = StreamOffer("azahar", false)
    private val moonlight = StreamOffer("", false, kind = StreamKind.MOONLIGHT)

    @Test
    fun `the stream row sits right after download`() {
        assertEquals(GameMenuAction.Play(1, false, false), delegate.resolveMenuAction(0, game, false, stream = container))
        assertEquals(GameMenuAction.Stream(1, StreamKind.CONTAINER), delegate.resolveMenuAction(1, game, false, stream = container))
        assertEquals(GameMenuAction.ToggleFavorite(1), delegate.resolveMenuAction(2, game, false, stream = container))
    }

    @Test
    fun `a moonlight row takes the same position as a container row`() {
        assertEquals(GameMenuAction.Play(1, false, true), delegate.resolveMenuAction(0, moonlightGame, false, stream = moonlight))
        assertEquals(GameMenuAction.Stream(1, StreamKind.MOONLIGHT), delegate.resolveMenuAction(1, moonlightGame, false, stream = moonlight))
        assertEquals(GameMenuAction.ToggleFavorite(1), delegate.resolveMenuAction(2, moonlightGame, false, stream = moonlight))
    }

    @Test
    fun `stream mode puts stream on top and download second`() {
        assertEquals(
            GameMenuAction.Stream(1, StreamKind.CONTAINER),
            delegate.resolveMenuAction(0, game, false, stream = container.copy(leads = true))
        )
        assertEquals(
            GameMenuAction.Download(1),
            delegate.resolveMenuAction(1, game, false, stream = container.copy(leads = true))
        )
        assertEquals(
            GameMenuAction.ToggleFavorite(1),
            delegate.resolveMenuAction(2, game, false, stream = container.copy(leads = true))
        )
    }

    @Test
    fun `stream mode without an offer keeps the download row on top`() {
        assertEquals(
            GameMenuAction.Play(1, false, false),
            delegate.resolveMenuAction(0, game, false, stream = null)
        )
        assertEquals(
            GameMenuAction.ToggleFavorite(1),
            delegate.resolveMenuAction(1, game, false, stream = null)
        )
    }

    @Test
    fun `focus reaches the last row in stream mode`() {
        delegate.moveGameMenuFocus(20, game, isPlatformRow = false, stream = container)
        val lastIndex = delegate.state.value.gameMenuFocusIndex

        assertEquals(
            GameMenuAction.Hide(1),
            delegate.resolveMenuAction(lastIndex, game, false, stream = container.copy(leads = true))
        )
    }

    @Test
    fun `no stream row without an offer`() {
        val actions = (0 until 12).map { delegate.resolveMenuAction(it, game, isPlatformRow = false) }
        assertFalse(actions.any { it is GameMenuAction.Stream })
        assertEquals(GameMenuAction.ToggleFavorite(1), delegate.resolveMenuAction(1, game, false))
    }

    @Test
    fun `focus reaches the last row with the stream row added`() {
        delegate.moveGameMenuFocus(20, game, isPlatformRow = false, stream = container)
        val lastIndex = delegate.state.value.gameMenuFocusIndex

        assertEquals(GameMenuAction.Hide(1), delegate.resolveMenuAction(lastIndex, game, false, stream = container))
    }
    @Test
    fun `every row resolves in order with either transport or no offer`() {
        for ((testedGame, offer) in listOf(moonlightGame to moonlight, game to container, game to null, game to container.copy(leads = true))) {
            delegate.resetMenu()
            delegate.moveGameMenuFocus(-100, testedGame, false)
            val expected = buildList {
                if (offer?.leads == true) add(GameMenuAction.Stream(1, offer.kind))
                if (offer?.leads == true) add(GameMenuAction.Download(1))
                else add(GameMenuAction.Play(1, false, testedGame.isDownloaded))
                if (offer != null && !offer.leads) add(GameMenuAction.Stream(1, offer.kind))
                add(GameMenuAction.ToggleFavorite(1))
                add(GameMenuAction.ViewDetails(1))
                add(GameMenuAction.AddToCollection(1))
                add(GameMenuAction.Refresh(1, false))
                if (testedGame.isDownloaded) add(GameMenuAction.Delete(1))
                add(GameMenuAction.Hide(1))
            }
            expected.forEachIndexed { index, action ->
                assertEquals(index, delegate.state.value.gameMenuFocusIndex)
                assertEquals(action, delegate.resolveMenuAction(index, testedGame, false, stream = offer))
                delegate.moveGameMenuFocus(1, testedGame, false, stream = offer)
            }
            assertEquals(expected.lastIndex, delegate.state.value.gameMenuFocusIndex)
        }
    }

}
