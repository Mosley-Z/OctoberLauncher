package com.shuiyu.octoberrain.feature.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.os.SharedMemory
import android.util.LruCache
import androidx.annotation.RequiresApi
import com.shuiyu.octoberrain.core.common.ReaderCapabilities
import com.shuiyu.octoberrain.core.common.ReaderLocator
import com.shuiyu.octoberrain.feature.reader.ReaderEngine
import com.shuiyu.octoberrain.feature.reader.ReaderFrame
import com.shuiyu.octoberrain.feature.reader.source.DocumentHandle
import kotlin.math.abs

/** 仅宿主当前/邻页位图最多12MiB，不recycle仍可能被主线程绘制的旧帧。 */
@RequiresApi(27)
internal class PdfEngine private constructor(
    private val client: PdfClient,
    private val width: Int,
    private val height: Int,
    private val pages: Int,
) : ReaderEngine {
    private var location = ReaderLocator.Pdf()
    private val cache =
        object : LruCache<ReaderLocator.Pdf, Bitmap>(PdfViewport.MAX_BYTES) {
            override fun sizeOf(
                key: ReaderLocator.Pdf,
                value: Bitmap,
            ): Int = value.allocationByteCount
        }
    override val capabilities = ReaderCapabilities(pageJump = true, zoom = true, bookmark = true)

    override suspend fun page(locator: ReaderLocator?): ReaderFrame {
        val target = PdfViewport.normalize(locator as? ReaderLocator.Pdf ?: ReaderLocator.Pdf(), pages)
        val bitmap = cache.get(target) ?: render(target).also { cache.put(target, it) }
        location = target
        cache
            .snapshot()
            .keys
            .filter { abs(it.page - target.page) > 1 }
            .forEach { cache.remove(it) }
        return ReaderFrame(
            target,
            "${target.page + 1}/$pages · ${(target.zoom * 100).toInt()}%" +
                if (target.crop) " · 裁边5%" else "",
            bitmap = bitmap,
        )
    }

    override suspend fun turn(delta: Int): ReaderFrame =
        page(location.copy(page = location.page + delta, centerX = 0.5f, centerY = 0.5f))

    suspend fun adjust(mode: PdfAdjustment): ReaderFrame {
        val step = 0.15f / location.zoom
        val next =
            when (mode) {
                PdfAdjustment.ZOOM_IN -> location.copy(zoom = location.zoom * 1.5f)
                PdfAdjustment.ZOOM_OUT -> location.copy(zoom = location.zoom / 1.5f)
                PdfAdjustment.LEFT -> location.copy(centerX = location.centerX - step)
                PdfAdjustment.RIGHT -> location.copy(centerX = location.centerX + step)
                PdfAdjustment.UP -> location.copy(centerY = location.centerY - step)
                PdfAdjustment.DOWN -> location.copy(centerY = location.centerY + step)
                PdfAdjustment.CROP -> location.copy(crop = !location.crop)
                PdfAdjustment.FIT -> ReaderLocator.Pdf(location.page)
            }
        return page(next)
    }

    private suspend fun render(target: ReaderLocator.Pdf): Bitmap {
        val bytes = PdfViewport.bytes(width, height)
        return SharedMemory.create("reader-page", bytes).use { shared ->
            client.render(target, width, height, shared)
            val mapped = shared.mapReadOnly()
            try {
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { it.copyPixelsFromBuffer(mapped) }
            } finally {
                SharedMemory.unmap(mapped)
            }
        }
    }

    override fun close() {
        cache.evictAll()
        client.close()
    }

    companion object {
        suspend fun create(
            context: Context,
            handle: DocumentHandle,
            width: Int,
            height: Int,
        ): PdfEngine {
            PdfViewport.bytes(width, height)
            val client = PdfClient(context)
            var retained = false
            try {
                client.connect()
                val pages = handle.openSeekable().use { client.open(it) }
                require(pages > 0)
                return PdfEngine(client, width, height, pages).also { retained = true }
            } finally {
                if (!retained) client.close()
            }
        }
    }
}
