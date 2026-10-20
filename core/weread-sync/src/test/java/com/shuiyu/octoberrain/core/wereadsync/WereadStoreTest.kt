package com.shuiyu.octoberrain.core.wereadsync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 数据目录写入器的结构断言。 */
class WereadStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun store(): WereadStore = WereadStore(File(folder.root, "weread"))

    /** 构造一条测试书目。 */
    private fun book(id: String = "b1"): WereadShelfBook =
        WereadShelfBook(
            bookId = id,
            title = "罪与罚",
            author = "陀思妥耶夫斯基",
            coverUrl = "https://example.test/c.jpg",
            readingSeconds = 3600,
            progress = 23,
            finished = false,
            finishTimeMillis = 0L,
        )

    /** 书架写入后键名与顺序符合内核读取约定。 */
    @Test
    fun `写入书架后可按原版键名读取`() {
        val store = store()
        store.writeShelf(listOf(book()), savedAtMillis = 1234L)
        assertTrue(store.hasShelf())
        val json = Json.parseToJsonElement(shelfFile().readText()).jsonObject
        val books = json["books"] as JsonArray
        assertEquals(1, books.size)
        val entry = books[0].jsonObject
        assertEquals("b1", entry["bookId"].toString().trim('"'))
        assertEquals("罪与罚", entry["title"].toString().trim('"'))
        assertEquals("3600", entry["readingTime"].toString())
        assertEquals("1234", json["savedAt"].toString())
    }

    /** 当前书目写入 book.json，清空时删除文件。 */
    @Test
    fun `当前书目可写可清`() {
        val store = store()
        store.writeCurrentBook(book())
        val json = Json.parseToJsonElement(File(folder.root, "weread/book.json").readText()).jsonObject
        assertEquals("23", json["progress"].toString())
        store.writeCurrentBook(null)
        assertFalse(File(folder.root, "weread/book.json").exists())
    }

    /** 统计写入周期键与时间键，并同步 stats.json 回退文件。 */
    @Test
    fun `统计写入周期键与回退文件`() {
        val store = store()
        store.writeStats(WereadStore.MODE_WEEKLY, 1000L, "{\"total\":60}", 5000L)
        val prefs = prefsJson()
        val strings = prefs["string"] as JsonObject
        assertEquals("{\"total\":60}", strings.getValue("d_weekly:1000").jsonPrimitive.content)
        val longs = prefs["long"] as JsonObject
        assertEquals("5000", longs["t_weekly:1000"].toString())
        val stats = Json.parseToJsonElement(File(folder.root, "weread/stats.json").readText()).jsonObject
        assertNotNull(stats["d_weekly:1000"])
    }

    /** 年度统计按年份分键，避免与周月键冲突。 */
    @Test
    fun `年度统计按年份写键`() {
        val store = store()
        val base = 1761000000L
        store.writeStats(WereadStore.MODE_ANNUALLY, base, "{\"y\":1}", 7000L)
        val strings = prefsJson()["string"] as JsonObject
        val key = strings.keys.firstOrNull { it.startsWith("stats_annual_") && !it.contains("_t_") }
        assertNotNull(key)
        assertEquals("{\"y\":1}", strings.getValue(key!!).jsonPrimitive.content)
    }

    /** 资产文件保留书名、时长、进度与读完时刻四组字段。 */
    @Test
    fun `资产写入 books 数组`() {
        val store = store()
        store.writeAssets(
            listOf(
                WereadAssetBook("a1", "赤心巡天", "情何以甚", 82 * 3600, 9990, 9990, 100, true, 1_700_000_000_000L),
            ),
        )
        val json = Json.parseToJsonElement(File(folder.root, "weread/assets.json").readText()).jsonObject
        val entry = (json["books"] as JsonArray)[0].jsonObject
        assertEquals("295200", entry["sec"].toString())
        assertEquals("100", entry["pct"].toString())
        assertEquals("1700000000000", entry["fin"].toString())
    }

    /** 偏好写入保持 string、long、int、bool 四组结构且不覆盖既有键。 */
    @Test
    fun `偏好写入保留四组结构`() {
        val store = store()
        store.putString("api_key", "wrk-1")
        store.putString("card_period", "monthly")
        store.putInt("menu_top_n", 5)
        store.putBool("menu_show_author", true)
        val prefs = prefsJson()
        assertEquals(setOf("string", "long", "int", "bool"), prefs.keys)
        val strings = prefs["string"] as JsonObject
        assertEquals("wrk-1", strings["api_key"].toString().trim('"'))
        assertEquals("monthly", strings["card_period"].toString().trim('"'))
        assertEquals("5", (prefs["int"] as JsonObject)["menu_top_n"].toString())
        assertEquals("true", (prefs["bool"] as JsonObject)["menu_show_author"].toString())
        store.putString("api_key", "wrk-2")
        assertEquals("wrk-2", (prefsJson()["string"] as JsonObject).getValue("api_key").jsonPrimitive.content)
    }

    /** 没有书架数据时判定为未同步。 */
    @Test
    fun `空目录判定无书架`() {
        assertFalse(store().hasShelf())
    }

    /** 读取临时目录内的书架文件。 */
    private fun shelfFile(): File = File(folder.root, "weread/shelf.json")

    /** 读取临时目录内的键值文件。 */
    private fun prefsJson(): JsonObject =
        Json.parseToJsonElement(File(folder.root, "weread/kernel_prefs.json").readText()).jsonObject
}

