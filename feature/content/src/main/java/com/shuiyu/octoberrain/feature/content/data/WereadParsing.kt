package com.shuiyu.octoberrain.feature.content.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

private const val TIMESTAMP_MILLIS_THRESHOLD = 100_000_000_000L

/** 笔记概览中的单本书条目，用于翻页与逐书拉取内容。 */
internal data class NotebookEntry(
    val bookId: String,
    val title: String,
    val sort: Long,
)

/**
 * 微信读书响应的纯逻辑解析。
 *
 * 所有函数只操作 kotlinx.serialization 的 JSON 元素，不触碰 Android 框架，
 * 可在本地 JVM 中直接验证字段兼容与降级行为。
 */
internal object WereadParsing {
    /**
     * 计算统计周期的起点，单位秒。
     *
     * 周按周一起算，月按月初起算，年按年初起算，总计固定为 0；
     * baseTime 为 0 时使用 referenceSeconds 所在周期。
     */
    fun periodStartSeconds(
        period: WereadPeriod,
        referenceSeconds: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        if (period == WereadPeriod.OVERALL) return 0L
        val moment = Instant.ofEpochSecond(referenceSeconds).atZone(zone)
        val start =
            when (period) {
                WereadPeriod.WEEKLY ->
                    moment.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                WereadPeriod.MONTHLY -> moment.withDayOfMonth(1)
                WereadPeriod.ANNUALLY -> moment.withDayOfYear(1)
                WereadPeriod.OVERALL -> moment
            }
        return start
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
            .epochSecond
    }

    /** 解析统计响应，字段缺失按零处理。 */
    fun stats(
        period: WereadPeriod,
        payload: JsonObject,
        fallbackBaseSeconds: Long,
    ): WereadStats {
        val base = payload.longOrZero("baseTime").takeIf { it > 0L } ?: fallbackBaseSeconds
        return WereadStats(
            period = period,
            baseTimeSeconds = base,
            totalSeconds = normalizeTotal(payload.longOrZero("totalReadTime")),
            readDays = payload.intOrZero("readDays"),
            dayAverageSeconds = normalizeTotal(payload.longOrZero("dayAverageReadTime")),
            buckets = buckets(period, payload, base),
        )
    }

    /** 解析笔记本概览中的书籍条目。 */
    fun notebookEntries(payload: JsonObject): List<NotebookEntry> {
        val books = payload.arrayOrNull("books") ?: return emptyList()
        return books.mapNotNull { element ->
            val book = element as? JsonObject ?: return@mapNotNull null
            val id = book.stringOrEmpty("bookId").ifBlank { book.stringOrEmpty("book_id") }
            if (id.isBlank()) return@mapNotNull null
            NotebookEntry(
                bookId = id,
                title =
                    book
                        .objOrNull("book")
                        ?.stringOrEmpty("title")
                        .orEmpty()
                        .ifBlank { book.stringOrEmpty("bookTitle") },
                sort = book.longOrZero("sort"),
            )
        }
    }

    /** 判断笔记本概览是否还有下一页。 */
    fun hasMore(payload: JsonObject): Boolean = payload.longOrZero("hasMore") != 0L

    /** 返回笔记本概览报告的总笔记数，缺失时为零。 */
    fun totalNotes(payload: JsonObject): Int = payload.intOrZero("totalNoteCount")

    /** 解析单本书的划线内容与个人想法，缺少章节名或原文时降级为空字符串。 */
    fun notes(
        entry: NotebookEntry,
        highlights: JsonObject?,
        reviews: JsonObject?,
    ): List<WereadNote> {
        val chapters = chapterTitles(highlights)
        val fromMarks = marks(entry, highlights, chapters)
        val fromReviews = thoughts(entry, reviews)
        return (fromMarks + fromReviews).sortedByDescending { it.createdAt }
    }

    /** 生成微信读书阅读跳转链接。 */
    fun deepLink(bookId: String): String = "weread://reading?bId=$bookId"

    /** 统一秒与毫秒时间戳。 */
    fun normalizeTimestamp(value: Long): Long = if (value in 1..TIMESTAMP_MILLIS_THRESHOLD) value * 1000L else value
}

/** 从划线响应中提取章节标识到标题的映射。 */
private fun chapterTitles(payload: JsonObject?): Map<String, String> {
    val chapters = payload?.arrayOrNull("chapters") ?: return emptyMap()
    return chapters
        .mapNotNull { it as? JsonObject }
        .associate { it.longOrZero("chapterUid").toString() to it.stringOrEmpty("title") }
}

/** 解析划线数组。 */
private fun marks(
    entry: NotebookEntry,
    payload: JsonObject?,
    chapters: Map<String, String>,
): List<WereadNote> {
    val array = payload?.arrayOrNull("updated") ?: payload?.arrayOrNull("bookmarks")
    return array?.mapNotNull { element ->
        val mark = element as? JsonObject ?: return@mapNotNull null
        val text = mark.stringOrEmpty("markText").ifBlank { mark.stringOrEmpty("text") }
        if (text.isBlank()) return@mapNotNull null
        WereadNote(
            bookId = entry.bookId,
            bookTitle = entry.title,
            chapterTitle = chapters[mark.longOrZero("chapterUid").toString()].orEmpty(),
            text = text,
            createdAt = WereadParsing.normalizeTimestamp(mark.longOrZero("createTime")),
            deepLink = WereadParsing.deepLink(entry.bookId),
        )
    } ?: emptyList()
}

/** 解析个人想法与点评数组。 */
private fun thoughts(
    entry: NotebookEntry,
    payload: JsonObject?,
): List<WereadNote> {
    val array = payload?.arrayOrNull("reviews") ?: return emptyList()
    return array.mapNotNull { element ->
        val review = (element as? JsonObject)?.objOrNull("review") ?: return@mapNotNull null
        val thought = review.stringOrEmpty("content")
        val original = review.stringOrEmpty("abstract")
        if (thought.isBlank() && original.isBlank()) return@mapNotNull null
        WereadNote(
            bookId = entry.bookId,
            bookTitle = entry.title,
            chapterTitle = review.stringOrEmpty("chapterName"),
            text = original,
            thought = thought,
            createdAt = WereadParsing.normalizeTimestamp(review.longOrZero("createTime")),
            deepLink = WereadParsing.deepLink(entry.bookId),
        )
    }
}

/** 读取字符串字段，缺失或非字符串时返回空串。 */
internal fun JsonObject.stringOrEmpty(key: String): String = (this[key] as? JsonPrimitive)?.content.orEmpty()

/** 读取长整数字段，缺失或不可解析时返回零。 */
internal fun JsonObject.longOrZero(key: String): Long = (this[key] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L

/** 读取整数字段，缺失或不可解析时返回零。 */
internal fun JsonObject.intOrZero(key: String): Int = (this[key] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0

/** 读取对象字段，缺失或类型不符时返回 null。 */
internal fun JsonObject.objOrNull(key: String): JsonObject? = this[key] as? JsonObject

/** 读取数组字段，缺失或类型不符时返回 null。 */
internal fun JsonObject.arrayOrNull(key: String): JsonArray? = this[key] as? JsonArray
