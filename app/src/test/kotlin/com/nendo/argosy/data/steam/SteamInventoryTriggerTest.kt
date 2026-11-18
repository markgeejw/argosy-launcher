package com.nendo.argosy.data.steam

import android.util.Log
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nendo.argosy.data.preferences.StreamPreferencesRepository
import com.nendo.argosy.data.stream.InventoryReadTrackingStore
import com.nendo.argosy.data.stream.StreamInventoryRepository
import com.nendo.argosy.data.stream.SunshineInventoryRefresh
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.LicenseListCallback
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

class SteamInventoryTriggerTest {
    @get:Rule val temp = TemporaryFolder()
    @After fun cleanup() = unmockkAll()

    @Test fun `normal forced and overwrite sync trigger ttl refresh without waiting`() = runBlocking {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        for (entrypoint in listOf("normal", "forced", "overwrite")) {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val store = InventoryReadTrackingStore(PreferenceDataStoreFactory.create(scope = scope) { temp.newFile("$entrypoint.preferences_pb") })
            val prefs = StreamPreferencesRepository(store)
            val repo = StreamInventoryRepository(store)
            prefs.setHost("g")
            repo.store("g", """[{"name":"old","appId":"1"}]""", 0)
            val day = 24 * 60 * 60 * 1000L
            val now = AtomicLong(day - 1)
            val calls = AtomicInteger()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val refresh = SunshineInventoryRefresh(repo, prefs, { now.get() }) {
                calls.incrementAndGet(); entered.complete(Unit); release.await()
                """[{"name":"new","appId":"2"}]"""
            }
            val manager = SteamLibraryManager(
                context = mockk(relaxed = true), gameDao = mockk(relaxed = true),
                platformDao = mockk(relaxed = true), steamAccountDao = mockk(relaxed = true),
                steamLicenseDao = mockk(relaxed = true), cachedLicenseDao = mockk(relaxed = true),
                imageCacheManager = mockk(relaxed = true), steamRepository = mockk(relaxed = true),
                steamIgdbResolver = mockk(relaxed = true), preferencesRepository = mockk(relaxed = true),
                syncPreferencesRepository = mockk(relaxed = true), steamLibraryRepair = mockk(relaxed = true),
                gameArtDao = mockk(relaxed = true), inventoryRefresh = refresh
            )
            val callback = mockk<LicenseListCallback>()
            every { callback.licenseList } returns emptyList()
            suspend fun trigger() {
                when (entrypoint) {
                    "normal" -> invokeLicenseList(manager, callback)
                    "forced" -> manager.forceSync()
                    else -> manager.forceSyncWithOverwrite()
                }
            }
            trigger()
            withTimeout(5000) { store.reads.receive(); store.reads.receive(); refresh.refresh(day - 1) }
            assertEquals(entrypoint, 0, calls.get())
            now.set(day)
            val previousRuns = manager.settledSyncRuns.value
            trigger()
            withTimeout(5000) { entered.await() }
            withTimeout(5000) { while (manager.settledSyncRuns.value <= previousRuns) delay(10) }
            assertEquals(entrypoint, 1, calls.get())
            release.complete(Unit)
            withTimeout(5000) { while (repo.snapshot("g", day)?.fetchedAt != day) delay(10) }
            assertEquals(entrypoint, "new", repo.snapshot("g", day)?.entries?.single()?.name)
            scope.cancel()
        }
    }

    private suspend fun invokeLicenseList(manager: SteamLibraryManager, callback: LicenseListCallback): Unit =
        suspendCoroutineUninterceptedOrReturn { continuation ->
            val method = SteamLibraryManager::class.java.getDeclaredMethod(
                "handleLicenseList", LicenseListCallback::class.java, Continuation::class.java
            )
            method.isAccessible = true
            method.invoke(manager, callback, continuation)
        }
}
