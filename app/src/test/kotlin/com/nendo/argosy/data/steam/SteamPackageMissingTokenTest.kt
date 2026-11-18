package com.nendo.argosy.data.steam

import com.nendo.argosy.data.local.dao.SteamLicenseDao
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSProductInfo
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.PICSProductInfoCallback
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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

    private fun field(manager: SteamLibraryManager, name: String) =
        manager.javaClass.getDeclaredField(name).apply { isAccessible = true }

    private suspend fun invoke(manager: SteamLibraryManager, name: String, vararg args: Any): Unit =
        suspendCoroutineUninterceptedOrReturn { continuation ->
            val types = args.filterIsInstance<Class<*>>().toTypedArray() + Continuation::class.java
            val method = manager.javaClass.getDeclaredMethod(name, *types).apply { isAccessible = true }
            method.invoke(manager, *args.filterNot { it is Class<*> }.toTypedArray(), continuation)
        }
}
