package com.shuiyu.octoberrain.feature.content

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.UsageEntry
import com.shuiyu.octoberrain.core.device.PermissionCenter
import com.shuiyu.octoberrain.core.device.UsageAccessChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * 最近任务条目。
 *
 * 参数 packageName 为应用包名；label 为应用显示名称；
 * lastUsedAt 为该应用最近一次进入前台的毫秒时间戳。
 */
data class RecentTask(
    val packageName: String,
    val label: String,
    val lastUsedAt: Long,
)

/**
 * 使用统计区间。
 *
 * 三个区间分别对应当日零点、本周一零点与本月初零点起的用量，
 * 标签直接用于页面上的区间切换按钮。
 */
enum class UsageSpan(
    val label: String,
) {
    TODAY("今天"),
    WEEK("本周"),
    MONTH("本月"),
}

/**
 * 单个区间的使用统计结果。
 *
 * 参数 span 为统计区间；totalMinutes 为区间内全部应用的前台时长合计；
 * entries 为按时长降序排列的应用条目，最多 20 项。
 */
data class UsageSummary(
    val span: UsageSpan,
    val totalMinutes: Int,
    val entries: List<UsageEntry>,
)

/**
 * 最近任务与使用统计的数据来源。
 *
 * 通过 UsageStatsManager 读取前台事件与聚合用量：最近任务取自最近 24 小时的
 * MOVE_TO_FOREGROUND 事件并按包名去重，使用统计取自三个区间的 totalTimeInForeground。
 * 结果在内存中缓存五分钟，读取失败或无权限时返回空结果，页面据此展示空状态与授权入口。
 */
