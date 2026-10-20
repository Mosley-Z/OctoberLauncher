package com.shuiyu.octoberrain.feature.widgets.kernel

import com.inkread.weekread.kernel.NoteContent
import com.inkread.weekread.kernel.NoteItem
import com.inkread.weekread.kernel.PeriodSnapshot
import com.inkread.weekread.kernel.ShelfBook
import com.inkread.weekread.kernel.ShelfSnapshot
import com.inkread.weekread.kernel.TodoEntry
import com.shuiyu.octoberrain.core.common.WereadNoteSnapshot
import com.shuiyu.octoberrain.core.common.WereadShelfItem
import com.shuiyu.octoberrain.core.common.WereadStatsSnapshot
import com.shuiyu.octoberrain.core.common.WereadTodoItem
import kotlin.math.roundToInt

/**
 * 内核数据映射：把内容服务的数据模型转换为微读内核的公开字段结构。
 *
 * 映射只做字段搬运与单位换算，内容服务没有提供的字段保留内核默认值，
 * 转换过程不触发网络请求，也不构造额外的数据来源。
 */
internal object KernelDataMapper {
    /**
     * 组装书架快照。
     *
     * 排行首本取列表第一项，标识集合由书目编号生成，抓取时刻取当前时间。
     *
     * @param items 内容服务返回的书架条目
     * @return 内核书架快照
     */
    fun shelf(items: List<WereadShelfItem>): ShelfSnapshot {
        val books = items.map(::shelfBook)
        val snapshot = ShelfSnapshot()
        snapshot.books = books
        snapshot.ids = books.map { it.bookId }.toSet()
        snapshot.fetchedAt = System.currentTimeMillis()
        return snapshot
    }

    /**
     * 转换单本书架条目。
     *
     * 内容服务的进度为 0 到 1 的比例，内核按百分数存储，此处乘一百后取整；
     * 阅读秒数与完成标记内容服务未提供，保留零值。
     *
     * @param item 内容服务返回的书架条目
     * @return 内核书架书目
     */
    private fun shelfBook(item: WereadShelfItem): ShelfBook {
        val book = ShelfBook()
        book.bookId = item.bookId
        book.title = item.title
        book.author = item.author
        book.coverUrl = item.coverPath
        book.progress = (item.progress * PERCENT_MAX).roundToInt()
        return book
    }

    /**
     * 装配周期统计快照。
     *
     * 分桶按自然顺序写入秒数数组，周形态补齐七天、月形态补齐三十一天，
     * 使绘制按日期下标取数时不会越界；周期起始时刻与对比值内容服务未提供，保留零值与空值。
     *
     * @param stats 内容服务返回的周期统计，空值表示无数据
     * @param mode 内核统计模式标识
     * @return 内核周期快照，无数据时为 null
     */
    fun period(
        stats: WereadStatsSnapshot?,
        mode: String,
    ): PeriodSnapshot? {
        if (stats == null) return null
        val period = PeriodSnapshot()
        period.mode = mode
        period.fetchedAt = System.currentTimeMillis()
        period.totalSec = stats.totalMinutes * SECONDS_PER_MINUTE
        period.readDays = stats.readDays
        period.avgSec = stats.dayAverageMinutes * SECONDS_PER_MINUTE
        period.dayCount = stats.buckets.size
        period.daySec = daySeconds(stats, mode)
        period.incomplete = stats.buckets.isEmpty()
        return period
    }

    /**
     * 生成逐日秒数数组。
     *
     * @param stats 内容服务返回的周期统计
     * @param mode 内核统计模式标识
     * @return 按日期下标排列的秒数数组
     */
    private fun daySeconds(
        stats: WereadStatsSnapshot,
        mode: String,
    ): IntArray {
        val limit = if (mode == MODE_MONTHLY) MONTH_DAYS else WEEK_DAYS
        val values = IntArray(limit)
        stats.buckets.forEachIndexed { index, bucket ->
            if (index < limit) values[index] = bucket.minutes * SECONDS_PER_MINUTE
        }
        return values
    }

    /**
     * 转换单条笔记。
     *
     * 内核按想法字段区分划线类型，类型标识由想法有无推导；书籍编号与封面等内容
     * 服务未提供，保留空值。
     *
     * @param item 内容服务返回的笔记快照
     * @return 内核笔记条目
     */
    fun note(item: WereadNoteSnapshot): NoteItem {
        val note = NoteItem()
        note.title = item.bookTitle
        note.chapterTitle = item.chapterTitle
        note.markText = item.text
        note.ideaText = item.thought
        note.createTime = item.createdAt
        note.kind = kindOf(item.thought)
        return note
    }

    /**
     * 把内核笔记条目转换为卡片内容结构。
     *
     * @param note 内核笔记条目
     * @return 内核卡片笔记内容
     */
    fun contentOf(note: NoteItem): NoteContent {
        val content = NoteContent()
        content.bookmarkId = note.bookmarkId
        content.bookId = note.bookId
        content.title = note.title
        content.author = note.author
        content.coverUrl = note.coverUrl
        content.markText = note.markText
        content.ideaText = note.ideaText
        content.kind = note.kind
        content.range = note.range
        content.star = note.star
        content.chapterUid = note.chapterUid
        content.chapterIdx = note.chapterIdx
        content.chapterTitle = note.chapterTitle
        content.createTime = note.createTime
        return content
    }

    /**
     * 转换待办列表。
     *
     * 内容服务的标识为字符串，内核按长整型存储，取字符串散列值作为标识；
     * 排序号按列表顺序写入，时间字段内容服务未提供，使用内核的无时间常量。
     *
     * @param items 内容服务返回的待办条目
     * @return 内核待办列表
     */
    fun todo(items: List<WereadTodoItem>): List<TodoEntry> =
        items.mapIndexed { index, item ->
            TodoEntry(item.id.hashCode().toLong(), item.text).apply {
                dateSec = TodoEntry.NO_TIME.toLong()
                done = item.done
                order = index.toLong()
            }
        }

    /**
     * 按想法有无推导笔记类型。
     *
     * @param thought 个人想法
     * @return 划线或想法类型标识
     */
    private fun kindOf(thought: String): String = if (thought.isEmpty()) NoteItem.KIND_MARK else NoteItem.KIND_IDEA

    /** 换算百分比时使用的基数。 */
    private const val PERCENT_MAX: Float = 100f

    /** 分钟到秒的换算系数。 */
    private const val SECONDS_PER_MINUTE: Int = 60

    /** 周形态的日数。 */
    private const val WEEK_DAYS: Int = 7

    /** 月形态的最大日数。 */
    private const val MONTH_DAYS: Int = 31

    /** 月模式标识，与内核周期模式一致。 */
    private const val MODE_MONTHLY: String = "monthly"
}
