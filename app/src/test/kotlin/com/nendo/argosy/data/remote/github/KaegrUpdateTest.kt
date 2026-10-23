package com.nendo.argosy.data.remote.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaegrUpdateTest {
    @Test
    fun `fork release sequence is ordered numerically`() {
        assertTrue(VersionInfo.parse("v2.24.0-kaegr.2")!! > VersionInfo.parse("2.24.0-kaegr.1")!!)
        assertTrue(VersionInfo.parse("v2.24.0-kaegr.10")!! > VersionInfo.parse("2.24.0-kaegr.2")!!)
    }

    @Test
    fun `universal release selects the plain apk`() {
        val arm64 = GitHubAsset("argosy-v2.24.0-kaegr.1-arm64.apk", "https://example.invalid/arm64", 1)
        val universal = GitHubAsset("argosy-v2.24.0-kaegr.1.apk", "https://example.invalid/universal", 1)

        assertEquals(universal, UpdateRepository.selectApkAsset(listOf(arm64, universal), 3_035_801))
        assertEquals(arm64, UpdateRepository.selectApkAsset(listOf(universal, arm64), 2_035_801))
    }
}
