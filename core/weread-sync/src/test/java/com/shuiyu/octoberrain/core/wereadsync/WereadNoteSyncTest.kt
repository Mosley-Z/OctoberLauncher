package com.shuiyu.octoberrain.core.wereadsync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 按官方回包结构验证分页、内核字段兼容与失败后续补。 */
class WereadNoteSyncTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val now = 1_800_000_000_000L

    private fun json(raw: String): JsonObject = Json.parseToJsonElement(raw).jsonObject

    private fun file(name: String) = File(folder.root, name)

    private fun cached(name: String) = json(file(name).readText())

    private fun book(
        id: String,
        sort: Int = 30,
    ): String =
        """{"bookId":"$id","sort":$sort,"noteCount":1,"reviewCount":1,"readingProgress":35,
            "book":{"title":"书$id","author":"作者","cover":"cover","price":12.5,"centPrice":1250}}"""

    private fun overview(
        books: String,
        more: Int = 0,
    ) = json("""{"books":[$books],"hasMore":$more}""")

    private fun emptyMarks() = json("""{"updated":[],"chapters":[]}""")

    private fun emptyIdeas() = json("""{"reviews":[],"hasMore":0,"synckey":0}""")

    @Test
    fun `all notebook and review pages normalize into kernel format`() {
        val calls = mutableListOf<Pair<String, JsonObject>>()
        val request =
            WereadRequest { api, params ->
                calls += api to params
                when (api) {
                    "/user/notebooks" ->
                        if (params["lastSort"] == null) overview(book("a"), 1) else overview(book("b", 20))
                    "/book/bookmarklist" ->
                        json(
                            """{"updated":[{"bookmarkId":"m","chapterUid":7,"range":"1-3","markText":"原文"}],
                        "chapters":[{"chapterUid":7,"chapterIdx":2,"title":"第二章"}]}""",
                        )
                    "/review/list/mine" ->
                        if (params["synckey"].toString() == "0") {
                            json(
                                """{"reviews":[{"review":{"reviewId":"r1","chapterUid":7,"content":"想法"}}],
                                "hasMore":1,"synckey":9}""",
                            )
                        } else {
                            json("""{"reviews":[{"review":{"reviewId":"r2","content":"书评"}}],"hasMore":0}""")
                        }
                    else -> error(api)
                }
            }
        val batch = WereadNoteSync(request, folder.root) { now }.sync()!!
        assertTrue(batch.complete)
        assertEquals(4, batch.updatedFiles)
        val books = json(batch.indexJson)["books"]!!.jsonArray
        assertEquals(2, books.size)
        assertEquals("书a", books[0].jsonObject["title"]!!.jsonPrimitive.content)
        assertEquals("35", books[0].jsonObject["progress"].toString())
        assertEquals("1250", books[0].jsonObject["centPrice"].toString())
        val mark = cached("nt_a.json")["marks"]!!.jsonArray[0].jsonObject
        assertEquals("第二章", mark["chapterTitle"]!!.jsonPrimitive.content)
        assertEquals(2, cached("ni_a.json")["ideas"]!!.jsonArray.size)
        val idea = cached("ni_a.json")["ideas"]!!.jsonArray[0].jsonObject
        assertEquals("第二章", idea["chapterName"]!!.jsonPrimitive.content)
        val nextPage = calls.first { it.first == "/user/notebooks" && it.second["lastSort"] != null }
        assertEquals("30", nextPage.second["lastSort"].toString())
        val reviews = calls.filter { it.first == "/review/list/mine" }
        assertTrue(reviews.all { it.second["bookid"] != null && it.second["bookId"] == null })
        assertTrue(calls.filter { it.first == "/book/bookmarklist" }.all { it.second.keys == setOf("bookId") })
    }

    @Test
    fun `idea-only books are synchronized and overview has no fixed total cap`() {
        var page = 0
        val batch =
            WereadNoteSync(
                WereadRequest { api, _ ->
                    when (api) {
                        "/user/notebooks" -> {
                            val entries =
                                (0 until 100).joinToString(",") { n ->
                                    """{"bookId":"b${page * 100 + n}","sort":${1000 - page * 100 - n},
                                "noteCount":0,"reviewCount":1}"""
                                }
                            overview(entries, if (++page < 3) 1 else 0)
                        }
                        "/book/bookmarklist" -> emptyMarks()
                        "/review/list/mine" ->
                            json(
                                """{"reviews":[{"review":{"reviewId":"r","content":"整书点评"}}],"hasMore":0}""",
                            )
                        else -> error(api)
                    }
                },
                folder.root,
            ) { now }.sync()!!
        assertTrue(batch.complete)
        assertEquals(300, json(batch.indexJson)["books"]!!.jsonArray.size)
        assertEquals(1, cached("ni_b299.json")["ideas"]!!.jsonArray.size)
    }

    @Test
    fun `failed overview page or repeated cursor keeps complete old index`() {
        val old = """[{"bookId":"old","title":"旧书"}]"""
        file("nt_index.json").writeText(old)
        var pages = 0
        val failedSync =
            WereadNoteSync(
                WereadRequest { _, _ -> if (++pages == 1) overview(book("a"), 1) else null },
                folder.root,
            )
        val failed = failedSync.sync()
        assertNull(failed)
        assertTrue(failedSync.warnings.single().contains("分页请求失败"))
        assertEquals(old, file("nt_index.json").readText())
        val loop = WereadNoteSync(WereadRequest { _, _ -> overview(book("a"), 1) }, folder.root).sync()
        assertNull(loop)
        assertEquals(old, file("nt_index.json").readText())
    }

    @Test
    fun `review identifier supports documented nested and older wrapper placement`() {
        val sync =
            WereadNoteSync(
                WereadRequest { api, _ ->
                    when (api) {
                        "/user/notebooks" -> overview(book("a"))
                        "/book/bookmarklist" -> emptyMarks()
                        "/review/list/mine" ->
                            json(
                                """{"reviews":[{"review":{"reviewId":"nested","content":"嵌套"}},
                            {"reviewId":"wrapper","review":{"content":"外层"}}],"hasMore":0}""",
                            )
                        else -> error(api)
                    }
                },
                folder.root,
            ) { now }
        assertTrue(sync.sync()!!.complete)
        val ids = cached("ni_a.json")["ideas"]!!.jsonArray.map { it.jsonObject["reviewId"]!!.jsonPrimitive.content }
        assertEquals(listOf("nested", "wrapper"), ids)
        assertTrue(sync.warnings.isEmpty())
    }

    @Test
    fun `failed review page retains old ideas while successful marks checkpoint and resume`() {
        val oldIdeas = """{"fetchedAt":1,"ideas":[{"reviewId":"old","content":"旧想法"}]}"""
        file("ni_a.json").writeText(oldIdeas)
        var failing = true
        var marksCalls = 0
        val request =
            WereadRequest { api, params ->
                when (api) {
                    "/user/notebooks" -> overview(book("a"))
                    "/book/bookmarklist" -> {
                        marksCalls++
                        emptyMarks()
                    }
                    "/review/list/mine" ->
                        if (failing && params["synckey"].toString() != "0") {
                            null
                        } else {
                            json(
                                """{"reviews":[{"review":{"reviewId":"new","content":"新想法"}}],
                                "hasMore":${if (failing) 1 else 0},"synckey":2}""",
                            )
                        }
                    else -> error(api)
                }
            }
        val first = WereadNoteSync(request, folder.root) { now }.sync()!!
        assertFalse(first.complete)
        assertEquals(1, first.remainingBooks)
        assertEquals(1, first.updatedFiles)
        assertEquals(oldIdeas, file("ni_a.json").readText())
        assertNotNull(cached("nt_a.json")["marks"])
        failing = false
        val second = WereadNoteSync(request, folder.root) { now + 100 }.sync()!!
        assertTrue(second.complete)
        assertEquals(1, marksCalls)
        assertEquals(1, second.updatedFiles)
        assertEquals(
            "new",
            cached("ni_a.json")["ideas"]!!
                .jsonArray[0]
                .jsonObject["reviewId"]!!
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `fresh bodies skip network until sort changes or ttl expires`() {
        var sort = 30
        var time = now
        var bodies = 0
        val request =
            WereadRequest { api, _ ->
                when (api) {
                    "/user/notebooks" -> overview(book("a", sort))
                    "/book/bookmarklist" -> {
                        bodies++
                        emptyMarks()
                    }
                    "/review/list/mine" -> {
                        bodies++
                        emptyIdeas()
                    }
                    else -> error(api)
                }
            }

        fun sync() = WereadNoteSync(request, folder.root) { time }.sync()!!
        assertTrue(sync().complete)
        assertEquals(0, sync().updatedFiles)
        assertEquals(2, bodies)
        sort++
        sync()
        assertEquals(4, bodies)
        time += 7 * 60 * 60 * 1000L
        sync()
        assertEquals(6, bodies)
    }

    @Test
    fun `midway offline stops requests immediately and next round resumes checkpointed books`() {
        for (failAtIdeas in listOf(false, true)) {
            val root = folder.newFolder(if (failAtIdeas) "ideas-failure" else "marks-failure")
            var offline = true
            val requests = mutableListOf<Pair<String, String>>()
            val failedApi = if (failAtIdeas) "/review/list/mine" else "/book/bookmarklist"
            val request =
                WereadRequest { api, business ->
                    if (api == "/user/notebooks") {
                        overview(listOf(book("a", 30), book("b", 20), book("c", 10)).joinToString(","))
                    } else {
                        val id = (business["bookId"] ?: business["bookid"])!!.jsonPrimitive.content
                        requests += api to id
                        when {
                            offline && api == failedApi && id == "b" -> null
                            api == "/book/bookmarklist" -> emptyMarks()
                            api == "/review/list/mine" -> emptyIdeas()
                            else -> error(api)
                        }
                    }
                }
            val first = WereadNoteSync(request, root) { now }.sync()!!
            assertFalse(first.complete)
            assertEquals(2, first.remainingBooks)
            assertEquals(if (failAtIdeas) 4 else 3, requests.size)
            assertEquals(failedApi to "b", requests.last())
            assertTrue(requests.none { it.second == "c" })
            assertTrue(File(root, "nt_a.json").exists())
            assertTrue(File(root, "ni_a.json").exists())
            assertFalse(File(root, "ni_b.json").exists())

            offline = false
            requests.clear()
            val second = WereadNoteSync(request, root) { now + 100 }.sync()!!
            assertTrue(second.complete)
            assertEquals(0, second.remainingBooks)
            assertEquals(if (failAtIdeas) 3 else 4, requests.size)
            assertEquals(failedApi to "b", requests.first())
            assertTrue(requests.none { it.second == "a" })
            assertTrue(File(root, "ni_c.json").exists())
        }
    }

    @Test
    fun `old flat index metadata survives missing nested book`() {
        file("nt_index.json").writeText("""[{"bookId":"a","title":"旧书名","author":"作者","charCount":123}]""")
        val batch =
            WereadNoteSync(
                WereadRequest { api, _ ->
                    when (api) {
                        "/user/notebooks" ->
                            overview(
                                """{"bookId":"a","sort":2,"title":"扁平书名","readingProgress":100}""",
                            )
                        "/book/bookmarklist" -> emptyMarks()
                        "/review/list/mine" -> emptyIdeas()
                        else -> error(api)
                    }
                },
                folder.root,
            ) { now }.sync()!!
        val book = json(batch.indexJson)["books"]!!.jsonArray[0].jsonObject
        assertEquals("扁平书名", book["title"]!!.jsonPrimitive.content)
        assertEquals("作者", book["author"]!!.jsonPrimitive.content)
        assertEquals("123", book["charCount"].toString())
        assertEquals("100", book["progress"].toString())
    }
}
