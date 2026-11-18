package com.nendo.argosy.ui.screens.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.model.stream.StreamSettings
import com.nendo.argosy.data.preferences.NotOnDeviceAction
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.remote.romm.RomMStreamingContainer
import com.nendo.argosy.data.stream.StreamKind
import com.nendo.argosy.data.stream.StreamRoute
import com.nendo.argosy.ui.screens.home.delegates.HomeGameMenuDelegate
import com.nendo.argosy.ui.screens.home.delegates.HomeLibraryDelegate
import com.nendo.argosy.ui.screens.home.delegates.HomeMediaState
import com.nendo.argosy.ui.screens.home.delegates.HomeNavigationDelegate
import com.nendo.argosy.ui.screens.home.delegates.LibraryState
import com.nendo.argosy.ui.screens.home.delegates.SyncState
import com.nendo.argosy.ui.screens.home.delegates.VideoPreviewState
import com.nendo.argosy.ui.screens.stream.StreamLaunchController
import com.nendo.argosy.ui.screens.stream.StreamOffer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeStreamOfferTest {
    private val dispatcher = StandardTestDispatcher()
    private val prefs = MutableStateFlow(UserPreferences(streamSettings = StreamSettings(host = "gamer", platforms = emptySet())))
    private val game = HomeGameUi(7, "Zelda", 1, "switch", "Switch", null, backgroundPath = null,
        developer = null, releaseYear = null, genre = null, isFavorite = false, isDownloaded = false,
        isRommGame = true, rommId = 29)
    private val controller = mockk<StreamLaunchController>(relaxed = true)
    private val menu = HomeGameMenuDelegate(mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
    private var vm: HomeViewModel? = null

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { vm?.viewModelScope?.cancel(); Dispatchers.resetMain() }

    private fun create(): HomeViewModel {
        val preferences = mockk<com.nendo.argosy.data.preferences.UserPreferencesRepository>(relaxed = true) {
            every { userPreferences } returns prefs
            every { this@mockk.preferences } returns emptyFlow()
            every { latest } returns null
        }
        io.mockk.coEvery { controller.inventoryFor(any()) } returns (true to null)
        every { controller.offers } returns prefs.map { Unit }
        every { controller.streamFirst } returns mockk {
            every { value } answers {
                com.nendo.argosy.ui.screens.stream.StreamFirstUi(
                    prefs.value.notOnDeviceAction, setOf("switch"), prefs.value.streamSettings
                )
            }
        }
        every { controller.offerFor(any(), any<String>(), any(), any(), any(), any(), any(), any(), any()) } answers {
            val settings = arg<StreamSettings>(6)
            val route = StreamRoute.resolve(settings, RomMStreamingContainer(platform = "switch", label = "eden"),
                arg(2), arg(1), arg(4), arg(5), arg(3),
                arg<com.nendo.argosy.data.model.stream.InventorySnapshot?>(8)?.entries)
            route?.let { StreamOffer("eden", false,
                leads = prefs.value.notOnDeviceAction == NotOnDeviceAction.STREAM && !arg<Boolean>(7), kind = it.kind) }
        }
        every { controller.offerFor(any(), any<String>(), any(), any(), any(), any(), any(), any()) } answers {
            controller.offerFor(arg(0), arg<String>(1), arg(2), arg(3), arg(4), arg(5), arg(6), arg(7), null)
        }
        val library = mockk<HomeLibraryDelegate>(relaxed = true) {
            every { state } returns MutableStateFlow(LibraryState(recentGames = listOf(game)))
        }
        val nav = mockk<HomeNavigationDelegate>(relaxed = true) {
            every { restoreInitialRow(any()) } returns (HomeRow.Continue to 0)
        }
        return HomeViewModel(
            context = mockk(relaxed = true), savedStateHandle = SavedStateHandle(), gameRepository = mockk(relaxed = true),
            displayAffinityHelper = mockk(relaxed = true), configureEmulatorUseCase = mockk(relaxed = true),
            appShortcutActions = mockk(relaxed = true), preferencesRepository = preferences,
            notificationManager = mockk(relaxed = true), gameNavigationContext = mockk(relaxed = true),
            downloadManager = mockk(relaxed = true), soundManager = mockk(relaxed = true),
            gameLaunchDelegate = mockk(relaxed = true) {
                every { syncOverlayState } returns MutableStateFlow(null)
                every { discPickerState } returns MutableStateFlow(null)
                every { memcardPickerState } returns MutableStateFlow(null)
            },
            collectionModalDelegate = mockk(relaxed = true) { every { state } returns MutableStateFlow(com.nendo.argosy.ui.screens.common.CollectionModalDelegate.State()) },
            fetchAchievementsUseCase = mockk(relaxed = true), achievementUpdateBus = com.nendo.argosy.core.event.AchievementUpdateBus(),
            modalResetSignal = com.nendo.argosy.ui.ModalResetSignal(),
            gradientExtractionDelegate = mockk(relaxed = true) {
                every { gradients } returns MutableStateFlow(emptyMap())
                every { mediaGradients } returns MutableStateFlow(emptyMap())
            }, ambientLedManager = mockk(relaxed = true), libraryDelegate = library, navigationDelegate = nav,
            downloadDelegate = mockk(relaxed = true) { every { downloadIndicators } returns MutableStateFlow(emptyMap()) },
            syncDelegate = mockk(relaxed = true) { every { state } returns MutableStateFlow(SyncState()) },
            videoPreviewDelegate = mockk(relaxed = true) { every { state } returns MutableStateFlow(VideoPreviewState()) },
            gameMenuDelegate = menu,
            mediaDelegate = mockk(relaxed = true) { every { state } returns MutableStateFlow(HomeMediaState()) },
            tilePickerDelegate = mockk(relaxed = true), dockDelegate = mockk(relaxed = true) { every { reorder } returns MutableStateFlow(null) },
            steamLauncherLinkController = mockk(relaxed = true), appsRepository = mockk(relaxed = true),
            homeTileRepository = mockk(relaxed = true), customGridShapeStore = mockk(relaxed = true),
            homeGridPageRepository = mockk(relaxed = true), raTileContentRepository = mockk(relaxed = true),
            pageChooserEntrySource = mockk(relaxed = true), collectionRepository = mockk(relaxed = true),
            advanceCollectionFocusUseCase = mockk(relaxed = true), prepareCollectionQueueUseCase = mockk(relaxed = true),
            homeTilePromptQueue = mockk(relaxed = true) { every { pending } returns MutableStateFlow(emptyList()) },
            syncPreferencesRepository = mockk(relaxed = true),
            socialRepository = mockk(relaxed = true) { every { friendsActivity } returns MutableStateFlow(emptyMap()) },
            romMRepository = mockk(relaxed = true), siblingChoice = mockk(relaxed = true) { every { pickChanges } returns MutableSharedFlow() },
            showcaseSource = mockk(relaxed = true), quickNavigationSource = mockk(relaxed = true),
            firstFrameCache = HomeFirstFrameCache(), monthlyRecap = mockk(relaxed = true), gameActions = mockk(relaxed = true),
            runningGames = mockk { every { state } returns MutableStateFlow(com.nendo.argosy.data.emulator.background.RunningGamesSnapshot()) },
            runningGameMenu = mockk(relaxed = true), streamLaunch = mockk { every { create(any()) } returns controller }
        ).also { vm = it }
    }

    @Test fun `dispatch rejects moonlight when a newer inventory suppresses it`() = runTest(dispatcher) {
        prefs.value = prefs.value.copy(streamSettings = prefs.value.streamSettings.copy(host = "gamer", platforms = setOf("switch")))
        val model = create()
        runCurrent()
        model.toggleGameMenu()
        runCurrent()
        assertEquals(StreamKind.MOONLIGHT, model.uiState.value.gameMenuStream?.kind)
        io.mockk.coEvery { controller.inventoryFor(any()) } returns (true to
            com.nendo.argosy.data.model.stream.InventorySnapshot(
                setOf(com.nendo.argosy.data.model.stream.InventoryEntry("Other", "1")), System.currentTimeMillis()))
        model.openGameStream(game, StreamKind.MOONLIGHT)
        runCurrent()
        verify(exactly = 0) { controller.open(any()) }
    }

    @Test fun `open menu tracks transport action and availability and rejects stale dispatch`() = runTest(dispatcher) {
        val model = create()
        runCurrent()
        assertEquals(game.id, model.uiState.value.focusedGame?.id)
        model.toggleGameMenu()
        runCurrent()
        assertTrue(model.uiState.value.showGameMenu)
        assertEquals(StreamKind.CONTAINER, model.uiState.value.gameMenuStream?.kind)
        model.moveGameMenuFocus(100)
        runCurrent()
        val maxWithStream = model.uiState.value.gameMenuFocusIndex
        prefs.value = prefs.value.copy(streamSettings = prefs.value.streamSettings.copy(host = ""))
        model.openGameStream(game, StreamKind.CONTAINER)
        runCurrent()
        assertNull(model.uiState.value.gameMenuStream)
        assertEquals(maxWithStream - 1, model.uiState.value.gameMenuFocusIndex)
        verify(exactly = 0) { controller.open(any()) }
        prefs.value = prefs.value.copy(streamSettings = prefs.value.streamSettings.copy(host = "gamer", platforms = setOf("switch")),
            notOnDeviceAction = NotOnDeviceAction.STREAM)
        model.openGameStream(game, StreamKind.CONTAINER)
        runCurrent()
        assertEquals(StreamKind.MOONLIGHT, model.uiState.value.gameMenuStream?.kind)
        assertEquals(true, model.uiState.value.gameMenuStream?.leads)
        verify(exactly = 0) { controller.open(any()) }
        prefs.value = prefs.value.copy(streamSettings = prefs.value.streamSettings.copy(platforms = emptySet()),
            notOnDeviceAction = NotOnDeviceAction.DOWNLOAD)
        runCurrent()
        assertEquals(StreamKind.CONTAINER, model.uiState.value.gameMenuStream?.kind)
        assertEquals(false, model.uiState.value.gameMenuStream?.leads)
        model.openGameStream(game, StreamKind.CONTAINER)
        runCurrent()
        verify(exactly = 1) { controller.open(game.id) }
    }
}
