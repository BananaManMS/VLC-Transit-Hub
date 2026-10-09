package com.example

import com.example.util.AppUpdateManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun testVersionComparison_newerPatch() {
        assertTrue(AppUpdateManager.isVersionNewer("2.0.1", "2.0.0"))
    }

    @Test
    fun testVersionComparison_newerMinor() {
        assertTrue(AppUpdateManager.isVersionNewer("2.1.0", "2.0.9"))
    }

    @Test
    fun testVersionComparison_newerMajor() {
        assertTrue(AppUpdateManager.isVersionNewer("3.0.0", "2.9.9"))
    }

    @Test
    fun testVersionComparison_sameVersion() {
        assertFalse(AppUpdateManager.isVersionNewer("2.0.0", "2.0.0"))
    }

    @Test
    fun testVersionComparison_olderVersion() {
        assertFalse(AppUpdateManager.isVersionNewer("1.9.5", "2.0.0"))
    }

    @Test
    fun testVersionComparison_withPrefixesAndMetadata() {
        assertTrue(AppUpdateManager.isVersionNewer("v2.0.1-beta", "2.0.0"))
    }
}
