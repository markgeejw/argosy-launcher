package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.fixtures.notRoutedSigil
import com.nendo.argosy.data.sync.fixtures.realFsFal
import com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class N3dsRestoreDoorsTest {
    private lateinit var temp: File
    private lateinit var data: File
    private lateinit var archiver: SaveArchiver
    private lateinit var registry: PlatformSaveHandlerRegistry
    private lateinit var client: SaveSyncApiClient
    private lateinit var downloader: SaveDownloader
    private val context = mockk<Context>(relaxed = true)
    private val games = mockk<GameDao>(relaxed = true)
    private val sync = mockk<SaveSyncDao>(relaxed = true)
    private val prefs = mockk<SyncPreferencesRepository>(relaxed = true)
    private val users = mockk<UserPreferencesRepository>(relaxed = true)
    private val cache = mockk<SaveCacheManager>(relaxed = true)
    private val active = mockk<ActiveSaveRepository>(relaxed = true)
    private val uploader = mockk<SaveUploader>(relaxed = true)
    private val api = mockk<RomMApi>(relaxed = true)
    private val fal = realFsFal()
    private val sigil = notRoutedSigil()
    private var game = game("0004000000055E00")

    private fun game(title: String) = GameEntity(
        id = 4L, title = "Game", sortTitle = "game", platformId = 3L, platformSlug = "3ds",
        rommId = 4L, igdbId = null, localPath = "/roms/game.3ds", source = GameSource.ROMM_SYNCED,
        titleId = title, saveId = "${title.take(8)}/${title.takeLast(8)}"
    )

    @Before
    fun setUp() {
        temp = createTempDirectory("n3ds_restore_doors").toFile()
        every { context.cacheDir } returns File(temp, "scratch").apply { mkdirs() }
        coEvery { games.getById(4L) } answers { game }
        coEvery { prefs.getRommUserId() } returns 7L
        coEvery { cache.findCachedByHash(any(), any()) } returns null
        coEvery { cache.protectBeforeOverwrite(any(), any(), any()) } returns true
        coEvery { cache.hasHardcoreSave(any()) } returns false
        archiver = SaveArchiver(mockk(relaxed = true), fal)
        registry = PlatformSaveHandlerRegistry(
            context = context, fal = fal, saveArchiver = archiver,
            switchSaveHandler = mockk(relaxed = true), gciSaveHandler = mockk(relaxed = true),
            retroArchSaveHandler = mockk(relaxed = true), dreamcastSaveHandler = mockk(relaxed = true),
            defaultSaveHandler = mockk(relaxed = true), unitSaveHandler = mockk(relaxed = true), sigilSaveHandler = sigil
        )
        client = spyk(SaveSyncApiClient(
            saveSyncDao = sync, emulatorConfigDao = mockk(relaxed = true), emulatorResolver = mockk(relaxed = true),
            builtinCoreResolver = mockk(relaxed = true), gameDao = games, savePathResolver = mockk(relaxed = true),
            userPreferencesRepository = users, fal = fal, switchSaveHandler = mockk(relaxed = true),
            gciSaveHandler = mockk(relaxed = true), saveHandlerRegistry = registry, conflictDetector = mockk(relaxed = true),
            saveUploader = { uploader }, saveDownloader = { mockk(relaxed = true) }, syncPreferencesRepository = prefs,
            snapshotRouter = { mockk(relaxed = true) }
        ))
        client.setApi(api)
        client.setDeviceId("test-device")
        every { client.hasEnoughDiskSpace(any(), any()) } returns true
        downloader = SaveDownloader(
            context = context, saveSyncDao = sync, saveCacheDao = mockk(relaxed = true),
            emulatorResolver = mockk(relaxed = true), gameDao = games, activeSaveRepository = active,
            titleDbRepository = mockk(relaxed = true), titleIdExtractor = mockk(relaxed = true),
            saveArchiver = archiver, savePathResolver = mockk(relaxed = true), syncPreferencesRepository = prefs,
            saveCacheManager = { cache }, fal = fal, switchSaveHandler = mockk(relaxed = true),
            gciSaveHandler = mockk(relaxed = true), apiClient = { client }, saveUploader = { uploader },
            emulatorSaveConfigRepository = mockk(relaxed = true), unitSaveHandler = mockk(relaxed = true),
            saveUnitResolver = mockk(relaxed = true), sigilSaveHandler = sigil
        )
        targetForGame()
    }

    private fun targetForGame() {
        data = File(temp, "${ROOT}title/${game.saveId!!.lowercase()}/data").apply { mkdirs() }
        File(data, "local").writeText("local data")
        coEvery { sync.getByGameEmulatorAndChannel(any(), any(), any(), any()) } returns SaveSyncEntity(
            gameId = 4L, rommId = 4L, emulatorId = "azahar", channelName = "autosave", rommSaveId = 51L,
            localSavePath = data.path, syncStatus = SaveSyncEntity.STATUS_SERVER_NEWER
        )
    }

    @After
    fun tearDown() { temp.deleteRecursively() }

    private fun zip(vararg names: String): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            names.forEach {
                zip.putNextEntry(ZipEntry(it))
                zip.write("cloud".toByteArray())
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private fun serve(bytes: ByteArray): File {
        val original = File(temp, "original.zip").apply { writeBytes(bytes) }
        coEvery { api.getSaveWithDevice(51L, "test-device") } returns Response.success(RomMSave(
            id = 51L, romId = 4L, userId = 7L, emulator = "azahar", fileName = "game.zip",
            downloadPath = "/saves/game.zip", updatedAt = "2026-10-01T00:00:00Z"
        ))
        coEvery { api.downloadRaw(any()) } answers { Response.success(original.readBytes().toResponseBody()) }
        return original
    }

    @Test
    fun `ordinary API pre-clear defers all 3ds deletion until placement validation`() = runTest {
        val ext = File(temp, "${ROOT}extdata/00000000/0000055D/main").apply { parentFile?.mkdirs(); writeText("local ext") }
        assertTrue(client.clearSavesBeforeRestore(data.path, "3ds", game.saveId, setOf("data", "extdata")))
        assertEquals("local data", File(data, "local").readText())
        assertEquals("local ext", ext.readText())
    }

    @Test
    fun `ordinary download places corrected extdata and leaves stale derived directory alone`() = runTest {
        val bytes = zip("data/main", "extdata/main")
        val source = serve(bytes)
        val ext = File(temp, "${ROOT}extdata/00000000/0000055D/main").apply { parentFile?.mkdirs(); writeText("old") }
        val stale = File(temp, "${ROOT}extdata/00000000/0000055e/main").apply { parentFile?.mkdirs(); writeText("stale") }
        val result = downloader.downloadSave(4L, "azahar")
        assertTrue("$result", result is SaveSyncResult.Success)
        assertEquals("cloud", File(data, "main").readText())
        assertEquals("cloud", ext.readText())
        assertEquals("stale", stale.readText())
        assertArrayEquals(bytes, source.readBytes())
    }

    @Test
    fun `known none download is placement refusal not corrupt recovery or successful sync`() = runTest {
        game = game("000400000008D300")
        targetForGame()
        val bytes = zip("data/main", "extdata/main")
        val source = serve(bytes)
        val result = downloader.downloadSave(4L, "azahar")
        assertTrue("$result", result is SaveSyncResult.Error)
        assertEquals("local data", File(data, "local").readText())
        assertFalse(File(data, "main").exists())
        assertArrayEquals(bytes, source.readBytes())
        coVerify(exactly = 0) { uploader.uploadSave(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { sync.markCorruptZip(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { sync.upsert(any()) }
        coVerify(exactly = 0) { api.confirmSaveDownloaded(any(), any()) }
        coVerify(exactly = 0) { active.activateCache(any(), any()) }
    }

    @Test
    fun `damaged zip triggers corrupt recovery without deleting local data`() = runTest {
        for (bytes in listOf("not a zip".toByteArray(), damagedZip())) {
            val source = serve(bytes)
            coEvery { uploader.uploadSave(any(), any(), any(), any(), any(), any()) } coAnswers {
                assertEquals("local data", File(data, "local").readText())
                SaveSyncResult.Error("test recovery refused")
            }
            val result = downloader.downloadSave(4L, "azahar")
            assertTrue("$result", result is SaveSyncResult.Error)
            assertEquals("local data", File(data, "local").readText())
            assertFalse(File(data, "main").exists())
            assertArrayEquals(bytes, source.readBytes())
        }
        coVerify(exactly = 2) { sync.markCorruptZip(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 2) { uploader.uploadSave(4L, "azahar", any(), true, false, any()) }
        coVerify(exactly = 0) { sync.upsert(any()) }
        coVerify(exactly = 0) { api.confirmSaveDownloaded(any(), any()) }
    }

    private fun damagedZip(): ByteArray {
        val payloadBytes = "cloud".toByteArray()
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val entry = ZipEntry("data/main").apply {
                method = ZipEntry.STORED
                size = payloadBytes.size.toLong()
                compressedSize = size
                crc = java.util.zip.CRC32().also { it.update(payloadBytes) }.value
            }
            zip.putNextEntry(entry)
            zip.write(payloadBytes)
            zip.closeEntry()
        }
        val bytes = output.toByteArray()
        val nameSize = (bytes[26].toInt() and 255) + ((bytes[27].toInt() and 255) shl 8)
        val extraSize = (bytes[28].toInt() and 255) + ((bytes[29].toInt() and 255) shl 8)
        val payload = 30 + nameSize + extraSize
        bytes[payload] = (bytes[payload].toInt() xor 255).toByte()
        return bytes
    }

    @Test
    fun `valid zip with an invalid filesystem destination is refused without corrupt recovery`() = runTest {
        val bytes = zip("data/invalid\u0000name")
        serve(bytes)
        assertTrue(downloader.downloadSave(4L, "azahar") is SaveSyncResult.Error)
        assertEquals("local data", File(data, "local").readText())
        coVerify(exactly = 0) { sync.markCorruptZip(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { uploader.uploadSave(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { sync.upsert(any()) }
    }

    @Test
    fun `ambiguous extdata blocks download clear and upload without deleting or advancing saves`() = runTest {
        val parent = File(temp, "${ROOT}extdata/00000000")
        val lower = File(parent, "0000055d/main").apply { parentFile?.mkdirs(); writeText("lower") }
        val upper = File(parent, "0000055D/main").apply { parentFile?.mkdirs(); writeText("upper") }
        serve(zip("data/main", "extdata/main"))
        assertTrue(downloader.downloadSave(4L, "azahar") is SaveSyncResult.Error)
        assertFalse(client.clearSavesBeforeRestore(data.path, "3ds", game.saveId, setOf("data", "extdata")))
        val realUploader = SaveUploader(
            context, sync, mockk(relaxed = true), mockk(relaxed = true), games, mockk(relaxed = true),
            archiver, mockk(relaxed = true), fal, registry, dagger.Lazy { client }, mockk(relaxed = true),
            dagger.Lazy { cache }, prefs, mockk(relaxed = true)
        )
        assertTrue(realUploader.uploadSave(4L, "azahar") is SaveSyncResult.Error)
        assertEquals("local data", File(data, "local").readText())
        assertEquals("lower", lower.readText())
        assertEquals("upper", upper.readText())
        coVerify(exactly = 0) { sync.upsert(any()) }
        coVerify(exactly = 0) { sync.markCorruptZip(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { api.confirmSaveDownloaded(any(), any()) }
        coVerify(exactly = 0) { api.uploadSave(any(), any(), any(), any(), any(), any<okhttp3.MultipartBody.Part>(), any()) }
        coVerify(exactly = 0) { api.uploadSaveWithDevice(any(), any(), any(), any(), any(), any(), any(), any<okhttp3.MultipartBody.Part>(), any()) }
    }

    @Test
    fun `hardcore downgrade of a CRC damaged zip returns an error without advancing saves`() = runTest {
        assertCorruptHardcoreDowngrade(damagedZip())
    }

    @Test
    fun `hardcore downgrade of a non zip returns an error without advancing saves`() = runTest {
        assertCorruptHardcoreDowngrade("not a zip".toByteArray())
    }

    private suspend fun assertCorruptHardcoreDowngrade(bytes: ByteArray) {
        val ext = File(temp, "${ROOT}extdata/00000000/0000055D/main").apply {
            parentFile?.mkdirs()
            writeText("local ext")
        }
        val original = File(temp, "owned.zip").apply { writeBytes(bytes) }
        val disposable = File(context.cacheDir, "held.zip").apply { writeBytes(bytes) }
        val resolver = SaveSyncConflictResolver(
            sync, mockk(relaxed = true), mockk(relaxed = true), games, archiver, mockk(relaxed = true),
            users, prefs, { cache }, { client }, fal, registry, mockk(relaxed = true)
        )
        val result = resolver.resolveHardcoreConflict(
            SaveSyncResult.NeedsHardcoreResolution(
                disposable.path, 4L, "Game", "azahar", data.path, true, "autosave"
            ), HardcoreResolutionChoice.DOWNGRADE_TO_CASUAL
        )
        assertTrue("$result", result is SaveSyncResult.Error)
        assertTrue((result as SaveSyncResult.Error).message.startsWith("Corrupt server zip: "))
        assertEquals("local data", File(data, "local").readText())
        assertEquals("local ext", ext.readText())
        assertFalse(File(data, "main").exists())
        assertArrayEquals(bytes, original.readBytes())
        assertFalse(disposable.exists())
        coVerify(exactly = 0) { sync.upsert(any()) }
        coVerify(exactly = 0) { active.activateCache(any(), any()) }
        coVerify(exactly = 0) {
            cache.cacheCurrentSave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
        coVerify(exactly = 0) { api.confirmSaveDownloaded(any(), any()) }
    }

    @Test
    fun `hardcore downgrade refusal keeps valid components and caller owned archive without advancing sync`() = runTest {
        game = game("000400000008D300")
        targetForGame()
        val bytes = zip("data/main", "extdata/main")
        val original = File(temp, "owned.zip").apply { writeBytes(bytes) }
        val disposable = File(context.cacheDir, "held.zip").apply { writeBytes(bytes) }
        val resolver = SaveSyncConflictResolver(
            sync, mockk(relaxed = true), mockk(relaxed = true), games, archiver, mockk(relaxed = true),
            users, prefs, { cache }, { client }, fal, registry, mockk(relaxed = true)
        )
        val result = resolver.resolveHardcoreConflict(
            SaveSyncResult.NeedsHardcoreResolution(
                disposable.path, 4L, "Game", "azahar", data.path, true, "autosave"
            ), HardcoreResolutionChoice.DOWNGRADE_TO_CASUAL
        )
        assertTrue("$result", result is SaveSyncResult.Error)
        assertEquals("local data", File(data, "local").readText())
        assertArrayEquals(bytes, original.readBytes())
        assertFalse(disposable.exists())
        coVerify(exactly = 0) { sync.upsert(any()) }
        coVerify(exactly = 0) { active.activateCache(any(), any()) }
    }

    companion object {
        private const val ROOT = "sdmc/Nintendo 3DS/00000000000000000000000000000000/00000000000000000000000000000000/"
    }
}