/** 同步编排的流程断言。 */
class WereadSyncServiceTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** 构造服务与存储的组合。 */
    private fun service(provider: WereadSyncProvider): Pair<WereadSyncService, WereadStore> {
        val store = WereadStore(File(folder.root, "weread"))
        return WereadSyncService(store, provider) { 9000L } to store
    }

    /** 提供者返回完整数据时，五类数据全部落盘并记录状态。 */
    @Test
    fun `完整同步写入五类数据`() {
        val provider =
            object : WereadSyncProvider {
                override suspend fun shelf(): List<WereadShelfBook> =
                    listOf(WereadShelfBook("b1", "罪与罚", "陀思妥耶夫斯基", "", 3600, 23, false, 0L))

                override suspend fun stats(
                    mode: String,
                    baseTimeSec: Long,
                ): String = "{\"total\":60}"

                override suspend fun assets(): List<WereadAssetBook> =
                    listOf(WereadAssetBook("a1", "赤诚", "作者", 100, 0, 0, 100, true))

                override suspend fun bill(
                    mode: String,
                    periodStartSec: Long,
                    statsRaw: String,
                ): String = "{\"items\":[]}"

                override suspend fun notes(): WereadNoteBatch = WereadNoteBatch("{}", emptyList())
            }
        val (service, store) = service(provider)
        val report = runBlocking { service.sync() }
        assertTrue(report.success)
        assertEquals(1, report.shelfCount)
        // 默认范围含 12 周、12 月、3 年与全时段，统计期数合计 28。
        assertEquals(28, report.statCount)
        // 账单只为周与月两类周期写入，合计 24 期。
        assertEquals(24, report.billCount)
        assertEquals(1, report.assetCount)
        assertEquals(0, report.noteCount)
        assertTrue(store.hasShelf())
        val state = parseSyncState(store.getString(WereadSyncService.KEY_SYNC_STATE))
        assertNotNull(state)
        assertTrue(state!!.success)
        assertEquals(9000L, state.finishedAtMillis)
    }

    /** 提供者全部返回空值时报告失败且不写书架。 */
    @Test
    fun `空数据报告失败`() {
        val provider =
            object : WereadSyncProvider {
                override suspend fun shelf(): List<WereadShelfBook>? = null

                override suspend fun stats(
                    mode: String,
                    baseTimeSec: Long,
                ): String? = null

                override suspend fun assets(): List<WereadAssetBook>? = null

                override suspend fun bill(
                    mode: String,
                    periodStartSec: Long,
                    statsRaw: String,
                ): String? = null

                override suspend fun notes(): WereadNoteBatch? = null
            }
        val (service, store) = service(provider)
        val report = runBlocking { service.sync() }
        assertFalse(report.success)
        assertFalse(store.hasShelf())
        val state = parseSyncState(store.getString(WereadSyncService.KEY_SYNC_STATE))
        assertEquals(false, state?.success)
    }

    /** 默认同步范围覆盖最近十二周、最近十二个月、最近三年与全时段。 */
    @Test
    fun `默认同步范围覆盖近三年`() {
        val periods = WereadSyncService.defaultPeriods(FIXED_MILLIS)
        assertEquals(28, periods.size)
        val modes = periods.map { it.mode }
        assertEquals(
            List(WereadSyncService.PERIOD_WEEK_COUNT) { WereadStore.MODE_WEEKLY } +
                List(WereadSyncService.PERIOD_MONTH_COUNT) { WereadStore.MODE_MONTHLY } +
                List(WereadSyncService.PERIOD_YEAR_COUNT) { WereadStore.MODE_ANNUALLY } +
                WereadStore.MODE_OVERALL,
            modes,
        )
        // 固定时刻为 2026-10-20 12:00（周二），第一项是本周起点，最后一项为全时段。
        assertEquals(WEEK_START_SEC, periods.first().baseTimeSec)
        assertEquals(MONTH_START_SEC, periods[WereadSyncService.PERIOD_WEEK_COUNT].baseTimeSec)
        assertEquals(
            YEAR_START_SEC,
            periods[WereadSyncService.PERIOD_WEEK_COUNT + WereadSyncService.PERIOD_MONTH_COUNT].baseTimeSec,
        )
        assertEquals(0L, periods.last().baseTimeSec)
        // 周起点按七天递减，月起点按月递减，年起点按年递减。
        val zone = ZoneId.systemDefault()

        fun secOf(date: LocalDate): Long = date.atStartOfDay(zone).toEpochSecond()

        val week = Instant.ofEpochSecond(WEEK_START_SEC).atZone(zone).toLocalDate()
        val month = Instant.ofEpochSecond(MONTH_START_SEC).atZone(zone).toLocalDate()
        val year = Instant.ofEpochSecond(YEAR_START_SEC).atZone(zone).toLocalDate()
        val weekIndex = WereadSyncService.PERIOD_WEEK_COUNT - 1
        val monthIndex = WereadSyncService.PERIOD_WEEK_COUNT + WereadSyncService.PERIOD_MONTH_COUNT - 1
        val yearIndex =
            WereadSyncService.PERIOD_WEEK_COUNT +
                WereadSyncService.PERIOD_MONTH_COUNT +
                WereadSyncService.PERIOD_YEAR_COUNT - 1
        val monthOffset = monthIndex - WereadSyncService.PERIOD_WEEK_COUNT
        val yearOffset = yearIndex - WereadSyncService.PERIOD_WEEK_COUNT - WereadSyncService.PERIOD_MONTH_COUNT
        assertEquals(secOf(week.minusWeeks(weekIndex.toLong())), periods[weekIndex].baseTimeSec)
        assertEquals(secOf(month.minusMonths(monthOffset.toLong())), periods[monthIndex].baseTimeSec)
        assertEquals(secOf(year.minusYears(yearOffset.toLong())), periods[yearIndex].baseTimeSec)
    }

    /**
     * 并发写入不同偏好键时各键都保留。
     *
     * 内核偏好是「读文档、改一组、写回」的流程，不加锁时并发调用会互相覆盖；
     * 这里以八个协程同时写不同键，验证读回后全部存在。
     */
    @Test
    fun `并发写入不同偏好键不互相覆盖`() =
        runBlocking {
            val target = WereadStore(File(folder.root, "weread"))
            (1..8)
                .map { index ->
                    launch(Dispatchers.IO) { target.putString("slot_$index", "value_$index") }
                }.forEach { it.join() }

            (1..8).forEach { index ->
                assertEquals("value_$index", target.getString("slot_$index"))
            }
        }

    /** 目标目录不可写时写入返回失败，不抛出异常。 */
    @Test
    fun `目录不可写时写入返回失败`() {
        val root = File(folder.root, "weread_blocked")
        // 用普通文件占用目标目录路径，使建目录与写盘都失败。
        root.writeText("blocked")

        val blocked = WereadStore(root)

        assertFalse(blocked.putString("k", "v"))
        assertFalse(blocked.writeShelfIds(listOf("b1")))
    }
}

/** 固定时刻：2026-10-20 12:00 本地时间。 */
private const val FIXED_MILLIS: Long = 1_792_468_800_000L

/** 固定时刻所在周的周一零点秒数，2026-10-19 00:00。 */
private const val WEEK_START_SEC: Long = 1_792_339_200L

/** 固定时刻所在月的月初零点秒数，2026-10-01 00:00。 */
private const val MONTH_START_SEC: Long = 1_790_784_000L

/** 固定时刻所在年的年初零点秒数，2026-01-01 00:00。 */
private const val YEAR_START_SEC: Long = 1_767_196_800L
