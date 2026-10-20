package com.shuiyu.octoberrain.feature.content.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.UsageEntry
import com.shuiyu.octoberrain.core.device.PermissionCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * 今日应用使用时长统计。
 *
 * 读取 UsageStatsManager 的当日聚合数据，按前台时长降序返回前八个应用。
 * 使用统计开关关闭或使用记录权限缺失时返回空列表，页面据此展示授权提示。
 */
class UsageRepository(
    private val context: Context,
) {
    companion object {
        private const val TAG = "使用统计"
        private const val MAX_ENTRIES = 8
        private const val TRACKED_KEY = PermissionCenter.KEY_USAGE_ACCESS
    }

    /** 是否具备读取使用记录的权限，读取失败按无权限处理。 */
    fun hasAccess(): Boolean =
        runCatching {
            AppServices.permissions.snapshot()[TRACKED_KEY] == true
        }.getOrElse { false }

    /** 读取今日使用时长，按分钟降序返回。 */
    suspend fun today(): List<UsageEntry> =
        withContext(Dispatchers.IO) {
            if (!ContentPaths.integrations().usageEnabled) return@withContext emptyList()
            if (!hasAccess()) return@withContext emptyList()
            val manager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                    ?: return@withContext emptyList()
            val end = System.currentTimeMillis()
            val start = startOfToday()
            val statistics =
                runCatching {
                    manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                }.getOrElse {
                    InkLog.w(TAG, "读取使用记录失败：${it.message}")
                    return@withContext emptyList()
                } ?: return@withContext emptyList()
            val durations = HashMap<String, Long>()
            for (statistic in statistics) {
                val packageName = statistic.packageName ?: continue
                if (packageName == context.packageName) continue
                val duration = statistic.totalTimeInForeground
                if (duration <= 0L) continue
                val existing = durations[packageName] ?: 0L
                if (duration > existing) durations[packageName] = duration
            }
            val packageManager = context.packageManager
            durations.entries
                .map { (packageName, duration) ->
                    UsageEntry(
                        label = labelOf(packageManager, packageName),
                        packageName = packageName,
                        minutes = minutesOf(duration),
                    )
                }.filter { it.minutes > 0 }
                .sortedByDescending { it.minutes }
                .take(MAX_ENTRIES)
        }

    /** 把前台毫秒数换算为分钟，非零时长至少计一分钟。 */
    private fun minutesOf(durationMs: Long): Int = ((durationMs + 30_000L) / 60_000L).toInt()

    /** 读取应用显示名称，失败时回退到包名。 */
    private fun labelOf(
        packageManager: PackageManager,
        packageName: String,
    ): String =
        runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrElse { packageName }

    /** 今天零点。 */
    private fun startOfToday(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
