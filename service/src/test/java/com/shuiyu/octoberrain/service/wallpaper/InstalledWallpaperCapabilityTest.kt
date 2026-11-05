package com.shuiyu.octoberrain.service.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InstalledWallpaperCapabilityTest {
    @Test
    fun `failed persistence does not fail startup or discard the probed capability`() {
        val directory =
            kotlin.io.path
                .createTempDirectory("wallpaper-write-failure")
                .toFile()
        val blockedParent = File(directory, "regular-file").apply { writeText("blocked") }
        try {
            assertTrue(readInstalledCapability(File(blockedParent, "capability")) { true })
        } finally {
            blockedParent.delete()
            directory.delete()
        }
    }

    @Test
    fun `an interrupted record can be repaired on a later launch`() {
        val directory =
            kotlin.io.path
                .createTempDirectory("wallpaper-partial-record")
                .toFile()
        val marker = File(directory, "capability").apply { writeText("") }
        try {
            assertTrue(readInstalledCapability(marker) { true })
            assertEquals("1", marker.readText())
            assertTrue(readInstalledCapability(marker) { error("must not reprobe a valid record") })
        } finally {
            marker.delete()
            directory.delete()
        }
    }

    @Test
    fun `positive and negative probes persist across restarts until uninstall`() {
        for (supported in listOf(false, true)) {
            val directory =
                kotlin.io.path
                    .createTempDirectory("wallpaper-install")
                    .toFile()
            val marker = File(directory, "capability")
            var calls = 0
            try {
                assertEquals(
                    supported,
                    readInstalledCapability(marker) {
                        calls++
                        supported
                    },
                )
                assertEquals(
                    supported,
                    readInstalledCapability(File(directory, "capability")) {
                        calls++
                        !supported
                    },
                )
                assertEquals(1, calls)
                assertTrue(marker.delete())
                assertFalse(
                    readInstalledCapability(marker) {
                        calls++
                        false
                    },
                )
                assertEquals(2, calls)
            } finally {
                marker.delete()
                directory.delete()
            }
        }
    }
}
