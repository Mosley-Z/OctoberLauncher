package com.shuiyu.octoberrain.feature.widgets

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageLoadPolicyTest {
    @Test
    fun readsPreserveCacheButMutationsInvalidate() {
        val cache = ImageDirectoryCache(probe = { 1L }, scan = { emptyList() })
        val key = ImageDirectoryCache.Key(0, "/images", "/images/a", null)
        for (event in listOf(1, 0x10, 0x20, 8, 0x40, 0x80, 0x100, 0x200, 0x400, 0x800)) {
            val request = cache.request(key)
            if (ImageLoadPolicy.isMutation(event)) cache.onEvent(request)
            if (event in listOf(1, 0x10, 0x20)) {
                assertTrue(cache.isCurrent(request))
            } else {
                assertFalse(cache.isCurrent(request))
            }
        }
    }

    @Test
    fun hiddenPreviewAndMetadataDecisions() {
        assertFalse(ImageLoadPolicy.isHiding(false, false))
        assertTrue(ImageLoadPolicy.isHiding(true, false))
        assertFalse(ImageLoadPolicy.isHiding(false, true))
        assertTrue(ImageLoadPolicy.sameContent(12L, 12L))
        assertFalse(ImageLoadPolicy.sameContent(12L, 13L))
    }
}
