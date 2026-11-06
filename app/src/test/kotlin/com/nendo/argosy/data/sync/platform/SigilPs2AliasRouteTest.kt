package com.nendo.argosy.data.sync.platform

import android.content.Context
import android.os.Environment
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.EmulatorSaveConfigEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.repository.EmulatorSaveConfigRepository
import com.nendo.argosy.data.storage.FileAccessLayer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

class SigilPs2AliasRouteTest {

    private val context = mockk<Context>(relaxed = true)
    private val gameDao = mockk<GameDao>(relaxed = true)
    private val emulatorResolver = mockk<EmulatorResolver>(relaxed = true)
    private val saveConfigDao = mockk<EmulatorSaveConfigDao>()
    private val emulatorConfigDao = mockk<EmulatorConfigDao>(relaxed = true)
    private val fal = mockk<FileAccessLayer>(relaxed = true)
    private val connectionManager = mockk<RomMConnectionManager>()
    private val saveLocator = mockk<SigilSaveLocator>()
    private val configs = mutableMapOf<String, EmulatorSaveConfigEntity>()

    private val handler = SigilSaveHandler(
        context = context,
        gameDao = gameDao,
        emulatorResolver = emulatorResolver,
        emulatorSaveConfigRepository = EmulatorSaveConfigRepository(saveConfigDao),
        switchProfileParser = mockk(relaxed = true),
        titleDbRepository = mockk(relaxed = true),
        emulatorConfigDao = emulatorConfigDao,
        fal = fal,
        connectionManager = { connectionManager },
        saveUnitResolver = mockk(relaxed = true),
        builtinSaveBase = mockk(relaxed = true),
        retroArchPathResolver = mockk(relaxed = true),
        sigilSyncStateDao = mockk(relaxed = true),
        syncPreferencesRepository = mockk(relaxed = true),
        cartFeatureScanner = mockk(relaxed = true),
        saveLocator = saveLocator
    )

    @Before
    fun setUp() {
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns File(STORAGE)
        every { context.filesDir } returns File("/data/user/0/com.nendo.argosy/files")
        every { fal.externalStorageRoots() } returns listOf(STORAGE)
        every { fal.isDirectory(any()) } returns true
        every { connectionManager.snapshotsEnabled() } returns false
        every { saveLocator.locate(any(), any(), any()) } returns
            SigilSaveLocation(shared = listOf("memcards/Mcd001.ps2"), own = emptyList())
        coEvery { gameDao.getById(GAME_ID) } returns GameEntity(
            id = GAME_ID,
            title = "Suikoden III",
            sortTitle = "suikoden iii",
            platformId = 2L,
            platformSlug = "ps2",
            rommId = 64L,
            igdbId = null,
            localPath = null,
            source = GameSource.ROMM_SYNCED,
            titleId = "SLUS-20387",
            saveId = "BASLUS-20387"
        )
        coEvery { emulatorResolver.getEmulatorPackageForGame(any(), any(), any()) } returns NETHER_PACKAGE
        coEvery { emulatorConfigDao.getSelectedMemcardForGame(GAME_ID) } returns null
        coEvery { saveConfigDao.getByEmulator(any()) } answers { configs[firstArg()] }
        coEvery { saveConfigDao.getAll() } answers { configs.values.toList() }
        stored("nethersx2", NETHER_ROOT, "Mcd001.ps2")
    }

    @After
    fun tearDown() {
        unmockkStatic(Environment::class)
    }

    @Test
    fun `the local route for a shared-package alias uses the selected NetherSX2 folder and card`() = runTest {
        assertRoute(handler.localRoute(GAME_ID, "aethersx2"), NETHER_ROOT, "Mcd001.ps2")
    }

    @Test
    fun `a shared-package alias routes the selected NetherSX2 shared card without server units`() = runTest {
        assertRoute(handler.route(GAME_ID, "aethersx2"), NETHER_ROOT, "Mcd001.ps2")

        verify(exactly = 1) {
            saveLocator.locate(match { it.root == NETHER_ROOT }, any(), any())
        }
    }

    @Test
    fun `an explicit different-app local route uses its own folder and selected card`() = runTest {
        stored("armsx2", ARMS_ROOT, "ArmsSlot1.ps2")

        assertRoute(handler.localRoute(GAME_ID, "armsx2"), ARMS_ROOT, "ArmsSlot1.ps2")
    }

    @Test
    fun `an explicit different-app shared route uses its own folder and selected card without server units`() = runTest {
        stored("armsx2", ARMS_ROOT, "ArmsSlot1.ps2")

        assertRoute(handler.route(GAME_ID, "armsx2"), ARMS_ROOT, "ArmsSlot1.ps2")

        verify(exactly = 1) {
            saveLocator.locate(match { it.root == ARMS_ROOT }, any(), any())
        }
    }

    private fun stored(emulatorId: String, root: String, card: String) {
        configs[emulatorId] = EmulatorSaveConfigEntity(
            emulatorId = emulatorId,
            savePathPattern = "$root/memcards",
            isAutoDetected = false,
            isUserOverride = true,
            selectedMemcardPath = "$root/memcards/$card"
        )
    }

    private fun assertRoute(route: SigilRoute?, root: String, card: String) {
        assertEquals("pcsx2_standalone", route?.layout)
        assertEquals(root, route?.root)
        assertEquals(mapOf("Slot1_Filename" to card), route?.options)
    }

    private companion object {
        const val GAME_ID = 64L
        const val STORAGE = "/storage/emulated/0"
        const val NETHER_PACKAGE = "xyz.aethersx2.android"
        const val NETHER_ROOT = "$STORAGE/NetherSX2"
        const val ARMS_ROOT = "$STORAGE/ARMSX2"
    }
}