class RecentsRepository(
    context: Context,
) {
    private companion object {
        private const val TAG = "最近任务"

        /** 内存缓存有效期，五分钟。 */
        private const val CACHE_TTL_MS = 5L * 60L * 1000L

        /** 最近任务的回溯窗口，24 小时。 */
        private const val RECENT_WINDOW_MS = 24L * 60L * 60L * 1000L
    }

    private val appContext: Context = context.applicationContext ?: context
    private val cacheLock = Any()
    private var recentCache: Cached<List<RecentTask>>? = null
    private val usageCache = HashMap<UsageSpan, Cached<UsageSummary>>()

    /** 带时间戳的缓存条目。 */
    private data class Cached<T>(
        val storedAt: Long,
        val value: T,
    )

    /**
     * 判断是否具备读取使用记录的权限。
     *
     * 以权限中心的快照为主；权限服务尚未装配时退回 AppOps 检查，
     * 两种途径都不可用时按无权限处理，避免页面直接抛异常。
     */
    fun canRead(): Boolean =
        runCatching {
            AppServices.permissions.snapshot()[PermissionCenter.KEY_USAGE_ACCESS] == true
        }.getOrElse { appOpsAllowed() }

    /**
     * 读取最近任务列表。
     *
     * 参数 limit 为最大条目数。读取最近 24 小时的前台事件，按包名保留最后一次时间，
     * 过滤本应用与无启动入口的应用，按时间降序返回。
     */
    @Suppress("DEPRECATION")
    suspend fun recentTasks(limit: Int = MAX_ENTRIES): List<RecentTask> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            cachedRecent(now)?.let { return@withContext it.take(limit) }
            if (!canRead()) return@withContext emptyList()
            val manager = usageManager() ?: return@withContext emptyList()
            val events =
                runCatching {
                    manager.queryEvents(now - RECENT_WINDOW_MS, now)
                }.getOrElse {
                    InkLog.w(TAG, "读取前台事件失败：${it.message}")
                    return@withContext emptyList()
                } ?: return@withContext emptyList()
            val lastUsed = HashMap<String, Long>()
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND) continue
                val packageName = event.packageName ?: continue
                if (packageName == appContext.packageName) continue
                val previous = lastUsed[packageName] ?: 0L
                if (event.timeStamp > previous) lastUsed[packageName] = event.timeStamp
            }
            val tasks =
                launchablePackages(lastUsed.keys)
                    .map { packageName ->
                        RecentTask(
                            packageName = packageName,
                            label = labelOf(packageName),
                            lastUsedAt = lastUsed.getValue(packageName),
                        )
                    }.sortedByDescending { it.lastUsedAt }
            storeRecent(tasks, now)
            tasks.take(limit)
        }

    /**
     * 读取指定区间的使用统计。
     *
     * 参数 span 为统计区间，limit 为最大条目数。区间起点分别为当日零点、
     * 本周一零点与本月初零点；查询间隔类型按区间选择日、周、月三种聚合。
     */
    suspend fun usage(
        span: UsageSpan,
        limit: Int = MAX_ENTRIES,
    ): UsageSummary =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            cachedUsage(span, now)?.let { cached ->
                return@withContext cached.copy(entries = cached.entries.take(limit))
            }
            val empty = UsageSummary(span, 0, emptyList())
            if (!canRead()) return@withContext empty
            val manager = usageManager() ?: return@withContext empty
            val begin = startOf(span, now)
            val interval =
                when (span) {
                    UsageSpan.TODAY -> UsageStatsManager.INTERVAL_DAILY
                    UsageSpan.WEEK -> UsageStatsManager.INTERVAL_WEEKLY
                    UsageSpan.MONTH -> UsageStatsManager.INTERVAL_MONTHLY
                }
            val statistics =
                runCatching {
                    manager.queryUsageStats(interval, begin, now)
                }.getOrElse {
                    InkLog.w(TAG, "读取区间用量失败：${it.message}")
                    return@withContext empty
                } ?: emptyList()
            // 同一区间内系统可能返回多个统计桶，同一包名取最大值，避免时长被重复累计
            val durations = HashMap<String, Long>()
            for (statistic in statistics) {
                val packageName = statistic.packageName ?: continue
                if (packageName == appContext.packageName) continue
                val duration = statistic.totalTimeInForeground
                if (duration <= 0L) continue
                val existing = durations[packageName] ?: 0L
                if (duration > existing) durations[packageName] = duration
            }
            val packages = launchablePackages(durations.keys)
            val totalMillis = packages.sumOf { packageName -> durations.getValue(packageName) }
            val entries =
                packages
                    .map { packageName ->
                        UsageEntry(
                            label = labelOf(packageName),
                            packageName = packageName,
                            minutes = minutesOf(durations.getValue(packageName)),
                        )
                    }.sortedByDescending { it.minutes }
                    .take(limit)
            val summary = UsageSummary(span, minutesOf(totalMillis), entries)
            storeUsage(span, summary, now)
            summary
        }

    /** 清空内存缓存，供页面上的刷新操作调用。 */
    fun invalidate() {
        synchronized(cacheLock) {
            recentCache = null
            usageCache.clear()
        }
    }

    /** 取系统使用统计服务，设备缺失该服务时返回 null。 */
    private fun usageManager(): UsageStatsManager? =
        appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /** 通过核心模块的判定函数检查使用记录权限，供权限服务尚未装配的早期阶段使用。 */
    private fun appOpsAllowed(): Boolean = UsageAccessChecker.isGranted(appContext)

    /** 过滤出可启动的应用包名，去掉本应用与没有启动入口的包。 */
    private fun launchablePackages(candidates: Collection<String>): List<String> {
        val packageManager = appContext.packageManager
        return candidates.filter { packageName ->
            packageName != appContext.packageName &&
                runCatching {
                    packageManager.getLaunchIntentForPackage(packageName) != null
                }.getOrElse { false }
        }
    }

    /** 读取应用显示名称，失败时回退到包名。 */
    private fun labelOf(packageName: String): String =
        runCatching {
            val packageManager = appContext.packageManager
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrElse { packageName }

    /** 把前台毫秒数换算为分钟，非零时长至少计一分钟。 */
    private fun minutesOf(durationMs: Long): Int = ((durationMs + 30_000L) / 60_000L).toInt()

    /** 计算区间起点：当日零点、本周一零点或本月初零点。 */
    private fun startOf(
        span: UsageSpan,
        now: Long,
    ): Long {
        val calendar =
            Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        when (span) {
            UsageSpan.TODAY -> Unit
            UsageSpan.WEEK -> {
                // 以周一为一周起点，回退到本周一
                val offset = (calendar.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
                calendar.add(Calendar.DAY_OF_MONTH, -offset)
            }
            UsageSpan.MONTH -> calendar.set(Calendar.DAY_OF_MONTH, 1)
        }
        return calendar.timeInMillis
    }

    /** 读取最近的缓存条目，过期时清除。 */
    private fun cachedRecent(now: Long): List<RecentTask>? =
        synchronized(cacheLock) {
            val entry = recentCache
            if (entry == null) {
                null
            } else if (now - entry.storedAt > CACHE_TTL_MS) {
                recentCache = null
                null
            } else {
                entry.value
            }
        }

    /** 写入最近任务缓存。 */
    private fun storeRecent(
        tasks: List<RecentTask>,
        now: Long,
    ) {
        synchronized(cacheLock) { recentCache = Cached(now, tasks) }
    }

    /** 读取指定区间的缓存条目，过期时清除。 */
    private fun cachedUsage(
        span: UsageSpan,
        now: Long,
    ): UsageSummary? =
        synchronized(cacheLock) {
            val entry = usageCache[span]
            if (entry == null) {
                null
            } else if (now - entry.storedAt > CACHE_TTL_MS) {
                usageCache.remove(span)
                null
            } else {
                entry.value
            }
        }

    /** 写入指定区间的使用统计缓存。 */
    private fun storeUsage(
        span: UsageSpan,
        summary: UsageSummary,
        now: Long,
    ) {
        synchronized(cacheLock) { usageCache[span] = Cached(now, summary) }
    }
}

/** 最近任务与使用统计的默认最大条目数。 */
private const val MAX_ENTRIES = 20
