package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.FileOrigin
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.PlatformFolderResolver
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SwitchBaseRomResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val gameDao = mockk<GameDao>(relaxed = true)
    private val platformDao = mockk<PlatformDao>(relaxed = true)
    private lateinit var switchRoot: File
    private lateinit var resolver: BaseRomFileResolver

    @Before
    fun setUp() {
        val storageRoot = tempFolder.newFolder("roms")
        switchRoot = File(storageRoot, "switch").apply { mkdirs() }
        val switch = PlatformEntity(
            id = 12L,
            slug = "switch",
            fsSlug = null,
            name = "Switch",
            shortName = "Switch",
            romExtensions = "nsp,xci,nsz,xcz"
        )
        coEvery { platformDao.getById(switch.id) } returns switch
        coEvery { platformDao.getAllPlatforms() } returns listOf(switch)

        val preferences = mockk<UserPreferencesRepository>()
        every { preferences.userPreferences } returns
            flowOf(UserPreferences(romStoragePath = storageRoot.absolutePath))

        resolver = BaseRomFileResolver(
            gameDao = gameDao,
            platformFolderResolver = PlatformFolderResolver(mockk(relaxed = true), platformDao, gameDao, preferences)
        )
    }

    @Test
    fun `an update alone in its game folder resolves to the base beside the game folders`() = runTest {
        val update = file("The Legend of Zelda_ Tears of the Kingdom/The Legend of Zelda Tears of the Kingdom [0100F2C0115B6800][v262144].nsp", 3)
        val base = file("The Legend of Zelda Tears of the Kingdom [0100F2C0115B6000][v0].nsp", 17)
        file("Other Game [0100AAAAAAAAA000][v0].nsp", 40)

        assertEquals(base, resolver.resolve(game(update), update))
        coVerify { gameDao.updateLocalPath(7L, base.absolutePath, GameSource.ROMM_SYNCED, FileOrigin.ADOPTED, any()) }
    }

    @Test
    fun `dlc in a game folder resolves to its base title`() = runTest {
        val dlc = file("Fire Emblem_ Three Houses/Fire Emblem Three Houses [Auxiliary Battle Maps] [010055D009F79003][v196608].nsp", 5)
        file("Fire Emblem_ Three Houses/Fire Emblem Three Houses [010055D009F78800][v327680].nsp", 6)
        val base = file("Fire Emblem Three Houses [010055D009F78000][v0].nsp", 11)

        assertEquals(base, resolver.locate(game(dlc), dlc))
    }

    @Test
    fun `an untagged base is matched by the name before the update's tags`() = runTest {
        val update = file("Super Mario Odyssey/Super Mario Odyssey[0100000000010800][US][v262144].nsp", 4)
        val base = file("Super Mario Odyssey.nsp", 56)
        file("Super Mario Party.nsp", 60)

        assertEquals(base, resolver.locate(game(update), update))
    }

    @Test
    fun `an update recorded at the platform root resolves to the base beside it`() = runTest {
        val update = file("Pokemon Violet [01008F6008C5E800][v720896].nsp", 52)
        val base = file("Pokémon Violet [01008F6008C5E000][v0].nsp", 67)

        assertEquals(base, resolver.locate(game(update), update))
    }

    @Test
    fun `a base in the game folder wins over the folder's larger update`() = runTest {
        val update = file("Mario Kart 8 Deluxe/Mario Kart 8 Deluxe [0100152000022800][v1376256].nsz", 35)
        val base = file("Mario Kart 8 Deluxe/Mario Kart 8 Deluxe [0100152000022000][v0].nsz", 30)

        assertEquals(base, resolver.locate(game(update), update))
    }

    @Test
    fun `an update with no base anywhere stays the recorded file`() = runTest {
        val update = file("Live A Live/LIVE A LIVE [US][0100CF801776C800][v65536]Update 1.0.1].nsp", 1)
        file("Other Game [0100AAAAAAAAA000][v0].nsp", 40)

        assertEquals(update, resolver.resolve(game(update), update))
        coVerify(exactly = 0) { gameDao.updateLocalPath(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `base title ids of updates and dlc`() {
        assertEquals(0x0100F2C0115B6000L, SwitchTitleId.baseOf(0x0100F2C0115B6800L))
        assertEquals(0x010055D009F78000L, SwitchTitleId.baseOf(0x010055D009F79003L))
        assertEquals(0x0100F2C0115B6000L, SwitchTitleId.baseOf(0x0100F2C0115B6000L))
    }

    private fun file(relative: String, size: Int) = File(switchRoot, relative).apply {
        parentFile?.mkdirs()
        writeBytes(ByteArray(size))
    }

    private fun game(localPath: File) = GameEntity(
        id = 7L,
        platformId = 12L,
        platformSlug = "switch",
        title = "Game",
        sortTitle = "game",
        localPath = localPath.absolutePath,
        fileOrigin = FileOrigin.ADOPTED,
        rommId = 29L,
        igdbId = null,
        source = GameSource.ROMM_SYNCED
    )
}
