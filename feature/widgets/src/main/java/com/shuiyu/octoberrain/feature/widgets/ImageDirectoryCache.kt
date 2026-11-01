package com.shuiyu.octoberrain.feature.widgets

import java.io.File
import java.util.Collections

/** One legacy external source only. Callbacks run synchronously outside the state lock. */
internal class ImageDirectoryCache(
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val probe: (Key) -> Long?,
    private val scan: (Key) -> List<File>,
) {
    data class Key(
        val pageIndex: Int,
        val canonicalParent: String,
        val legacyPath: String?,
        val legacySource: String?,
    )

    /** Opaque snapshot token: callers obtain it from request(), without copy semantics. */
    class Request internal constructor(
        val key: Key,
        val generation: Long,
        val eventVersion: Long,
    )

    data class Result(
        val request: Request,
        val files: List<File>,
        val reusable: Boolean,
    )

    private val lock = Any()
    private var key: Key? = null
    private var generation = 0L
    private var eventVersion = 0L
    private var dirty = true
    private var files: List<File>? = null
    private var mtime: Long? = null
    private var scannedAt = 0L

    fun request(source: Key): Request =
        synchronized(lock) {
            if (key != source) {
                generation++
                key = source
                files = null
                dirty = true
            }
            Request(source, generation, eventVersion)
        }

    fun isCurrent(request: Request): Boolean = synchronized(lock) { current(request) }

    fun invalidate() =
        synchronized(lock) {
            eventVersion++
            dirty = true
        }

    /** Bind observer callbacks to the captured request; late events from old sources are ignored. */
    fun onEvent(request: Request) =
        synchronized(lock) {
            if (request.generation == generation && request.key == key) {
                eventVersion++
                dirty = true
            }
        }

    fun clear() =
        synchronized(lock) {
            generation++
            eventVersion++
            key = null
            files = null
            dirty = true
        }

    /** Null means superseded. Scan failures propagate and leave the snapshot dirty. */
    fun load(
        request: Request,
        force: Boolean = false,
    ): Result? {
        if (!isCurrent(request)) return null
        val stamp =
            try {
                probe(request.key)
            } catch (_: Exception) {
                null
            }
        val now = clock()
        val cached =
            synchronized(lock) {
                if (current(request)) reusableFiles(force, stamp, now) else null
            }
        return if (cached != null) {
            Result(request, cached, true)
        } else {
            scanAndPublish(request, stamp, now)
        }
    }

    /** Called under lock; every cache miss invalidates the previous clean snapshot before IO. */
    private fun reusableFiles(
        force: Boolean,
        stamp: Long?,
        now: Long,
    ): List<File>? {
        val trusted = stamp != null && stamp == mtime
        val fresh = !dirty && now - scannedAt < MAX_AGE_MS
        val cached = files.takeIf { !force && trusted && fresh }
        if (cached == null) dirty = true
        return cached
    }

    private fun scanAndPublish(
        request: Request,
        stamp: Long?,
        now: Long,
    ): Result? {
        if (!isCurrent(request)) return null
        // The injected original resolver owns filtering, ordering and failure fallback.
        val candidates = Collections.unmodifiableList(ArrayList(scan(request.key)))
        return synchronized(lock) {
            if (current(request)) {
                files = candidates.takeIf { stamp != null }
                mtime = stamp
                scannedAt = now
                dirty = stamp == null
                Result(request, candidates, stamp != null)
            } else {
                null
            }
        }
    }

    private fun current(request: Request): Boolean =
        request.key == key && request.generation == generation && request.eventVersion == eventVersion

    private companion object {
        const val MAX_AGE_MS = 300_000L
    }
}
