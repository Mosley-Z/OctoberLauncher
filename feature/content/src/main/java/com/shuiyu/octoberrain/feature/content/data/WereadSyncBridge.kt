package com.shuiyu.octoberrain.feature.content.data

import com.inkread.weekread.kernel.model.BillSheet
import com.shuiyu.octoberrain.core.wereadsync.WereadAssetBook
import com.shuiyu.octoberrain.core.wereadsync.WereadNoteBatch
import com.shuiyu.octoberrain.core.wereadsync.WereadShelfBook
import com.shuiyu.octoberrain.core.wereadsync.WereadSyncProvider
import org.json.JSONObject

/**
 * 用微读网关响应为数据目录同步提供内容。
 *
 * 只做字段搬运：网络协议由 [WereadClient] 负责，写入格式由 `core:weread-sync` 负责，
 * 这里把网关原始 JSON 转成同步模型。资产、账单与笔记的原版接口尚未接入，对应方法返回
 * 空值，待接口格式确认后补齐。
 *
 * @param client 微读网关客户端
 */
class WereadSyncBridge(
    private val client: WereadClient,
) : WereadSyncProvider {
    override suspend fun shelf(): List<WereadShelfBook>? = client.shelfRaw()?.let { raw -> parseShelf(raw) }

    override suspend fun stats(
        mode: String,
        baseTimeSec: Long,
    ): String? = client.statsRaw(mode, baseTimeSec)

    override suspend fun assets(): List<WereadAssetBook>? = null

    override suspend fun bill(
        mode: String,
        periodStartSec: Long,
        statsRaw: String,
    ): String? =
        runCatching { BillSheet.buildBillSheet(statsRaw, mode, periodStartSec) }
            .getOrNull()
            ?.takeIf { json -> json.contains("\"items\"") }

    override suspend fun notes(): WereadNoteBatch? = null

    /**
     * 解析书架原始响应。
     *
     * 微读 `shelf.json` 的进度是千分比，超过一百时除以十换算为百分比；阅读时长优先取
     * `readingTime`，缺失时取 `readTimeSec`；完成时刻兼容秒与毫秒两种单位。
     *
     * @param raw 网关原始 JSON 文本
     * @return 书架条目，结构不合法时返回空列表
     */
    private fun parseShelf(raw: String): List<WereadShelfBook> {
        val books =
            runCatching { JSONObject(raw).optJSONArray("books") }
                .getOrNull()
                ?: return emptyList()
        val result = ArrayList<WereadShelfBook>(books.length())
        for (index in 0 until books.length()) {
            books.optJSONObject(index)?.let { item ->
                val bookId = item.optString("bookId").ifBlank { item.optString("book_id") }
                if (bookId.isNotBlank()) {
                    result.add(toShelfBook(bookId, item))
                }
            }
        }
        return result
    }

    /** 把单本书的网关字段映射为同步模型。 */
    private fun toShelfBook(
        bookId: String,
        item: JSONObject,
    ): WereadShelfBook {
        val finishTime = item.optLong("finishTime", 0L)
        return WereadShelfBook(
            bookId = bookId,
            title = item.optString("title").ifBlank { "未命名书籍" },
            author = item.optString("author"),
            coverUrl = item.optString("cover"),
            readingSeconds = readingSeconds(item),
            progress = percentOf(item.optInt("progress", 0)),
            finished = finishTime > 0L || item.optInt("finishReading", 0) == 1,
            finishTimeMillis = millisOf(finishTime),
        )
    }

    /**
     * 读取阅读时长。
     *
     * 网关不同接口对同一含义使用 `readingTime`、`readTime`、`readTimeSec` 三种字段名，
     * 单位也分秒与毫秒两种；超过十万的取值按毫秒换算为秒。
     */
    private fun readingSeconds(item: JSONObject): Int {
        val candidates = listOf(READING_TIME, READ_TIME, READ_TIME_SEC, READING_SEC)
        val raw = candidates.firstNotNullOfOrNull { name -> item.optInt(name, 0).takeIf { it > 0 } }
        return when {
            raw == null -> 0
            raw > MILLIS_THRESHOLD -> raw / 1000
            else -> raw
        }
    }

    /** 把千分比进度换算为百分比。 */
    private fun percentOf(raw: Int): Int =
        when {
            raw < 0 -> -1
            raw > 100 -> (raw / 10).coerceIn(0, 100)
            else -> raw
        }

    /** 把秒级完成时刻换算为毫秒。 */
    private fun millisOf(value: Long): Long = if (value in 1..SECONDS_UPPER_BOUND) value * 1000L else value

    private companion object {
        /** 秒级时间戳的上界，超过一年即视为毫秒。 */
        const val SECONDS_UPPER_BOUND: Long = 99_999_999_999L

        /** 时长字段名：累计阅读时长。 */
        const val READING_TIME: String = "readingTime"

        /** 时长字段名：本期阅读时长。 */
        const val READ_TIME: String = "readTime"

        /** 时长字段名：秒级阅读时长。 */
        const val READ_TIME_SEC: String = "readTimeSec"

        /** 时长字段名：秒级累计时长。 */
        const val READING_SEC: String = "readingSec"

        /** 超过该值的时长按毫秒换算。 */
        const val MILLIS_THRESHOLD: Int = 100_000
    }
}
