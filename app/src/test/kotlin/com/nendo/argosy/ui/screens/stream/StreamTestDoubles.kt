package com.nendo.argosy.ui.screens.stream

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

fun noStreamLaunch(): StreamLaunchController.Factory {
    val controller = mockk<StreamLaunchController>(relaxed = true) {
        every { offerFor(any(), any(), any()) } returns null
        every { offers } returns emptyFlow()
        every { streamFirst } returns MutableStateFlow(StreamFirstUi())
    }
    return mockk { every { create(any()) } returns controller }
}
