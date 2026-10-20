package com.shuiyu.octoberrain.feature.content.data

import com.shuiyu.octoberrain.core.common.AppJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 阅读统计解析与缓存路径的单元测试。
 *
 * 传输层由固定响应替代，覆盖周、年、总计三种模式，时间戳键与秒/毫秒单位，
 * 以及密钥缺失、错误码与缓存命中等分支。
 */
class WereadStatsTest {
    private val base = 1_700_006_400L

    /** 本周逐日数据按时间升序返回，总时长与天数来自顶层字段。 */
    @Test
    fun `周统计解析时间戳键`() =
        runBlocking {
            val body =
                statsBody {
                    put("baseTime", base)
                    put("totalReadTime", 7200L)
                    put("readDays", 3)
                    put("dayAverageReadTime", 2400L)
                    put(
                        "readTimes",
                        buildJsonObject {
                            put("1700092800", 3600L)
                            put("1700006400", 3600L)
                        },
                    )
                }
            val stats = testClient(statsTransport(body)).weekly(base)
            assertNotNull(stats)
            assertEquals(WereadPeriod.WEEKLY, stats?.period)
            assertEquals(7200L, stats?.totalSeconds)
            assertEquals(3, stats?.readDays)
            assertEquals(2, stats?.buckets?.size)
            assertEquals(3600L, stats?.buckets?.first()?.seconds)
            assertTrue((stats?.buckets?.first()?.startMillis ?: 0L) < (stats?.buckets?.last()?.startMillis ?: 0L))
        }

    /** 分桶值以毫秒给出时按周粒度归一为秒。 */
    @Test
    fun `周统计分桶毫秒归一`() =
        runBlocking {
            val body =
                statsBody {
                    put("totalReadTime", 3600L)
                    put("readTimes", buildJsonObject { put("1700006400", 3_600_000L) })
                }
            val stats = testClient(statsTransport(body)).weekly(base)
            assertEquals(3600L, stats?.buckets?.single()?.seconds)
        }

    /** 年统计按月分桶，大于日均阈值的秒数保持原值。 */
    @Test
    fun `年统计月分桶保留秒值`() =
        runBlocking {
            val body =
                statsBody {
                    put("baseTime", base)
                    put("totalReadTime", 900_000L)
                    put(
                        "readTimes",
                        buildJsonObject {
                            put("1704067200", 200_000L)
                            put("1706745600", 700_000L)
                        },
                    )
                }
            val stats = testClient(statsTransport(body)).annually(base)
            assertEquals(WereadPeriod.ANNUALLY, stats?.period)
            assertEquals(listOf(200_000L, 700_000L), stats?.buckets?.map { it.seconds })
        }

    /** 总计模式返回累计时长、天数与按年分桶。 */
    @Test
    fun `总计统计解析累计字段`() =
        runBlocking {
            val body =
                statsBody {
                    put("totalReadTime", 5_000_000L)
                    put("readDays", 120)
                    put("dayAverageReadTime", 1200L)
                    put("readTimes", buildJsonObject { put("1609459200", 2_500_000L) })
                }
            val stats = testClient(statsTransport(body)).overall()
            assertEquals(WereadPeriod.OVERALL, stats?.period)
            assertEquals(0L, stats?.baseTimeSeconds)
            assertEquals(5_000_000L, stats?.totalSeconds)
            assertEquals(120, stats?.readDays)
            assertEquals(2_500_000L, stats?.buckets?.single()?.seconds)
        }

    /** 总时长以毫秒给出时换算为秒。 */
    @Test
    fun `总时长毫秒归一`() =
        runBlocking {
            val body = statsBody { put("totalReadTime", 500_000_000L) }
            val stats = testClient(statsTransport(body)).overall()
            assertEquals(500_000L, stats?.totalSeconds)
        }

    /** readTimes 缺失时回落到 dailyReadTimes。 */
    @Test
    fun `每日明细作为回退来源`() =
        runBlocking {
            val body =
                statsBody {
                    put("dailyReadTimes", buildJsonObject { put("1700006400", 1800L) })
                }
            val stats = testClient(statsTransport(body)).weekly(base)
            assertEquals(1800L, stats?.buckets?.single()?.seconds)
        }

