package com.shuiyu.octoberrain.core.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ByteBudgetCacheTest {
    @Test fun sizesShareOneBudgetAndLeastRecentIsEvicted() {
        val cache = ByteBudgetCache<String>(8) { it.length }
        cache.put("small:a", "1234")
        cache.put("large:b", "1234")
        cache.get("small:a")
        cache.put("list:c", "12")
        assertNull(cache.get("large:b"))
        assertEquals("1234", cache.get("small:a"))
        assertEquals(6, cache.size())
    }

    @Test fun loweringBudgetAppliesToExistingEntriesAndReplacementDoesNotDoubleCount() {
        val cache = ByteBudgetCache<String>(12) { it.length }
        cache.put("a", "123456")
        cache.put("b", "123456")
        cache.put("b", "1234")
        assertEquals(10, cache.size())
        cache.resize(4)
        assertNull(cache.get("a"))
        assertEquals(4, cache.size())
        cache.clear()
        assertEquals(0, cache.size())
    }

    @Test fun oversizedEntryCannotExceedBudget() {
        val cache = ByteBudgetCache<String>(2) { it.length }
        cache.put("large", "1234")
        assertEquals(0, cache.size())
        assertNull(cache.get("large"))
    }
}
