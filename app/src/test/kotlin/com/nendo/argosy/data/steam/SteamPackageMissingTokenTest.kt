package com.nendo.argosy.data.steam

import com.nendo.argosy.R
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SteamLicenseDao
import `in`.dragonbra.javasteam.types.KeyValue
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSProductInfo
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.PICSProductInfoCallback
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

class SteamPackageMissingTokenTest {
    @Test fun `missing token does not store mapping or report a clean sync`() = runBlocking {
        val licenses = mockk<SteamLicenseDao>(relaxed = true)
        val manager = SteamLibraryManager(
            context = mockk(relaxed = true), gameDao = mockk(relaxed = true),
            platformDao = mockk(relaxed = true), steamAccountDao = mockk(relaxed = true),
            steamLicenseDao = licenses, cachedLicenseDao = mockk(relaxed = true),
            imageCacheManager = mockk(relaxed = true), steamRepository = mockk(relaxed = true),
            steamIgdbResolver = mockk(relaxed = true), preferencesRepository = mockk(relaxed = true),
            syncPreferencesRepository = mockk(relaxed = true), steamLibraryRepair = mockk(relaxed = true),
            gameArtDao = mockk(relaxed = true), inventoryRefresh = mockk(relaxed = true)
        )
        val info = mockk<PICSProductInfo> { every { isMissingToken } returns true }
        val callback = mockk<PICSProductInfoCallback> { every { packages } returns mapOf(42 to info) }
        field(manager, "currentAccountId").set(manager, 1L)
        field(manager, "lastFullSyncAt").set(manager, java.time.Instant.now())
        @Suppress("UNCHECKED_CAST")
        (field(manager, "pendingPackageIds").get(manager) as MutableSet<Int>).add(42)
        invoke(manager, "handlePackageInfo", PICSProductInfoCallback::class.java, callback)
        invoke(manager, "finishPackagePhase")
        coVerify(exactly = 0) { licenses.insertOrUpdate(any()) }
        assertTrue(manager.syncState.value is LibrarySyncState.Error)
        assertEquals(1, field(manager, "picsFailures").getInt(manager))
        assertNull(field(manager, "lastFullSyncAt").get(manager))
    }

    @Test fun `missing package token remains an error after successful app processing`() = runBlocking {
        val licenses = mockk<SteamLicenseDao>(relaxed = true)
        val games = mockk<GameDao>(relaxed = true)
        coEvery { games.getBySteamAppId(100L) } returns null
        val manager = SteamLibraryManager(
            context = mockk(relaxed = true), gameDao = games,
            platformDao = mockk(relaxed = true), steamAccountDao = mockk(relaxed = true),
            steamLicenseDao = licenses, cachedLicenseDao = mockk(relaxed = true),
            imageCacheManager = mockk(relaxed = true), steamRepository = mockk(relaxed = true),
            steamIgdbResolver = mockk(relaxed = true), preferencesRepository = mockk(relaxed = true),
            syncPreferencesRepository = mockk(relaxed = true), steamLibraryRepair = mockk(relaxed = true),
            gameArtDao = mockk(relaxed = true), inventoryRefresh = mockk(relaxed = true)
        )
        val packageKv = KeyValue("package").apply {
            children.add(KeyValue("appids").apply { children.add(KeyValue("0", "100")) })
        }
        val appKv = KeyValue("app").apply {
            children.add(KeyValue("common").apply {
                children.add(KeyValue("type", "game"))
                children.add(KeyValue("name", "Successful Game"))
            })
        }
        val missing = mockk<PICSProductInfo> { every { isMissingToken } returns true }
        val accessible = mockk<PICSProductInfo> {
            every { isMissingToken } returns false
            every { keyValues } returns packageKv
        }
        val app = mockk<PICSProductInfo> { every { keyValues } returns appKv }
        val packages = mockk<PICSProductInfoCallback> { every { this@mockk.packages } returns mapOf(42 to missing, 43 to accessible) }
        val apps = mockk<PICSProductInfoCallback> { every { this@mockk.apps } returns mapOf(100 to app) }
        field(manager, "currentAccountId").set(manager, 1L)
        field(manager, "lastFullSyncAt").set(manager, java.time.Instant.now())
        field(manager, "enrichmentJob").set(manager, mockk<Job> { every { isActive } returns true })
        @Suppress("UNCHECKED_CAST")
        (field(manager, "pendingPackageIds").get(manager) as MutableSet<Int>).addAll(listOf(42, 43))
        invoke(manager, "handlePackageInfo", PICSProductInfoCallback::class.java, packages)
        invoke(manager, "storeLicenses")
        invoke(manager, "handleAppInfo", PICSProductInfoCallback::class.java, apps)
        invoke(manager, "finishAppPhase")
        coVerify(exactly = 1) { licenses.insertOrUpdate(match { it.packageId == 43 && it.appIds == "100" }) }
        coVerify(exactly = 0) { licenses.insertOrUpdate(match { it.packageId == 42 }) }
        coVerify(exactly = 1) { games.insert(match { it.steamAppId == 100L && it.title == "Successful Game" }) }
        val state = manager.syncState.value as LibrarySyncState.Error
        assertEquals(R.string.notif_steam_sync_error_missing_package_token, state.messageRes)
        assertNull(field(manager, "lastFullSyncAt").get(manager))
    }

    private fun field(manager: SteamLibraryManager, name: String) =
        manager.javaClass.getDeclaredField(name).apply { isAccessible = true }

    private suspend fun invoke(manager: SteamLibraryManager, name: String, vararg args: Any): Unit =
        suspendCoroutineUninterceptedOrReturn { continuation ->
            val types = args.filterIsInstance<Class<*>>().toTypedArray() + Continuation::class.java
            val method = manager.javaClass.getDeclaredMethod(name, *types).apply { isAccessible = true }
            method.invoke(manager, *args.filterNot { it is Class<*> }.toTypedArray(), continuation)
        }
}
