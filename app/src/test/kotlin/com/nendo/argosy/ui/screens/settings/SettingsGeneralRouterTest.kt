package com.nendo.argosy.ui.screens.settings

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.test.runTest
import com.nendo.argosy.data.preferences.DisplayRoleOverride
import com.nendo.argosy.data.preferences.DisplayRoleOverride.AUTO
import com.nendo.argosy.data.preferences.DisplayRoleOverride.STANDARD
import com.nendo.argosy.data.preferences.DisplayRoleOverride.SWAPPED
import com.nendo.argosy.util.SecondaryDisplayType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsGeneralRouterTest {
    @Test
    fun `display role carousel visits every option in both directions`() {
        val state = MutableStateFlow(SettingsUiState())
        val vm = mockk<SettingsViewModel>()
        every { vm._uiState } returns state
        every { vm.displayAffinityHelper.secondaryDisplayType } returns SecondaryDisplayType.EXTERNAL
        val selected = slot<DisplayRoleOverride>()
        mockkStatic(::routeSetDisplayRoleOverride)
        try {
            every { routeSetDisplayRoleOverride(vm, capture(selected)) } returns Unit
            val transitions = listOf(
                Triple(AUTO, 1, STANDARD),
                Triple(STANDARD, 1, SWAPPED),
                Triple(SWAPPED, 1, AUTO),
                Triple(AUTO, -1, SWAPPED),
                Triple(SWAPPED, -1, STANDARD),
                Triple(STANDARD, -1, AUTO)
            )
            for ((current, direction, expected) in transitions) {
                state.value = state.value.copy(display = state.value.display.copy(displayRoleOverride = current))
                selected.clear()
                routeCycleDisplayRoleOverride(vm, direction)
                assertEquals("$current, direction $direction", expected, selected.captured)
            }
        } finally {
            unmockkStatic(::routeSetDisplayRoleOverride)
        }
    }
    @Test
    fun `stream first action can be selected with moonlight and requires a transport`() = runTest {
        val state = MutableStateFlow(SettingsUiState().let {
            it.copy(streaming = it.streaming.copy(settings = it.streaming.settings.copy(host = "gamer")))
        })
        val vm = mockk<SettingsViewModel>(relaxed = true)
        val sync = mockk<com.nendo.argosy.ui.screens.settings.delegates.SyncSettingsDelegate>()
        val server = mockk<com.nendo.argosy.ui.screens.settings.delegates.ServerSettingsDelegate>()
        every { vm._uiState } returns state
        every { vm.syncDelegate } returns sync
        every { vm.serverDelegate } returns server
        every { server.updateState(any()) } answers { state.value = state.value.copy(server = firstArg()) }
        every { sync.setNotOnDeviceAction(any(), any(), any()) } answers {
            arg<(com.nendo.argosy.data.preferences.NotOnDeviceAction) -> Unit>(2)(secondArg())
        }
        val stream = com.nendo.argosy.data.preferences.NotOnDeviceAction.STREAM
        val download = com.nendo.argosy.data.preferences.NotOnDeviceAction.DOWNLOAD
        mockkStatic("androidx.lifecycle.ViewModelKt")
        try {
            every { vm.viewModelScope } returns this
            routeSetNotOnDeviceAction(vm, stream)
            assertEquals(stream, state.value.server.notOnDeviceAction)
            routeSetNotOnDeviceAction(vm, download)
            assertEquals(download, state.value.server.notOnDeviceAction)
            state.value = state.value.copy(streaming = state.value.streaming.copy(
                settings = state.value.streaming.settings.copy(host = " ")
            ))
            routeSetNotOnDeviceAction(vm, stream)
            assertEquals(download, state.value.server.notOnDeviceAction)
            io.mockk.verify(exactly = 2) { sync.setNotOnDeviceAction(any(), any(), any()) }
        } finally {
            unmockkStatic("androidx.lifecycle.ViewModelKt")
        }
    }
}
