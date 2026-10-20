package com.shuiyu.octoberrain.feature.content.data

import com.inkread.weekread.kernel.PeriodSnapshot
import com.inkread.weekread.kernel.load.KernelDataLoader
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.WereadBillSnapshot
import com.shuiyu.octoberrain.core.common.WereadFingerprintSnapshot
import com.shuiyu.octoberrain.core.common.WereadRankEntry
import com.shuiyu.octoberrain.core.common.WereadShelfItem
import java.io.File

/**
 * 微读墨记原盘数据读取。
 *
 * 十雨桌面为微读墨记做原生适配：卡片与负一屏直接读微读写入 filesDir 的同一份数据，
 * 与微读自身渲染共用一份来源，避免出现两套统计。数据缺失或格式变化时返回空结果，
 * 由调用方决定是否退回网络读取。
 *
 * @param root 微读数据根目录，通常为 filesDir/weread
 */
class KernelWereadReader(
    root: File,
) {
    private val loader = KernelDataLoader(root)

    /**
     * 读取书架。
     *
     * @param limit 条目上限，非正数表示不限制
     * @return 书架条目，按微读原盘顺序
     */
    fun shelf(limit: Int): List<WereadShelfItem> {
        val books = runCatching { loader.shelf()?.books }.getOrNull().orEmpty()
        val items =
            books.map { book ->
                WereadShelfItem(
                    bookId = book.bookId.orEmpty(),
                    title = book.title.orEmpty(),
                    author = book.author.orEmpty(),
                    coverPath = book.coverUrl?.takeIf { it.isNotBlank() },
                    progress = book.progress.coerceIn(0, 100) / 100f,
                    deepLink = null,
                )
            }
        return if (limit <= 0 || limit >= items.size) items else items.take(limit)
    }

    /**
     * 读取月度账单。
     *
     * 本月时长取月度周期的总秒数，本月逐日为每日秒数换算的分钟数；
     * 上月时长由月度周期的环比推得，缺失环比时记零。
     *
     * @return 月度账单快照，数据缺失时返回 null
     */
    fun bill(): WereadBillSnapshot? {
        val month = period(MODE_MONTH) ?: return null
        val minutes = month.totalSec / SECONDS_PER_MINUTE
        return WereadBillSnapshot(
            monthMinutes = minutes,
            lastMonthMinutes = previousMinutes(minutes, month.compare),
            days = month.daySec?.map { it / SECONDS_PER_MINUTE }.orEmpty(),
        )
    }

    /**
     * 读取阅读排行。
     *
     * 与微读墨台排行一致，按书架内的阅读时长降序取前若干本，进度换算为 0 到 1。
     *
     * @param limit 条目上限，非正数按一条处理
     * @return 排行条目
     */
    fun rank(limit: Int): List<WereadRankEntry> {
        val count = limit.coerceAtLeast(1)
        val books = runCatching { loader.shelf()?.books }.getOrNull().orEmpty()
        return books
            .sortedByDescending { it.readingSec }
            .take(count)
            .map { book ->
                WereadRankEntry(
                    title = book.title.orEmpty(),
                    author = book.author.orEmpty(),
                    progress = book.progress.coerceIn(0, 100) / 100f,
                )
            }
    }

    /**
     * 读取阅读指纹。
     *
     * 累计时长与有效天数取自总览周期，自然日均取自总览的平均秒数，
     * 当月逐日取自月度周期。
     *
     * @return 指纹快照，两个周期都缺失时返回 null
     */
    fun fingerprint(): WereadFingerprintSnapshot? {
        val overall = period(MODE_OVERALL)
        val month = period(MODE_MONTH)
        if (overall == null && month == null) return null
        val totalMinutes = (overall?.totalSec ?: 0) / SECONDS_PER_MINUTE
        val averageMinutes =
            overall?.avgSec?.div(SECONDS_PER_MINUTE)
                ?: totalMinutes / (overall?.readDays?.coerceAtLeast(1) ?: 1)
        return WereadFingerprintSnapshot(
            totalMinutes = totalMinutes,
            readDays = overall?.readDays ?: 0,
            dayAverageMinutes = averageMinutes,
            days = month?.daySec?.map { it / SECONDS_PER_MINUTE }.orEmpty(),
        )
    }

    /**
     * 读取指定周期的统计快照。
     *
     * @param mode 周期标识，取值 week、month、year、overall
     * @return 周期快照，缺失时返回 null
     */
    private fun period(mode: String): PeriodSnapshot? =
        runCatching { loader.period(mode) }.getOrElse { error ->
            InkLog.d("内容数据源", "微读周期 $mode 读取失败：${error.message}")
            null
        }

    /**
     * 由本月分钟数与环比推算上月分钟数。
     *
     * @param minutes 本月分钟数
     * @param compare 环比比值，正数表示本月增加
     * @return 上月分钟数，环比缺失时返回零
     */
    private fun previousMinutes(
        minutes: Int,
        compare: Double?,
    ): Int {
        val ratio = 1.0 + (compare ?: return 0)
        return if (ratio <= 0.0) 0 else (minutes / ratio).toInt()
    }

    private companion object {
        /** 每分钟秒数。 */
        private const val SECONDS_PER_MINUTE = 60

        /** 月度周期标识，与内核 PeriodCalendar.MONTHLY 一致。 */
        private const val MODE_MONTH = "monthly"

        /** 总览周期标识，与内核 PeriodCalendar.OVERALL 一致。 */
        private const val MODE_OVERALL = "overall"
    }
}
