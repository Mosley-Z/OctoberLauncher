package com.shuiyu.octoberrain.feature.content.data

import com.shuiyu.octoberrain.core.common.AppJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 笔记分页与内容合并的单元测试。
 *
 * 覆盖划线与想法的合并排序、游标传递、字段缺失降级、空标识过滤与密钥路径。
 */
class WereadNotesTest {
    private fun marksBody(): String =
        buildJsonObject {
            put(
                "updated",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("chapterUid", 10)
                            put("markText", "划线原文")
                            put("createTime", 1_700_000_000L)
                        },
                    )
                },
            )
            put(
                "chapters",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("chapterUid", 10)
                            put("title", "第一章")
                        },
                    )
                },
            )
        }.toString()

    private fun reviewsBody(): String =
        buildJsonObject {
            put(
                "reviews",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put(
                                "review",
                                buildJsonObject {
                                    put("content", "个人想法")
                                    put("abstract", "想法对应的原文")
                                    put("chapterName", "第二章")
                                    put("createTime", 1_700_000_500L)
                                },
                            )
                        },
                    )
                },
            )
        }.toString()

    /** 同一本书的划线与想法合并后按创建时间降序排列。 */
    @Test
    fun `划线想法合并排序`() =
        runBlocking {
            val transport = notesTransport(notebooksBody(), marksBody(), reviewsBody())
            val page = testClient(transport).notes()
            assertEquals(2, page.notes.size)
            assertEquals("个人想法", page.notes.first().thought)
            assertEquals("划线原文", page.notes.last().text)
            assertEquals("第一章", page.notes.last().chapterTitle)
            assertEquals("第二章", page.notes.first().chapterTitle)
            assertEquals("b1", page.notes.first().bookId)
            assertEquals("测试书籍", page.notes.first().bookTitle)
            assertEquals("weread://reading?bId=b1", page.notes.first().deepLink)
        }

    /** hasMore 为 1 时返回最后一本书的排序值作为游标。 */
    @Test
    fun `有更多数据时返回游标`() =
        runBlocking {
            val transport = notesTransport(notebooksBody(sort = 100L, hasMore = 1), marksBody(), reviewsBody())
            val page = testClient(transport).notes()
            assertEquals("100", page.nextCursor)
            assertEquals(1, page.total)
        }

    /** hasMore 为 0 时不返回游标。 */
    @Test
    fun `无更多数据时游标为空`() =
        runBlocking {
            val page = testClient(notesTransport(notebooksBody(hasMore = 0))).notes()
            assertNull(page.nextCursor)
        }

    /** 请求携带 lastSort 游标与条数上限。 */
    @Test
    fun `翻页请求携带游标`() =
        runBlocking {
            val transport = notesTransport(notebooksBody(hasMore = 0), marksBody())
            testClient(transport).notes(limit = 10, cursor = "100")
            val first = AppJson.format.parseToJsonElement(transport.requests.first()) as JsonObject
            assertEquals("100", first["lastSort"]?.jsonPrimitive?.content)
            assertEquals("10", first["count"]?.jsonPrimitive?.content)
        }

    /** 划线与想法缺少可选字段时降级为空字符串。 */
    @Test
    fun `字段缺失降级为空字符串`() =
        runBlocking {
            val marks =
                buildJsonObject {
                    put(
                        "updated",
                        buildJsonArray {
                            add(buildJsonObject { put("markText", "只有原文") })
                        },
                    )
                }.toString()
            val reviews =
                buildJsonObject {
                    put(
                        "reviews",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("review", buildJsonObject { put("content", "只有想法") })
                                },
                            )
                        },
                    )
                }.toString()
            val page = testClient(notesTransport(notebooksBody(), marks, reviews)).notes()
            val original = page.notes.first { it.text == "只有原文" }
            val thought = page.notes.first { it.thought == "只有想法" }
            assertEquals("", original.chapterTitle)
            assertEquals("", original.thought)
            assertEquals("", thought.text)
            assertEquals("", thought.chapterTitle)
        }

    /** 未配置密钥时返回空页且不发起请求。 */
    @Test
    fun `缺少密钥返回空页`() =
        runBlocking {
            val transport = notesTransport(notebooksBody())
            val page = testClient(transport, key = "").notes()
            assertTrue(page.notes.isEmpty())
            assertTrue(transport.requests.isEmpty())
        }

    /** 书目标识为空的书被跳过。 */
    @Test
    fun `空书目标识被跳过`() =
        runBlocking {
            val notebooks =
                buildJsonObject {
                    put(
                        "books",
                        buildJsonArray {
                            add(buildJsonObject { put("sort", 1L) })
                            add(
                                buildJsonObject {
                                    put("bookId", "b2")
                                    put("sort", 2L)
                                    put("book", buildJsonObject { put("title", "第二本") })
                                },
                            )
                        },
                    )
                    put("hasMore", 0)
                }.toString()
            val page = testClient(notesTransport(notebooks, marksBody())).notes()
            assertEquals("b2", page.notes.first().bookId)
        }

    /** 网关错误码导致空页。 */
    @Test
    fun `笔记接口错误码返回空页`() =
        runBlocking {
            val transport = FakeTransport { RawResponse(200, """{"errcode":-2012}""") }
            val page = testClient(transport).notes()
            assertTrue(page.notes.isEmpty())
        }
}
