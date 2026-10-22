package com.shuiyu.octoberrain.feature.reader.source

import com.shuiyu.octoberrain.core.common.ReaderFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BoundedCopyTest {
    @Test fun exactLimitCopiesAndDoesNotNeedKnownInputLength() {
        val output = ByteArrayOutputStream()
        assertEquals(3L, copyBounded(ByteArrayInputStream(byteArrayOf(1, 2, 3)), output, 3))
        assertEquals(3, output.size())
    }

    @Test fun overBudgetStopsBeforeWritingExcessBytes() {
        val output = ByteArrayOutputStream()
        try {
            copyBounded(ByteArrayInputStream(ByteArray(40000)), output, 35000)
            fail("expected budget rejection")
        } catch (error: ReaderSourceException) {
            assertEquals(ReaderFailure.LIMIT, error.failure)
            assertEquals(32768, output.size())
        }
    }
}
