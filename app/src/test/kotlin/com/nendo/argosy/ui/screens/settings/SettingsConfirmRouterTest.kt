package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.data.model.stream.StreamSettings
import com.nendo.argosy.ui.screens.settings.delegates.StreamingState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsConfirmRouterTest {
    @Test
    fun `confirming the streaming client opens its picker without cycling`() {
        val state = MutableStateFlow(
            SettingsUiState(
                currentSection = SettingsSection.STREAMING,
                focusedIndex = 1,
                streaming = StreamingState(settings = StreamSettings())
            )
        )
        val vm = mockk<SettingsViewModel>(relaxed = true)
        every { vm._uiState } returns state

        routeConfirm(vm)

        verify(exactly = 1) { vm.requestEnumPicker("streamClient") }
        verify(exactly = 0) { vm.cycleStreamClient(any()) }
        assertEquals(StreamSettings.DEFAULT_MOONLIGHT_PACKAGE, state.value.streaming.settings.moonlightPackage)
    }
}
