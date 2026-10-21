package com.shuiyu.octoberrain.core.common

// 微读资产文件中的「最近阅读时间」解析与排序。
//
// 内核书架快照只带书目基础字段，不携带阅读时间；需要「最近在读」顺序时，
// 从原盘 assets.json 取出每本书的 readUpdateTime，再按时间降序排列。
// 微读墨记与十雨桌面两侧都读同一个文件，集中在这里避免两份实现出现顺序差异。

/** 资产文本中书目编号的匹配式。 */
private val BOOK_ID_PATTERN = Regex("\"id\"\\s*:\\s*\"([^\"]*)\"")

/** 资产文本中最近阅读时间的匹配式，取 Unix 秒。 */
private val READ_TIME_PATTERN = Regex("\"readUpdateTime\"\\s*:\\s*(\\d+)")

/**
 * 从资产文件文本解析书目编号与最近阅读时间。
 *
 * 解析采用顺序扫描：每个时间值归属文本中它之前最近的书目编号，避免依赖对象顺序。
 * 字段缺失或数值非法时跳过该项。
 *
 * @param json 资产文件文本
 * @return 书目编号到最近阅读秒数的映射，无有效项时为空
 */
fun parseRecentReadTimes(json: String): Map<String, Long> {
    val ids = BOOK_ID_PATTERN.findAll(json).map { it.range.first to it.groupValues[1] }.toList()
    if (ids.isEmpty()) return emptyMap()
    val times = LinkedHashMap<String, Long>()
    READ_TIME_PATTERN.findAll(json).forEach { match ->
        val seconds = match.groupValues[1].toLongOrNull() ?: return@forEach
        val bookId = ids.lastOrNull { it.first < match.range.first }?.second ?: return@forEach
        times[bookId] = seconds
    }
    return times
}

/**
 * 按最近阅读时间重排书目。
 *
 * 时间新的排在前面，时间相同的保持原相对顺序；映射里没有的书排在已有时读书之后，
 * 同样保持原相对顺序。映射为空时原样返回，避免无数据时打乱内核顺序。
 *
 * @param items 原始书目
 * @param bookIdOf 取书目编号的函数
 * @param times 书目编号到最近阅读秒数的映射
 * @return 排序后的书目
 */
fun <T> sortByRecentRead(
    items: List<T>,
    bookIdOf: (T) -> String,
    times: Map<String, Long>,
): List<T> {
    if (times.isEmpty() || items.size < 2) return items
    return items.sortedByDescending { times[bookIdOf(it)] ?: Long.MIN_VALUE }
}
