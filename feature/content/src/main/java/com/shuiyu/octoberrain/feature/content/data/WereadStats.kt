package com.shuiyu.octoberrain.feature.content.data

/** 阅读统计周期，与 `/readdata/detail` 的 mode 参数一一对应。 */
enum class WereadPeriod(
    val apiMode: String,
) {
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    ANNUALLY("annually"),
    OVERALL("overall"),
}

/**
 * 统计周期内的一个分桶。
 *
 * @property startMillis 分桶起始时间戳，单位毫秒
 * @property seconds 该分桶内的阅读与收听时长，单位秒
 */
data class WereadBucket(
    val startMillis: Long,
    val seconds: Long,
)

/**
 * 单个周期的阅读统计结果，所有时长字段单位均为秒。
 *
 * @property period 统计周期
 * @property baseTimeSeconds 服务端返回的周期起点，单位秒；总计模式为 0
 * @property totalSeconds 周期总时长
 * @property readDays 有效阅读天数
 * @property dayAverageSeconds 自然日均时长
 * @property buckets 按时间升序排列的分桶数据
 */
data class WereadStats(
    val period: WereadPeriod,
    val baseTimeSeconds: Long,
    val totalSeconds: Long,
    val readDays: Int,
    val dayAverageSeconds: Long,
    val buckets: List<WereadBucket>,
)

/**
 * 单条笔记，字段缺失时降级为空字符串。
 *
 * @property bookId 书籍标识
 * @property bookTitle 书名
 * @property chapterTitle 章节名
 * @property text 划线原文
 * @property thought 个人想法或点评
 * @property createdAt 创建时间，单位毫秒
 * @property deepLink 微信读书深链
 */
data class WereadNote(
    val bookId: String = "",
    val bookTitle: String = "",
    val chapterTitle: String = "",
    val text: String = "",
    val thought: String = "",
    val createdAt: Long = 0L,
    val deepLink: String = "",
)

/**
 * 笔记分页结果。
 *
 * @property notes 当前页笔记，按创建时间降序排列
 * @property nextCursor 下一页游标；无更多数据时为 null
 * @property total 笔记本概览报告的总笔记数
 */
data class WereadNotePage(
    val notes: List<WereadNote> = emptyList(),
    val nextCursor: String? = null,
    val total: Int = 0,
)
