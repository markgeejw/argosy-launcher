package com.nendo.argosy.data.stream

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.nendo.argosy.data.model.stream.StreamSettings
import com.nendo.argosy.data.preferences.StreamPreferencesRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StreamPreferencesRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun TestScope.store(file: File): Pair<DataStore<Preferences>, CoroutineScope> {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        return PreferenceDataStoreFactory.create(scope = scope) { file } to scope
    }

    @Test
    fun `defaults stream switch games from arceus through moonlight`() = runTest {
        val file = tempFolder.newFile("stream.preferences_pb")
        val (store, scope) = store(file)

        assertEquals(StreamSettings("arceus", "com.limelight", setOf("switch")), StreamPreferencesRepository(store).settings.first())

        scope.cancel()
    }

    @Test
    fun `the host is stored trimmed`() = runTest {
        val file = tempFolder.newFile("stream.preferences_pb")
        val (store, scope) = store(file)
        val repo = StreamPreferencesRepository(store)
        repo.setHost("  gamer.tail  ")

        assertEquals("gamer.tail", repo.settings.first().host)

        scope.cancel()
    }

    @Test
    fun `platforms are enabled and disabled one at a time`() = runTest {
        val file = tempFolder.newFile("stream.preferences_pb")
        val (store, scope) = store(file)
        val repo = StreamPreferencesRepository(store)
        repo.setPlatformEnabled("3ds", true)
        repo.setPlatformEnabled("switch", false)

        assertEquals(setOf("3ds"), repo.settings.first().platforms)

        scope.cancel()
    }

    @Test
    fun `the client package is stored`() = runTest {
        val file = tempFolder.newFile("stream.preferences_pb")
        val (store, scope) = store(file)
        val repo = StreamPreferencesRepository(store)
        repo.setMoonlightPackage("com.limelight.noir")

        assertEquals("com.limelight.noir", repo.settings.first().moonlightPackage)

        scope.cancel()
    }

    @Test
    fun `stored settings reload after the data store closes`() = runTest {
        val file = tempFolder.newFile("stream.preferences_pb")
        val (firstStore, firstScope) = store(file)
        StreamPreferencesRepository(firstStore).apply {
            setHost("gamer")
            setMoonlightPackage("com.limelight.noir")
            setPlatformEnabled("switch", false)
        }
        firstScope.cancel()

        val (reopenedStore, reopenedScope) = store(file)
        assertEquals(
            StreamSettings("gamer", "com.limelight.noir", emptySet()),
            StreamPreferencesRepository(reopenedStore).settings.first()
        )

        reopenedScope.cancel()
    }
}
