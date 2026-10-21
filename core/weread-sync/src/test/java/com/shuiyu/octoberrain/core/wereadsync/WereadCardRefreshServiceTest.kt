package com.shuiyu.octoberrain.core.wereadsync

import com.shuiyu.octoberrain.core.common.WereadRefreshRequest
import com.shuiyu.octoberrain.core.data.WereadDataGate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WereadCardRefreshServiceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun obj(raw: String): JsonObject = Json.parseToJsonElement(raw).jsonObject

    @Test
    fun `绑定关闭仍刷新当前形态且各绑定只发一次`() {
        assertEquals(listOf("monthly"), WereadCardRefreshService.refreshTargets("monthly", 0))
        assertEquals(listOf("monthly", "weekly", "book"), WereadCardRefreshService.refreshTargets("monthly", 7))
        assertEquals(listOf("note"), WereadCardRefreshService.refreshTargets("note", 7))
        assertTrue(WereadCardRefreshService.refreshTargets("todo", 7).isEmpty())
    }

    @Test
    fun `原版绑定掩码迁移后保留且独立布尔按位覆盖`() {
        assertEquals(2, WereadCardRefreshService.bindingMask(obj("""{"int":{"bind_targets":2}}""")))
        assertEquals(
            6,
            WereadCardRefreshService.bindingMask(
                obj(
                    """
                    {"int":{"bind_targets":3},"bool":{"bind_week":false,"bind_book":true}}
                    """.trimIndent(),
                ),
            ),
        )
    }

    @Test
    fun `历史月请求该期而绑定周请求当前期不预热历史资产`() =
        runBlocking {
            File(folder.root, "kernel_prefs.json").writeText("""{"int":{"bind_targets":1}}""")
            val calls = mutableListOf<Pair<String, JsonObject>>()
            val service =
                WereadCardRefreshService(
                    WereadRequest { api, body ->
                        calls += api to body
                        obj("""{"readTime":60,"readTimes":{}}""")
                    },
                    folder.root,
                    { 1_700_000_000_000L },
                )
            assertTrue(service.refresh(WereadRefreshRequest("monthly", 1_600_000_000L)).success)
            assertEquals(listOf("monthly", "weekly"), calls.map { it.second["mode"]!!.jsonPrimitive.content })
            assertEquals("1600000000", calls[0].second["baseTime"]!!.jsonPrimitive.content)
            assertTrue(calls.all { it.first == "/readdata/detail" })
            assertEquals(1_700_000_000_000L, WereadStore(folder.root).getLong("t_monthly:1600000000"))
        }

    @Test
    fun `网络失败保留统计正文与原成功时间`() =
        runBlocking {
            val store = WereadStore(folder.root)
            store.writeStats("weekly", 123, """{"readTime":77}""", 456)
            File(folder.root, "kernel_prefs.json").let { file ->
                val prefs = obj(file.readText())
                file.writeText(JsonObject(prefs + ("int" to obj("""{"bind_targets":0}"""))).toString())
            }
            val service = WereadCardRefreshService(WereadRequest { _, _ -> null }, folder.root)
            assertFalse(service.refresh(WereadRefreshRequest("weekly", 123)).success)
            assertEquals(456L, store.getLong("t_weekly:123"))
            assertTrue(store.getString("d_weekly:123")!!.contains("77"))
        }

    @Test
    fun `手动书补查选中书真实进度而不扫描历史资产`() =
        runBlocking {
            File(folder.root, "kernel_prefs.json").writeText("""{"int":{"bind_targets":0}}""")
            val ids = mutableListOf<String>()
            val service =
                WereadCardRefreshService(
                    WereadRequest { api, body ->
                        if (api == "/shelf/sync") {
                            obj(
                                """
                                {"books":[{"bookId":"latest","readUpdateTime":9},
                                {"bookId":"picked","readUpdateTime":1}]}
                                """.trimIndent(),
                            )
                        } else {
                            ids += body["bookId"]!!.jsonPrimitive.content
                            obj("""{"book":{"progress":35,"readingTime":103831}}""")
                        }
                    },
                    folder.root,
                )
            assertTrue(service.refresh(WereadRefreshRequest("book", bookId = "picked")).success)
            assertEquals(listOf("latest", "picked"), ids)
            assertEquals("picked", obj(File(folder.root, "book.json").readText())["bookId"]!!.jsonPrimitive.content)
        }

    @Test
    fun `本记手动刷新绕过正文有效期且不发绑定统计`() =
        runBlocking {
            val calls = mutableListOf<String>()
            val transport =
                WereadRequest { api, _ ->
                    calls += api
                    when (api) {
                        "/user/notebooks" ->
                            obj(
                                """{"books":[{"bookId":"b","sort":1,"book":{"title":"书"}}],"hasMore":0}""",
                            )
                        "/book/bookmarklist" -> obj("""{"updated":[],"chapters":[]}""")
                        else -> obj("""{"reviews":[],"hasMore":0}""")
                    }
                }
            assertTrue(WereadNoteSync(transport, folder.root).sync()!!.complete)
            calls.clear()
            assertTrue(WereadCardRefreshService(transport, folder.root).refresh(WereadRefreshRequest("note")).success)
            assertEquals(listOf("/user/notebooks", "/book/bookmarklist", "/review/list/mine"), calls)
        }

    @Test
    fun `待办无需凭据或联网请求且异常释放互斥门`() =
        runBlocking {
            val service = WereadCardRefreshService(WereadRequest { _, _ -> error("offline") }, folder.root)
            assertTrue(service.refresh(WereadRefreshRequest("todo")).success)
            runCatching { service.refresh(WereadRefreshRequest("weekly")) }
            assertTrue(WereadDataGate.tryBeginImport())
            WereadDataGate.endImport()
        }
}
