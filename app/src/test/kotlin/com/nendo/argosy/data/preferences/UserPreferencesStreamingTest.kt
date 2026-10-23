package com.nendo.argosy.data.preferences

import com.nendo.argosy.data.model.stream.StreamSettings
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UserPreferencesStreamingTest {

    @Test
    fun `streaming values reach the shared user preference aggregate`() = runTest {
        val stream = MutableStateFlow(StreamSettings())
        val repository = UserPreferencesRepository(
            displayPrefs = mockk<DisplayPreferencesRepository> { every { preferences } returns flowOf(DisplayPreferences()) },
            syncPrefs = mockk<SyncPreferencesRepository> { every { preferences } returns flowOf(SyncPreferences()) },
            controlsPrefs = mockk<ControlsPreferencesRepository> { every { preferences } returns flowOf(ControlsPreferences()) },
            storagePrefs = mockk<StoragePreferencesRepository> { every { preferences } returns flowOf(StoragePreferences()) },
            appPrefs = mockk<AppPreferencesRepository> { every { preferences } returns flowOf(AppPreferences()) },
            builtinPrefs = mockk<BuiltinEmulatorPreferencesRepository> { every { isBuiltinLibretroEnabled() } returns flowOf(false) },
            sessionPrefs = mockk(relaxed = true),
            jellyfinPrefs = mockk<JellyfinPreferencesRepository> { every { preferences } returns flowOf(JellyfinPreferences()) },
            streamPrefs = mockk<StreamPreferencesRepository> { every { settings } returns stream }
        )

        assertEquals(StreamSettings(), repository.userPreferences.first().streamSettings)
        val updated = StreamSettings("paired-name", "com.limelight.noir", emptySet())
        stream.value = updated
        assertEquals(updated, repository.userPreferences.first().streamSettings)
    }
}
