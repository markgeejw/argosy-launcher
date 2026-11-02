package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SigilSyncStateDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncOrchestrator
import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.sync.CrossEmulatorSaveMigrator
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.SaveOwnershipTracker
import com.nendo.argosy.data.sync.fixtures.realFsFal
import com.nendo.sigil.Sigil
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class N3dsSigilRestorePathsTest {
    private lateinit var temp: File
    private lateinit var target: File
    private lateinit var manager: SaveCacheManager
    private lateinit var sigil: SigilSaveHandler
    private lateinit var registry: PlatformSaveHandlerRegistry
    private lateinit var route: SigilRoute
    private lateinit var fal: com.nendo.argosy.data.storage.FileAccessLayer
    private val context = mockk<Context>(relaxed = true)
    private val games = mockk<GameDao>(relaxed = true)
    private val caches = mockk<SaveCacheDao>(relaxed = true)
    private val states = mockk<SigilSyncStateDao>(relaxed = true)
    private val prefs = mockk<SyncPreferencesRepository>(relaxed = true)
    private val ownership = mockk<SaveOwnershipTracker>(relaxed = true)
    private val emulator = mockk<EmulatorResolver>(relaxed = true)
    private val active = mockk<ActiveSaveRepository>(relaxed = true)
    private val notifications = mockk<NotificationManager>(relaxed = true)
    private var game = game("0004000000055E00")

    @Before
    fun setUp() {
        val native = runCatching { Sigil.layoutSubdirs("azahar") }.isSuccess
        if (java.lang.Boolean.getBoolean("sigil.requireHostJni")) assertTrue("patched host JNI required", native)
        assumeTrue("run with -PsigilHostJniDir=<patched host build>/bindings", native)
        temp = createTempDirectory("n3ds_sigil_paths").toFile()
        target = File(temp, "target").apply { mkdirs() }
        route = SigilRoute("azahar", target.path, "org.azahar", null)
        every { context.filesDir } returns temp
        every { context.cacheDir } returns File(temp, "scratch").apply { mkdirs() }
        coEvery { games.getById(GAME_ID) } answers { game }
        coEvery { states.get(any(), any(), any(), any()) } returns null
        coEvery { prefs.getRommUserId() } returns 7L
        coEvery { prefs.isSecureSaves() } returns true
        fal = realFsFal()
        val archiver = SaveArchiver(mockk<AndroidDataAccessor>(relaxed = true), fal)
        sigil = spyk(SigilSaveHandler(
            context, games, emulator, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), fal, { mockk(relaxed = true) }, mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), states, prefs,
            mockk(relaxed = true), mockk(relaxed = true)
        ))
        coEvery { sigil.route(any(), any()) } returns null
        coEvery { sigil.localRoute(GAME_ID, "azahar") } returns route
        coEvery { sigil.localRoute(GAME_ID, "citra") } returns route.copy(layout = "citra", root = File(temp, "source").path)
        registry = PlatformSaveHandlerRegistry(
            context = context, fal = fal, saveArchiver = archiver,
            switchSaveHandler = mockk(relaxed = true), gciSaveHandler = mockk(relaxed = true),
            retroArchSaveHandler = mockk(relaxed = true), dreamcastSaveHandler = mockk(relaxed = true),
            defaultSaveHandler = mockk(relaxed = true), unitSaveHandler = mockk(relaxed = true), sigilSaveHandler = sigil
        )
        val userPrefs = mockk<UserPreferencesRepository>(relaxed = true)
        every { userPrefs.userPreferences } returns flowOf(UserPreferences())
        manager = SaveCacheManager(
            context, caches, mockk(relaxed = true), mockk(relaxed = true), games, userPrefs, prefs,
            mockk(relaxed = true), archiver, fal, registry, ownership, mockk(relaxed = true),
            mockk(relaxed = true), GciSaveHandler(context, fal, archiver), sigil
        )
    }

    @After
    fun tearDown() {
        if (::temp.isInitialized) temp.deleteRecursively()
    }

    private fun game(id: String) = GameEntity(
        id = GAME_ID, title = "3DS Game", sortTitle = "3ds game", platformId = 3L,
        platformSlug = "3ds", rommId = 4L, igdbId = null, localPath = null,
        source = GameSource.ROMM_SYNCED, titleId = id, saveId = "${id.take(8)}/${id.takeLast(8)}"
    )

    private fun zip(vararg names: String): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            names.forEach {
                zip.putNextEntry(ZipEntry(it))
                zip.write("cloud".toByteArray())
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private fun cache(format: String, bytes: ByteArray): Pair<SaveCacheEntity, File> {
        val file = File(temp, "save_cache/unit.zip").apply { parentFile?.mkdirs(); writeBytes(bytes) }
        val row = SaveCacheEntity(
            id = 31L, gameId = GAME_ID, emulatorId = "citra", cachedAt = Instant.EPOCH,
            saveSize = bytes.size.toLong(), cachePath = "unit.zip", contentHash = null, saveFormat = format
        )
        coEvery { caches.getById(row.id) } returns row
        coEvery { active.getActiveRow(GAME_ID) } returns row
        return row to file
    }

    private fun local(path: String, value: String = "local"): File = File(target, path).apply {
        parentFile?.mkdirs()
        writeText(value)
    }

    @Test
    fun `neutral cache uses native corrected extdata while preserving stale derived and absent data`() = runTest {
        val (row, _) = cache(SaveCacheEntity.FORMAT_NEUTRAL, zip("extdata/main"))
        val data = local("${TITLE}00040000/00055e00/data/main")
        val ext = local("${EXT}0000055D/main")
        val stale = local("${EXT}0000055e/main")
        assertTrue(manager.restoreSave(row.id, target.path, "azahar"))
        assertEquals("cloud", ext.readText())
        assertEquals("local", data.readText())
        assertEquals("local", stale.readText())
        assertFalse(File(ext.parentFile.parentFile, "0000055d").exists())
    }

    @Test
    fun `native cache goes through Kotlin placement with the same corrected case destination`() = runTest {
        val (row, _) = cache(SaveCacheEntity.FORMAT_NATIVE, zip("data/main", "extdata/main"))
        val data = local("${TITLE}00040000/00055e00/data/main")
        val ext = local("${EXT}0000055D/main")
        val stale = local("${EXT}0000055e/main")
        assertTrue(manager.restoreSave(row.id, data.parent, "azahar"))
        assertEquals("cloud", data.readText())
        assertEquals("cloud", ext.readText())
        assertEquals("local", stale.readText())
    }

    @Test
    fun `known none mixed cache is refused through both formats without deleting local or archive`() = runTest {
        game = game("000400000008D300")
        for (format in listOf(SaveCacheEntity.FORMAT_NATIVE, SaveCacheEntity.FORMAT_NEUTRAL)) {
            val bytes = zip("data/new", "extdata/new")
            val (row, file) = cache(format, bytes)
            val data = local("${TITLE}00040000/0008d300/data/main")
            val stale = local("${EXT}000008d3/main")
            assertFalse(manager.restoreSave(row.id, data.parent, "azahar"))
            assertEquals("local", data.readText())
            assertEquals("local", stale.readText())
            assertArrayEquals(bytes, file.readBytes())
        }
    }

    @Test
    fun `native migration converts legacy roots through validated native restore then collects both components`() = runTest {
        val (_, file) = cache(SaveCacheEntity.FORMAT_NATIVE, zip("data/main", "extdata/main"))
        val bytes = file.readBytes()
        assertEquals(CrossEmulatorSaveMigrator.Outcome.Placed, migrator().migrateBeforeLaunch(GAME_ID))
        assertEquals("cloud", File(target, "${TITLE}00040000/00055e00/data/main").readText())
        assertEquals("cloud", File(target, "${EXT}0000055d/main").readText())
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun `neutral migration goes directly through native restore without snapshot capability`() = runTest {
        cache(SaveCacheEntity.FORMAT_NEUTRAL, zip("00040000/00055e00/data/main", "00040000/00055e00/extdata/main"))
        assertEquals(CrossEmulatorSaveMigrator.Outcome.Placed, migrator().migrateBeforeLaunch(GAME_ID))
        assertEquals("cloud", File(target, "${EXT}0000055d/main").readText())
    }

    @Test
    fun `unplaceable extdata refuses migration without mutating existing title data or cache`() = runTest {
        game = game("000400000008D300")
        for (format in listOf(SaveCacheEntity.FORMAT_NATIVE, SaveCacheEntity.FORMAT_NEUTRAL)) {
            val bytes = zip("data/new", "extdata/new")
            val (_, file) = cache(format, bytes)
            val data = local("${TITLE}00040000/0008d300/data/main")
            coEvery { sigil.probe(route, GAME_ID) } returns SigilProbe.Absent
            assertTrue(migrator().migrateBeforeLaunch(GAME_ID) is CrossEmulatorSaveMigrator.Outcome.Refused)
            assertEquals("local", data.readText())
            assertArrayEquals(bytes, file.readBytes())
            coVerify(exactly = 0) { states.upsert(any()) }
        }
    }

    @Test
    fun `empty extdata directory hints preserve case without fabricating saves`() = runTest {
        for (id in listOf("0000055d", "0000055D")) {
            target = File(temp, id).apply { mkdirs() }
            route = route.copy(root = target.path)
            coEvery { sigil.route(GAME_ID, "azahar") } returns route
            val directory = File(target, "$EXT$id").apply { mkdirs() }
            assertEquals(SigilCollect.Absent, sigil.collect(GAME_ID, "azahar"))
            assertEquals(SigilProbe.Absent, sigil.probe(route, GAME_ID))
            assertTrue(sigil.restoreInto(route, GAME_ID, zip("extdata/main")) is SigilRestore.Restored)
            assertEquals("cloud", File(directory, "main").readText())
            val sibling = if (id.endsWith('D')) "0000055d" else "0000055D"
            assertFalse(File(directory.parentFile, sibling).exists())
        }
    }

    @Test
    fun `empty case collisions refuse collect probe and restore without deleting aliases`() = runTest {
        coEvery { sigil.route(GAME_ID, "azahar") } returns route
        val lower = File(target, "${EXT}0000055d").apply { mkdirs() }
        val upper = File(target, "${EXT}0000055D").apply { mkdirs() }
        val data = local("${TITLE}00040000/00055e00/data/main")
        assertTrue(sigil.collect(GAME_ID, "azahar") is SigilCollect.Unreadable)
        assertTrue(sigil.probe(route, GAME_ID) is SigilProbe.Unreadable)
        assertTrue(sigil.restoreInto(route, GAME_ID, zip("data/main", "extdata/main")) is SigilRestore.Refused)
        assertEquals("local", data.readText())
        assertTrue(lower.isDirectory && upper.isDirectory)
        assertTrue(lower.listFiles().isNullOrEmpty() && upper.listFiles().isNullOrEmpty())
    }

    @Test
    fun `known none empty extdata variants are not hints or saves`() = runTest {
        game = game("000400000008D300")
        coEvery { sigil.route(GAME_ID, "azahar") } returns route
        val lower = File(target, "${EXT}000008d3").apply { mkdirs() }
        val upper = File(target, "${EXT}000008D3").apply { mkdirs() }
        assertEquals(SigilCollect.Absent, sigil.collect(GAME_ID, "azahar"))
        assertEquals(SigilProbe.Absent, sigil.probe(route, GAME_ID))
        assertTrue(sigil.restoreInto(route, GAME_ID, zip("extdata/main")) is SigilRestore.Refused)
        assertTrue(lower.listFiles().isNullOrEmpty() && upper.listFiles().isNullOrEmpty())
    }

    @Test
    fun `unreadable extdata is not mistaken for an absent empty directory`() = runTest {
        coEvery { sigil.route(GAME_ID, "azahar") } returns route
        val parent = File(target, EXT).apply { mkdirs() }
        every { fal.listFilesUnion(parent.path.trimEnd('/')) } returns emptyList()
        every { fal.isListable(parent.path.trimEnd('/')) } returns false
        assertTrue(sigil.collect(GAME_ID, "azahar") is SigilCollect.Unreadable)
        assertTrue(sigil.probe(route, GAME_ID) is SigilProbe.Unreadable)
        assertTrue(sigil.restoreInto(route, GAME_ID, zip("extdata/main")) is SigilRestore.Refused)
        assertTrue(parent.listFiles().isNullOrEmpty())
    }

    @Test
    fun `a directory hint alias outside the save root is refused`() = runTest {
        coEvery { sigil.route(GAME_ID, "azahar") } returns route
        val outside = File(temp, "outside").apply { mkdirs() }
        val alias = File(target, "${EXT}0000055D")
        alias.parentFile?.mkdirs()
        java.nio.file.Files.createSymbolicLink(alias.toPath(), outside.toPath())
        assertTrue(sigil.collect(GAME_ID, "azahar") is SigilCollect.Unreadable)
        assertTrue(sigil.probe(route, GAME_ID) is SigilProbe.Unreadable)
        assertTrue(sigil.restoreInto(route, GAME_ID, zip("extdata/main")) is SigilRestore.Refused)
        assertTrue(outside.listFiles().isNullOrEmpty())
        assertTrue(java.nio.file.Files.isSymbolicLink(alias.toPath()))
    }

    private fun migrator(): CrossEmulatorSaveMigrator {
        coEvery { emulator.getEmulatorPackageForGame(any(), any(), any()) } returns "org.azahar"
        every { emulator.resolveEmulatorId("org.azahar") } returns "azahar"
        coEvery { caches.getMostRecent(GAME_ID, 7L) } returns null
        coEvery { prefs.isSaveMigrationNoticeShown(any()) } returns false
        return CrossEmulatorSaveMigrator(
            games, active, caches, emulator, sigil, manager, mockk<SaveSyncOrchestrator>(relaxed = true),
            prefs, ownership, notifications
        )
    }

    companion object {
        private const val GAME_ID = 4L
        private const val ROOT = "sdmc/Nintendo 3DS/00000000000000000000000000000000/00000000000000000000000000000000/"
        private const val TITLE = "${ROOT}title/"
        private const val EXT = "${ROOT}extdata/00000000/"
    }
}
