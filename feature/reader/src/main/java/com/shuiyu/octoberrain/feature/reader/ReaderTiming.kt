package com.shuiyu.octoberrain.feature.reader

import android.os.SystemClock
import android.util.Log
import com.shuiyu.octoberrain.core.common.ReaderFormat
import com.shuiyu.octoberrain.feature.reader.source.SourceCache

/** Opt-in action-to-draw timings; never include document identifiers or body text. */
internal object ReaderTiming {
    private const val TAG = "ReaderPerf"
    private var sequence = 0L

    data class Sample(
        val id: Long,
        val started: Long,
    )

    fun start(): Sample? =
        if (Log.isLoggable(TAG, Log.DEBUG)) Sample(++sequence, SystemClock.elapsedRealtimeNanos()) else null

    fun event(
        pageTurn: Boolean,
        previous: ReaderFrame?,
        current: ReaderFrame?,
    ): String =
        when {
            !pageTurn -> "action"
            previous?.locator != current?.locator -> "turn"
            else -> "boundary"
        }

    fun cacheClosed(cache: SourceCache) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return
        val state = cache.snapshot()
        Log.d(
            TAG,
            "event=sourceClosed files=${state.files} diskBytes=${state.diskBytes} reservedBytes=${state.reservedBytes}",
        )
    }

    fun pdfCache(
        bytes: Int,
        entries: Int,
    ) {
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "event=pdfCache bytes=$bytes entries=$entries")
    }

    fun drawn(
        page: ReaderPageView,
        started: Sample?,
        event: String,
        format: ReaderFormat,
        valid: () -> Boolean,
    ) {
        if (started == null) return
        page.contentDrawn = {
            if (valid()) {
                val elapsedUs = (SystemClock.elapsedRealtimeNanos() - started.started) / 1_000
                Log.d(TAG, "sample=${started.id} event=$event format=$format elapsedUs=$elapsedUs endpoint=draw")
            }
        }
        page.invalidate()
    }
}
