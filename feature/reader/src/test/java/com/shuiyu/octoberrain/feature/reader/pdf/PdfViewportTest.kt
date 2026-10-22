package com.shuiyu.octoberrain.feature.reader.pdf

import com.shuiyu.octoberrain.core.common.ReaderLocator
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfViewportTest {
    @Test fun bitmapBudgetUsesLongArithmeticAndRejectsOversizedViewport() {
        assertEquals(480 * 800 * 4, PdfViewport.bytes(480, 800))
        try {
            PdfViewport.bytes(4096, 4096)
            throw AssertionError("budget accepted")
        } catch (
            _: IllegalArgumentException,
        ) {
            // 64MiB不能绕过12MiB预算。
        }
        try {
            PdfViewport.bytes(Int.MAX_VALUE, Int.MAX_VALUE)
            throw AssertionError("overflow accepted")
        } catch (
            _: IllegalArgumentException,
        ) {
            // 不允许乘法溢出绕过预算。
        }
    }

    @Test fun staleAndNonFiniteLocatorsAreClampedBeforeCrossingBinder() {
        val value = PdfViewport.normalize(ReaderLocator.Pdf(999, Float.NaN, Float.POSITIVE_INFINITY, -8f), 7)
        assertEquals(6, value.page)
        assertEquals(1f, value.zoom, 0f)
        assertEquals(0.5f, value.centerX, 0f)
        assertEquals(0f, value.centerY, 0f)
    }

    @Test fun zoomAndCropChangeMatrixWithoutChangingPixelAllocation() {
        val fit = PdfViewport.scale(600, 800, 480, 640, 1f, false)
        assertEquals(0.8f, fit, 0.0001f)
        assertEquals(fit * 4, PdfViewport.scale(600, 800, 480, 640, 4f, false), 0.0001f)
        assertEquals(fit / 0.9f, PdfViewport.scale(600, 800, 480, 640, 1f, true), 0.0001f)
    }
}
