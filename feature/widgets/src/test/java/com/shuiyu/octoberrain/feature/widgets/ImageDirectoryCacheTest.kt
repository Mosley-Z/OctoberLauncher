package com.shuiyu.octoberrain.feature.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class ImageDirectoryCacheTest {
    private val key = ImageDirectoryCache.Key(0, "/images", "/images/a.png", "source")

    @Test
    fun repeatedLoadsAndMinuteSteps() {
        var now = 0L
        var scans = 0
        val cache =
            ImageDirectoryCache({ now }, { 1L }) {
                scans++
                listOf(File("/images/a.png"))
            }
        repeat(20) { cache.load(cache.request(key)) }
        assertEquals(1, scans)
        // Loads at 0, 60, ... 540 seconds: exactly two scans including the initial one.
        repeat(10) {
            now = it * 60_000L
            cache.load(cache.request(key))
        }
        assertEquals(2, scans)
    }

    @Test
    fun expiryBoundaryAndTriggers() {
        var now = 0L
        var stamp = 1L
        var scans = 0
        val cache =
            ImageDirectoryCache({ now }, { stamp }) {
                scans++
                emptyList()
            }
        cache.load(cache.request(key))
        now = 299_999
        cache.load(cache.request(key))
        assertEquals(1, scans)
        now = 300_000
        cache.load(cache.request(key))
        assertEquals(2, scans)
        stamp++
        cache.load(cache.request(key))
        cache.invalidate()
        cache.load(cache.request(key))
        cache.load(cache.request(key), force = true)
        assertEquals(5, scans)
    }

    @Test
    fun eventDuringScanRejectsResultAndRequiresAnotherScan() {
        var scans = 0
        lateinit var cache: ImageDirectoryCache
        cache =
            ImageDirectoryCache({ 0L }, { 1L }) {
                scans++
                if (scans == 1) cache.invalidate()
                emptyList()
            }
        assertNull(cache.load(cache.request(key)))
        cache.load(cache.request(key))
        cache.load(cache.request(key))
        assertEquals(2, scans)
    }

    @Test
    fun sourceSwitchAndClearDuringScanRejectOldResults() {
        lateinit var cache: ImageDirectoryCache
        val other = key.copy(pageIndex = 1)
        cache =
            ImageDirectoryCache({ 0L }, { 1L }) {
                cache.request(other)
                listOf(File("/old"))
            }
        val old = cache.request(key)
        assertNull(cache.load(old))
        assertFalse(cache.isCurrent(old))
        cache =
            ImageDirectoryCache({ 0L }, { 1L }) {
                cache.clear()
                emptyList()
            }
        assertNull(cache.load(cache.request(key)))
    }

    @Test
    fun untrustedProbeAlwaysUsesOriginalScanAndNeverOldCandidates() {
        var trusted = true
        var scans = 0
        val cache =
            ImageDirectoryCache({ 0L }, { if (trusted) 1L else null }) {
                scans++
                listOf(File("/candidate-$scans"))
            }
        cache.load(cache.request(key))
        trusted = false
        assertEquals(listOf(File("/candidate-2")), cache.load(cache.request(key))?.files)
        assertFalse(cache.load(cache.request(key))!!.reusable)
        assertEquals(3, scans)
    }

    @Test
    fun forcedScanFailureInvalidatesSuccessfulSnapshot() {
        var scans = 0
        val cache =
            ImageDirectoryCache({ 0L }, { 1L }) {
                scans++
                check(scans != 2) { "forced scan failure" }
                listOf(File("/candidate-$scans"))
            }
        assertEquals(listOf(File("/candidate-1")), cache.load(cache.request(key))?.files)
        try {
            cache.load(cache.request(key), force = true)
            fail("expected scan failure")
        } catch (_: IllegalStateException) {
            // The failure belongs to the caller; the old snapshot must stay dirty.
        }
        assertEquals(listOf(File("/candidate-3")), cache.load(cache.request(key))?.files)
        assertEquals(3, scans)
    }

    @Test
    fun scanFailurePropagatesAndRetryDoesNotUseFailedSnapshot() {
        var scans = 0
        val cache =
            ImageDirectoryCache({ 0L }, { 1L }) {
                scans++
                check(scans != 1) { "scan" }
                emptyList()
            }
        try {
            cache.load(cache.request(key))
            fail("expected scan failure")
        } catch (_: IllegalStateException) {
            // The original resolver/caller retains responsibility for its failure policy.
        }
        cache.load(cache.request(key))
        assertEquals(2, scans)
    }
}
