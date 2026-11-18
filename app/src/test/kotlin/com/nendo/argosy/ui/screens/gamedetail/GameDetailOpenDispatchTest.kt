package com.nendo.argosy.ui.screens.gamedetail

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.stream.StreamSettings
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.ui.screens.stream.StreamLaunchController
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GameDetailOpenDispatchTest {

    private val dispatcher = StandardTestDispatcher()
    private val emulatorDetector = mockk<EmulatorDetector>(relaxed = true)
    private val preferences =
        mockk<com.nendo.argosy.data.preferences.UserPreferencesRepository>(relaxed = true)
    private val gameRepository = mockk<com.nendo.argosy.data.repository.GameRepository>(relaxed = true)
    private val tracker = mockk<com.nendo.argosy.data.emulator.PlaySessionTracker>(relaxed = true) {
        every { activeSession } returns MutableStateFlow(null)
    }
    private val launchDelegate = mockk<com.nendo.argosy.ui.screens.common.GameLaunchDelegate>(relaxed = true) {
        every { syncOverlayState } returns MutableStateFlow(null)
        every { sessionEndRunning } returns MutableStateFlow(false)
        every { memcardPickerState } returns MutableStateFlow(null)
        every { variantPickerState } returns MutableStateFlow(null)
        every { isSessionEndBusy } returns false
    }
    private val downloadState = MutableStateFlow(com.nendo.argosy.ui.screens.gamedetail.delegates.DownloadUiState())
    private val downloadDelegate = mockk<com.nendo.argosy.ui.screens.gamedetail.delegates.DownloadDelegate>(relaxed = true) {
        every { state } returns downloadState
        every { launchEvents } returns MutableSharedFlow()
        every { updateDownloadStatus(any(), any()) } answers {
            downloadState.value = downloadState.value.copy(downloadStatus = firstArg())
        }
    }
    private val streamController = mockk<StreamLaunchController>(relaxed = true)
    private val streamLaunch = mockk<StreamLaunchController.Factory> {
        every { create(any()) } returns streamController
    }

    private fun entity(
        source: GameSource = GameSource.ROMM_REMOTE,
        title: String = "The Legend of Zelda: Tears of the Kingdom",
        rommId: Long? = 29L
    ) = GameEntity(
        id = 7L,
        platformId = 1L,
        platformSlug = "switch",
        title = title,
        sortTitle = "legend of zelda tears of the kingdom",
        localPath = null,
        rommId = rommId,
        igdbId = null,
        source = source
    )

    private fun viewModel(settings: StreamSettings = StreamSettings()) = GameDetailViewModel(
        context = mockk(relaxed = true),
        gameDiscDao = mockk(relaxed = true),
        gameFileDao = mockk(relaxed = true),
        platformRepository = mockk(relaxed = true),
        emulatorConfigDao = mockk(relaxed = true),
        emulatorDetector = emulatorDetector,
        emulatorResolver = mockk(relaxed = true),
        stateSupportResolver = mockk(relaxed = true),
        prefetchGameSaveDataUseCase = mockk(relaxed = true),
        builtinCoreResolver = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true),
        gameRepository = gameRepository,
        activeSaveRepository = mockk(relaxed = true),
        gameNavigationContext = mockk(relaxed = true),
        configureEmulatorUseCase = mockk(relaxed = true),
        romMRepository = mockk(relaxed = true),
        soundManager = mockk(relaxed = true),
        gameActions = mockk(relaxed = true),
        gameLaunchDelegate = launchDelegate,
        playSessionTracker = tracker,
        collectionModalDelegate = mockk(relaxed = true) {
            every { state } returns MutableStateFlow(
                com.nendo.argosy.ui.screens.common.CollectionModalDelegate.State()
            )
        },
        imageCacheManager = mockk(relaxed = true),
        preferencesRepository = preferences,
        gameUpdateBus = com.nendo.argosy.core.event.GameUpdateBus(),
        repairImageCacheUseCase = mockk(relaxed = true),
        modalResetSignal = com.nendo.argosy.ui.ModalResetSignal(),
        titleIdDownloadObserver = mockk(relaxed = true),
        gameDocumentLoader = mockk(relaxed = true),
        documentHighlightStore = mockk(relaxed = true),
        pickerModalDelegate = mockk(relaxed = true) {
            every { state } returns MutableStateFlow(
                com.nendo.argosy.ui.screens.gamedetail.delegates.PickerModalState()
            )
            every { selection } returns MutableStateFlow(null)
        },
        achievementDelegate = mockk(relaxed = true) {
            every { achievements } returns MutableStateFlow(emptyList())
        },
        downloadDelegate = downloadDelegate,
        saveManagement = mockk(relaxed = true) {
            every { saveChannelDelegate } returns mockk(relaxed = true) {
                every { state } returns MutableStateFlow(
                    com.nendo.argosy.ui.common.savechannel.SaveChannelState()
                )
                every { saveStatusEvents } returns MutableSharedFlow()
            }
        },
        screenshotDelegate = mockk(relaxed = true) {
            every { state } returns MutableStateFlow(
                com.nendo.argosy.ui.screens.gamedetail.delegates.ScreenshotState()
            )
        },
        ratingsStatus = mockk(relaxed = true) {
            every { state } returns MutableStateFlow(
                com.nendo.argosy.ui.screens.gamedetail.delegates.RatingsStatusState()
            )
        },
        artworkDelegate = mockk(relaxed = true),
        playOptionsDelegate = mockk(relaxed = true) {
            every { state } returns MutableStateFlow(
                com.nendo.argosy.ui.screens.gamedetail.delegates.PlayOptionsState()
            )
        },
        moreOptionsDelegate = mockk(relaxed = true) {
            every { state } returns MutableStateFlow(
                com.nendo.argosy.ui.screens.gamedetail.delegates.MoreOptionsState()
            )
        },
        perGameSettingsDelegate = mockk(relaxed = true) {
            every { state } returns MutableStateFlow(
                com.nendo.argosy.ui.screens.gamedetail.delegates.PerGameSettingsState()
            )
        },
        speedrunSplitsDelegate = mockk(relaxed = true),
        socialRepository = mockk(relaxed = true) {
            every { hiddenGameIds } returns MutableStateFlow(emptySet())
            every { connectionState } returns MutableStateFlow(
                mockk<com.nendo.argosy.data.social.SocialConnectionState>()
            )
            every { friendsActivity } returns MutableStateFlow(emptyMap())
            every { reviewSummaries } returns MutableStateFlow(emptyMap())
            every { gameReviews } returns MutableStateFlow(emptyMap())
            every { reviewWriteEvents } returns MutableSharedFlow()
        },
        steamLauncherLinkController = mockk(relaxed = true),
        variantScanner = mockk(relaxed = true),
        variantResolver = mockk(relaxed = true),
        downloadManager = mockk(relaxed = true),
        downloadFileStatusRepository = mockk(relaxed = true),
        getRelatedGamesUseCase = mockk(relaxed = true),
        gradientExtractionDelegate = mockk(relaxed = true) {
            every { gradients } returns MutableStateFlow(emptyMap())
        },
        gameThemeAudio = mockk(relaxed = true),
        siblingChoice = mockk(relaxed = true) {
            every { state } returns MutableStateFlow(null)
        },
        steamInstallReports = mockk(relaxed = true),
        appsRepository = mockk(relaxed = true) {
            every { packageChanges } returns emptyFlow()
        },
        runningGames = mockk {
            every { state } returns MutableStateFlow(com.nendo.argosy.data.emulator.background.RunningGamesSnapshot())
        },
        runningGameMenu = mockk(relaxed = true),
        streamLaunch = streamLaunch
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { preferences.userPreferences } returns flowOf(UserPreferences())
        every { emulatorDetector.installedEmulators } returns
            MutableStateFlow(listOf(mockk<InstalledEmulator>(relaxed = true)))
        every { streamController.offers } returns emptyFlow()
        coEvery { streamController.inventoryFor(any()) } returns (true to null)
        every { streamController.offerFor(any(), any<String>(), any(), any(), any(), any(), any(), any(), any()) } answers {
            streamController.offerFor(arg(0), arg<String>(1), arg(2), arg(3), arg(4), arg(5), arg(6), arg(7))
        }
        every { streamController.offerFor(any(), any<String>(), any(), any(), any(), any(), any(), any()) } returns
            com.nendo.argosy.ui.screens.stream.StreamOffer("", false, kind = com.nendo.argosy.data.stream.StreamKind.MOONLIGHT)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `open on a moonlight game emits the stream intent and never touches the container controller`() =
        runTest(dispatcher) {
            coEvery { gameRepository.getById(7L) } returns entity()
            val vm = viewModel()
            val events = mutableListOf<LaunchEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) { vm.launchEvents.collect { events.add(it) } }
            vm.loadGame(7L)
            advanceUntilIdle()

            clearMocks(launchDelegate, tracker, answers = false)
            vm.openStream()
            advanceUntilIdle()

            assertEquals(1, events.filterIsInstance<LaunchEvent.StreamIntent>().size)
            verify(exactly = 0) { streamController.open(any()) }
            verify { launchDelegate wasNot io.mockk.Called }
            verify { tracker wasNot io.mockk.Called }
        }

    @Test
    fun `container dispatch retains the validated game after navigation`() = runTest(dispatcher) {
        val inventory = CompletableDeferred<Pair<Boolean, com.nendo.argosy.data.model.stream.InventorySnapshot?>>()
        coEvery { streamController.inventoryFor(any()) } coAnswers { inventory.await() }
        val container = com.nendo.argosy.ui.screens.stream.StreamOffer("eden", false)
        every { streamController.offerFor(any(), any<String>(), any(), any(), any(), any(), any(), any()) } returns container
        every { streamController.offerFor(any(), any<String>(), any(), any(), any(), any(), any(), any(), any()) } returns container
        coEvery { gameRepository.getById(7L) } returns entity()
        coEvery { gameRepository.getById(8L) } returns entity(title = "Other", rommId = 30).copy(id = 8)
        val vm = viewModel()
        vm.loadGame(7L)
        advanceUntilIdle()
        assertEquals(com.nendo.argosy.data.stream.StreamKind.CONTAINER, vm.uiState.value.stream?.kind)
        vm.openStream()
        runCurrent()
        vm.loadGame(8L)
        runCurrent()
        inventory.complete(true to null)
        advanceUntilIdle()
        verify(exactly = 0) { streamController.open(8L) }
        verify(exactly = 1) { streamController.open(7L) }
    }

    @Test
    fun `dispatch rejects a moonlight offer suppressed after the menu was drawn`() = runTest(dispatcher) {
        coEvery { gameRepository.getById(7L) } returns entity()
        val vm = viewModel()
        val events = mutableListOf<LaunchEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            vm.launchEvents.collect { events.add(it) }
        }
        vm.loadGame(7L)
        advanceUntilIdle()
        assertEquals(com.nendo.argosy.data.stream.StreamKind.MOONLIGHT, vm.uiState.value.stream?.kind)
        every { streamController.offerFor(any(), any<String>(), any(), any(), any(), any(), any(), any(), any()) } returns null
        clearMocks(launchDelegate, tracker, answers = false)
        vm.openStream()
        advanceUntilIdle()
        assertEquals(0, events.filterIsInstance<LaunchEvent.StreamIntent>().size)
        verify(exactly = 0) { streamController.open(any()) }
        verify { launchDelegate wasNot io.mockk.Called }
        verify { tracker wasNot io.mockk.Called }
    }

    @Test
    fun `open on a steam game emits the stream intent and never touches the container controller`() =
        runTest(dispatcher) {
            coEvery { gameRepository.getById(7L) } returns
                entity(source = GameSource.STEAM, title = "Hades", rommId = null)
            val vm = viewModel()
            val events = mutableListOf<LaunchEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) { vm.launchEvents.collect { events.add(it) } }
            vm.loadGame(7L)
            advanceUntilIdle()

            withContext(Dispatchers.IO) { withTimeout(5_000) { vm.uiState.first { it.game?.title == "Hades" } } }
            advanceUntilIdle()
            clearMocks(launchDelegate, tracker, answers = false)
            vm.openStream()
            advanceUntilIdle()

            assertEquals(1, events.filterIsInstance<LaunchEvent.StreamIntent>().size)
            verify(exactly = 0) { streamController.open(any()) }
            verify { launchDelegate wasNot io.mockk.Called }
            verify { tracker wasNot io.mockk.Called }
        }
    @Test
    fun `missing server file leads with moonlight and primary action dispatches intent`() = runTest(dispatcher) {
        coEvery { gameRepository.getById(7L) } returns entity().copy(hasFileOnDisk = false)
        coEvery { gameRepository.validateAndDiscoverGame(7L) } returns false
        every { streamController.offerFor(any(), any<String>(), any(), any(), any(), any(), any(), any()) } answers {
            com.nendo.argosy.ui.screens.stream.StreamOffer("", false, leads = !arg<Boolean>(7),
                kind = com.nendo.argosy.data.stream.StreamKind.MOONLIGHT)
        }
        val vm = viewModel()
        val events = mutableListOf<LaunchEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler), start = CoroutineStart.UNDISPATCHED) {
            vm.launchEvents.collect { events.add(it) }
        }
        vm.loadGame(7L)
        withContext(Dispatchers.IO) { withTimeout(5_000) { vm.uiState.first { it.game != null && it.downloadStatus == GameDownloadStatus.NO_FILE } } }
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.stream?.leads)
        clearMocks(launchDelegate, tracker, answers = false)
        vm.primaryAction()
        advanceUntilIdle()
        assertEquals(1, events.filterIsInstance<LaunchEvent.StreamIntent>().size)
        verify(exactly = 0) { streamController.open(any()) }
        verify { launchDelegate wasNot io.mockk.Called }
        verify { tracker wasNot io.mockk.Called }
        downloadState.value = downloadState.value.copy(downloadStatus = GameDownloadStatus.DOWNLOADED)
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.stream?.leads)
        downloadState.value = downloadState.value.copy(downloadStatus = GameDownloadStatus.PAUSED)
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.stream?.leads)
    }

}