    /** 数字序号键按周期内日期依次换算。 */
    @Test
    fun `周期内序号键换算为连续日期`() =
        runBlocking {
            val body =
                statsBody {
                    put(
                        "readTimes",
                        buildJsonObject {
                            put("1", 600L)
                            put("2", 1200L)
                        },
                    )
                }
            val stats = testClient(statsTransport(body)).weekly(base)
            val buckets = stats?.buckets.orEmpty()
            assertEquals(2, buckets.size)
            assertEquals(86_400_000L, buckets[1].startMillis - buckets[0].startMillis)
        }

    /** 请求体顶层平铺业务参数，不含 params 包装。 */
    @Test
    fun `请求体字段平铺`() =
        runBlocking {
            val transport = statsTransport(statsBody { put("totalReadTime", 60L) })
            testClient(transport).weekly(base)
            val body = AppJson.format.parseToJsonElement(transport.requests.single()) as JsonObject
            assertEquals("/readdata/detail", body["api_name"]?.jsonPrimitive?.content)
            assertEquals("1.0.5", body["skill_version"]?.jsonPrimitive?.content)
            assertEquals("weekly", body["mode"]?.jsonPrimitive?.content)
            assertNull(body["params"])
        }

    /** HTTP 状态非成功时返回 null。 */
    @Test
    fun `HTTP错误返回空`() =
        runBlocking {
            val transport = FakeTransport { RawResponse(500, "error") }
            assertNull(testClient(transport).weekly(base))
        }

    /** 业务错误码 -2012 视为密钥失效。 */
    @Test
    fun `密钥失效错误码返回空`() =
        runBlocking {
            val transport = FakeTransport { RawResponse(200, """{"errcode":-2012}""") }
            assertNull(testClient(transport).weekly(base))
        }

    /** 其它业务错误码同样返回 null。 */
    @Test
    fun `业务错误码返回空`() =
        runBlocking {
            val transport = FakeTransport { RawResponse(200, """{"errcode":7,"message":"denied"}""") }
            assertNull(testClient(transport).weekly(base))
        }

    /** 未配置密钥时不发起请求。 */
    @Test
    fun `缺少密钥不发起请求`() =
        runBlocking {
            val transport = statsTransport(statsBody { put("totalReadTime", 60L) })
            assertNull(testClient(transport, key = "").weekly(base))
            assertTrue(transport.requests.isEmpty())
        }

    /** 集成关闭时不发起请求。 */
    @Test
    fun `集成关闭返回空`() =
        runBlocking {
            val transport = statsTransport(statsBody { put("totalReadTime", 60L) })
            assertNull(testClient(transport, enabled = false).weekly(base))
            assertTrue(transport.requests.isEmpty())
        }

    /** 同一周期第二次读取命中缓存，不重复请求。 */
    @Test
    fun `同周期重复读取命中缓存`() =
        runBlocking {
            val transport = statsTransport(statsBody { put("totalReadTime", 60L) })
            val cache = WereadCache(File(Files.createTempDirectory("weread-memory").toFile(), "cache"))
            val client = testClient(transport, cache)
            val first = client.weekly(base)
            val second = client.weekly(base)
            assertEquals(1, transport.requests.size)
            assertEquals(first?.totalSeconds, second?.totalSeconds)
        }

    /** 磁盘缓存可跨客户端实例复用。 */
    @Test
    fun `磁盘缓存跨实例复用`() =
        runBlocking {
            val directory = File(Files.createTempDirectory("weread-disk").toFile(), "cache")
            val transport = statsTransport(statsBody { put("totalReadTime", 90L) })
            testClient(transport, WereadCache(directory)).weekly(base)
            val secondTransport = statsTransport(statsBody { put("totalReadTime", 999L) })
            val second = testClient(secondTransport, WereadCache(directory)).weekly(base)
            assertEquals(90L, second?.totalSeconds)
            assertTrue(secondTransport.requests.isEmpty())
        }
}
