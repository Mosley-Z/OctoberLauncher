package com.shuiyu.octoberrain.feature.reader.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.shuiyu.octoberrain.core.common.ReaderCapabilities
import com.shuiyu.octoberrain.core.common.ReaderFailure
import com.shuiyu.octoberrain.core.common.ReaderLocator
import com.shuiyu.octoberrain.feature.reader.ReaderEngine
import com.shuiyu.octoberrain.feature.reader.ReaderFrame
import com.shuiyu.octoberrain.feature.reader.source.DocumentHandle
import com.shuiyu.octoberrain.feature.reader.source.ReaderSourceException

/** Single sampled frame, fitted into a fixed viewport; no animation or full-size decoded bitmap retained. */
internal class ImageEngine(
    source: DocumentHandle,
    width: Int,
    height: Int,
) : ReaderEngine {
    private val frame: ReaderFrame
    override val capabilities = ReaderCapabilities()

    init {
        if (width.toLong() * height * 4 !in 1..MAX_BITMAP_BYTES) throw ReaderSourceException(ReaderFailure.LIMIT)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        source.openStream().use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) throw ReaderSourceException(ReaderFailure.CORRUPT)
        options.inJustDecodeBounds = false
        options.inSampleSize = sample(options.outWidth, options.outHeight, width, height)
        val bitmap =
            source.openStream().use { BitmapFactory.decodeStream(it, null, options) }
                ?: throw ReaderSourceException(ReaderFailure.CORRUPT)
        try {
            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            canvas.drawColor(Color.WHITE)
            val scale = minOf(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
            val left = (width - bitmap.width * scale) / 2
            val top = (height - bitmap.height * scale) / 2
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(left, top, width - left, height - top),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            frame = ReaderFrame(ReaderLocator.Image(), "1/1 · 图片", bitmap = output)
        } finally {
            bitmap.recycle()
        }
    }

    override suspend fun page(locator: ReaderLocator?): ReaderFrame = frame

    override suspend fun turn(delta: Int): ReaderFrame = frame

    override fun close() = Unit // Published frame may still be held by a View until the next main-thread update.

    private fun sample(
        imageWidth: Int,
        imageHeight: Int,
        width: Int,
        height: Int,
    ): Int {
        var sample = 1
        while (imageWidth / sample > width * 2 ||
            imageHeight / sample > height * 2 ||
            (imageWidth.toLong() / sample) * (imageHeight / sample) * 4 > MAX_BITMAP_BYTES
        ) {
            sample *= 2
        }
        return sample
    }

    private companion object {
        const val MAX_BITMAP_BYTES = 12L * 1024 * 1024
    }
}
