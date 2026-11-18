package com.nendo.argosy.data.steam

import `in`.dragonbra.javasteam.steam.handlers.steamapps.License
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class SteamPackageRequestsTest {
    @Test fun `requests retain license tokens across batches and duplicate package ids`() {
        val licenses = (1..101).map { license(it, it.toLong() * 100) } +
            listOf(license(7, 0), license(0, 99), license(7, 777))
        val batches = packageRequests(licenses).chunked(100)
        assertEquals(listOf(100, 3), batches.map { it.size })
        assertEquals(100L, batches[0][0].accessToken)
        assertEquals(10100L, batches[1][0].accessToken)
        assertEquals(listOf(7, 7), batches[1].drop(1).map { it.id })
        assertEquals(listOf(0L, 777L), batches[1].drop(1).map { it.accessToken })
    }

    private fun license(id: Int, token: Long): License = mockk {
        every { packageID } returns id
        every { accessToken } returns token
    }
}
