package com.nendo.argosy.data.streaming

import com.nendo.argosy.data.preferences.NotOnDeviceAction
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamFirstRuleTest {

    @Test
    fun `moonlight without a container leads for a game not on device`() {
        assertEquals(true, StreamFirstRule.leadsWithStream(NotOnDeviceAction.STREAM, false, false, hasMoonlight = true))
        assertEquals(false, StreamFirstRule.leadsWithStream(NotOnDeviceAction.STREAM, true, false, hasMoonlight = true))
        assertEquals(false, StreamFirstRule.leadsWithStream(NotOnDeviceAction.DOWNLOAD, false, false, hasMoonlight = true))
    }

    @Test
    fun `only stream mode with a container for a game not on the device leads with stream`() {
        val expected = mapOf(
            Triple(NotOnDeviceAction.STREAM, false, true) to true,
            Triple(NotOnDeviceAction.STREAM, false, false) to false,
            Triple(NotOnDeviceAction.STREAM, true, true) to false,
            Triple(NotOnDeviceAction.STREAM, true, false) to false,
            Triple(NotOnDeviceAction.DOWNLOAD, false, true) to false,
            Triple(NotOnDeviceAction.DOWNLOAD, false, false) to false,
            Triple(NotOnDeviceAction.DOWNLOAD, true, true) to false,
            Triple(NotOnDeviceAction.DOWNLOAD, true, false) to false
        )

        expected.forEach { (case, leads) ->
            val (action, isOnDevice, hasContainer) = case
            assertEquals(
                "action=$action onDevice=$isOnDevice container=$hasContainer",
                leads,
                StreamFirstRule.leadsWithStream(action, isOnDevice, hasContainer)
            )
        }
    }
}
