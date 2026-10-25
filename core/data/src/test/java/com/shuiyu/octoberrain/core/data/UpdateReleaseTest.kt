package com.shuiyu.octoberrain.core.data

import com.shuiyu.octoberrain.core.common.AppJson
import com.shuiyu.octoberrain.core.common.ChangelogEntry
import com.shuiyu.octoberrain.core.common.IntegrationsConfig
import com.shuiyu.octoberrain.core.common.UpdateInfo
import com.shuiyu.octoberrain.core.common.UpdateSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class UpdateReleaseTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val primary = "https://github.com/example/update.json"
    private val mirror = "https://mirror.example/update.json"
    private val manifest =
        """
        {
          "versionName":"1.0.0", "versionCode":52,
          "downloadUrl":"https://example/app.apk",
          "sha256":"${"a".repeat(64)}", "sizeBytes":100
        }
        """.trimIndent()

    @Test
    fun releaseChangelogCanBeDecoded() {
        val entries =
            AppJson.format.decodeFromString<List<ChangelogEntry>>(
                """[{"version":"1.0.0","date":"2026-10-26","items":["正式版本"]}]""",
            )
        assertEquals("1.0.0", entries.single().version)
    }

    @Test
    fun emptyConfigurationUsesGithubAndPreservesCustomSourcePriority() {
        assertEquals(listOf(UpdateSources.GITHUB_MANIFEST_URL), UpdateSources.manifests(""))
        assertEquals(
            listOf(primary, mirror, UpdateSources.GITHUB_MANIFEST_URL),
            UpdateSources.manifests(primary, listOf(mirror, primary)),
        )
        val old = AppJson.format.decodeFromString<IntegrationsConfig>("""{"updateUrl":"$primary"}""")
        assertEquals(primary, old.updateUrl)
        assertEquals(emptyList<String>(), old.updateFallbackUrls)
    }

    @Test
    fun currentAndOlderVersionsAreNeverOffered() {
        assertNull(readUpdate(listOf(primary), 52) { manifest })
        assertNull(readUpdate(listOf(primary), 53) { manifest })
        assertEquals(52, readUpdate(listOf(primary), 51) { manifest }?.versionCode)
    }

    @Test
    fun networkFailureFallsBackToMirror() {
        val tried = mutableListOf<String>()
        val result =
            readUpdate(listOf(primary, mirror), 51) {
                tried += it
                if (it == primary) throw IOException("offline")
                manifest
            }
        assertEquals(listOf(primary, mirror), tried)
        assertEquals(52, result?.versionCode)
    }

    @Test
    fun invalidManifestFallsBackWithoutOfferingUnverifiedDownload() {
        assertEquals(52, readUpdate(listOf(primary, mirror), 51) { if (it == primary) "{}" else manifest }?.versionCode)
    }

    @Test(expected = IOException::class)
    fun totalFailureIsNotReportedAsLatest() {
        readUpdate(listOf(primary, mirror), 51) { throw IOException("offline") }
    }

    @Test
    fun corruptPrimaryApkFallsBackAndRemovesPartialFile() {
        val directory = temporary.newFolder()
        val expected = temporary.newFile().apply { writeText("signed-apk-fixture") }
        val info = UpdateInfo("1.0.0", 52, "", primary, fileSha256(expected), expected.length(), listOf(mirror))
        val result =
            downloadUpdate(info, directory) { url, file ->
                file.writeText(if (url == primary) "corrupt" else expected.readText())
            }
        assertEquals(expected.readText(), result?.readText())
        assertFalse(directory.resolve("update_52.part").exists())
    }

    @Test
    fun mismatchedApkIsNotReturnedToInstaller() {
        val directory = temporary.newFolder()
        val info = UpdateInfo("1.0.0", 52, "", primary, "a".repeat(64), 100)
        assertNull(downloadUpdate(info, directory) { _, file -> file.writeText("bad") })
        assertFalse(directory.resolve("update_52.part").exists())
        assertFalse(directory.resolve("update_52.apk").exists())
    }

    @Test(expected = IOException::class)
    fun insecureSourceIsRejected() {
        readUpdate(listOf("http://example/update.json"), 51) { manifest }
    }
}
