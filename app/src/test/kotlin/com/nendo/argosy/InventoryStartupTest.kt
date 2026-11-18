package com.nendo.argosy

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nendo.argosy.data.preferences.StreamPreferencesRepository
import com.nendo.argosy.data.stream.InventoryReadTrackingStore
import com.nendo.argosy.data.stream.StreamInventoryRepository
import com.nendo.argosy.data.stream.SunshineInventoryRefresh
import com.nendo.argosy.data.update.UpdateCheckWorker
import com.nendo.argosy.libretro.slot.GameSlots
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class InventoryStartupTest {
    @get:Rule val temp = TemporaryFolder()
    @After fun cleanup() = unmockkAll()

    @Test fun `startup triggers ttl refresh without waiting for fetch`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val store = InventoryReadTrackingStore(PreferenceDataStoreFactory.create(scope = scope) { temp.newFile("startup.preferences_pb") })
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
        mockkObject(GameSlots, UpdateCheckWorker.Companion)
        every { GameSlots.isSlotProcess } returns false
        val reached = AtomicInteger()
        every { UpdateCheckWorker.schedule(any()) } answers { reached.incrementAndGet(); throw StopStartup() }
        fun start() {
            val app = ArgosyApp()
            app.javaClass.superclass!!.getDeclaredField("injected").apply { isAccessible = true }.setBoolean(app, true)
            app.sunshineInventoryRefresh = refresh
            app.userCertStore = mockk(relaxed = true)
            app.clientCertStore = mockk(relaxed = true)
            app.socialSyncCoordinator = mockk(relaxed = true)
            app.variantFileCleanup = mockk(relaxed = true)
            app.homeAppAvailability = mockk(relaxed = true)
            app.privilegedAccess = mockk(relaxed = true)
            app.performanceModeStore = mockk(relaxed = true)
            try { app.onCreate() } catch (_: StopStartup) { }
        }
        start()
        withTimeout(5000) { while (reached.get() < 1) kotlinx.coroutines.delay(10) }
        withTimeout(5000) { store.reads.receive(); store.reads.receive(); refresh.refresh(day - 1) }
        assertEquals(0, calls.get())
        now.set(day)
        start()
        withTimeout(5000) { entered.await() }
        assertEquals(2, reached.get())
        assertEquals(1, calls.get())
        release.complete(Unit)
        withTimeout(5000) { while (repo.snapshot("g", day)?.fetchedAt != day) kotlinx.coroutines.delay(10) }
        assertEquals("new", repo.snapshot("g", day)?.entries?.single()?.name)
        scope.cancel()
    }
    private class StopStartup : RuntimeException()
}
