package com.shuiyu.octoberrain.core.common

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WereadRecentReadTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun currentShelfTimesOverrideAssetsAndFillNewBooks() {
        val times =
            mergeRecentReadTimes(
                """[{"id":"a","readUpdateTime":200},{"id":"b","readUpdateTime":100}]""",
                """{"books":[
                {"bookId":"a","readUpdateTime":200},{"bookId":"b","readUpdateTime":300},
                {"bookId":"c","readUpdateTime":400}]}""",
            )
        assertEquals(listOf("c", "b", "a"), sortByRecentRead(listOf("a", "b", "c"), { it }, times))
    }

    @Test fun absentShelfTimeFallsBackToAssets() {
        val times =
            mergeRecentReadTimes(
                """[{"id":"a","readUpdateTime":200},{"id":"b","readUpdateTime":100}]""",
                """{"books":[{"bookId":"a"},{"bookId":"b","readUpdateTime":300}]}""",
            )
        assertEquals(mapOf("a" to 200L, "b" to 300L), times)
    }

    @Test fun cacheInvalidatesWhenOnlyShelfChanges() {
        val assets = File(folder.root, "assets.json")
        val shelf = File(folder.root, "shelf.json")
        assets.writeText("""[{"id":"a","readUpdateTime":100}]""")
        shelf.writeText("""{"books":[{"bookId":"a","readUpdateTime":200}]}""")
        val cache = WereadRecentReadCache()
        assertEquals(200L, cache.read(folder.root)["a"])
        shelf.writeText("""{"books":[{"bookId":"a","readUpdateTime":3000}]}""")
        assertEquals(3000L, cache.read(folder.root)["a"])
        shelf.delete()
        assertEquals(100L, cache.read(folder.root)["a"])
    }
}
