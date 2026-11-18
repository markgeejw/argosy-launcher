package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsSection
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingInputTest {
    @Test
    fun `left and right set pc state and repeated directions do not toggle`() {
        val state = MutableStateFlow(SettingsUiState(currentSection = SettingsSection.STREAMING, focusedIndex = 3))
        val viewModel = mockk<SettingsViewModel>(relaxed = true)
        every { viewModel.uiState } returns state
        every { viewModel.toggleStreamPlatform("pc") } answers {
            val streaming = state.value.streaming
            val settings = streaming.settings
            val platforms = settings.platforms
            state.value = state.value.copy(streaming = streaming.copy(settings = settings.copy(
                platforms = if ("pc" in platforms) platforms - "pc" else platforms + "pc"
            )))
        }
        val input = LightSectionsInput(viewModel)

        assertEquals(InputResult.HANDLED, input.onLeft())
        assertEquals(InputResult.HANDLED, input.onLeft())
        assertEquals(setOf("switch"), state.value.streaming.settings.platforms)
        assertEquals(InputResult.HANDLED, input.onRight())
        assertEquals(InputResult.HANDLED, input.onRight())
        assertEquals(setOf("switch", "pc"), state.value.streaming.settings.platforms)
        verify(exactly = 2) { viewModel.toggleStreamPlatform("pc") }
    }

    @Test
    fun `client directions continue cycling`() {
        val viewModel = mockk<SettingsViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(SettingsUiState(currentSection = SettingsSection.STREAMING, focusedIndex = 1))
        val input = LightSectionsInput(viewModel)

        assertEquals(InputResult.HANDLED, input.onLeft())
        assertEquals(InputResult.HANDLED, input.onRight())
        verify { viewModel.cycleStreamClient(-1); viewModel.cycleStreamClient(1) }
    }
}
