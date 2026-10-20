package com.shuiyu.octoberrain.feature.settings

import android.os.Handler
import android.os.Looper
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.RefreshPolicy
import com.shuiyu.octoberrain.core.common.RefreshReason
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.render.RefreshScheduler

/**
 * 快捷托盘设置页面。
 *
 * 行控件全部由 [SettingsCatalog.trayGroups] 的声明生成；
 * 本页额外维护「距上次整刷」摘要行，并按固定周期刷新其文字。
 */
class TrayPrefsScreen : SettingsBaseScreen("快捷托盘") {
    /** 摘要文字的刷新调度器，运行在主线程。 */
    private val summaryHandler = Handler(Looper.getMainLooper())

    /** 距上次整刷的摘要行，页面重建时替换为新行。 */
    private var summaryRow: SettingsRow? = null

    /** 最近一次写入摘要行的文字，用于跳过内容未变化的写入。 */
    private var summaryText: String = ""

    /** 摘要刷新任务：更新时间摘要后按固定周期重新投递。 */
    private val summaryTick =
        object : Runnable {
            override fun run() {
                updateSummary()
                summaryHandler.postDelayed(this, SUMMARY_INTERVAL_MS)
            }
        }

    override fun build(
        page: InkPage,
        config: LauncherConfig,
    ) {
        SettingsSpecRenderer.renderPage(
            page = page,
            groups = SettingsCatalog.trayGroups,
            config = config,
            sink = settingEffectSink(),
            actions = trayActions(),
            customs = trayCustoms(),
        )
    }

    /** 托盘页的动作表，键为设置项标识。 */
    private fun trayActions(): Map<String, () -> Unit> =
        mapOf(
            "tray.refreshNow" to { requestManualRefresh() },
        )

    /** 托盘页的自定义行表，键为声明中的自定义行标识。 */
    private fun trayCustoms(): Map<String, (SettingsSection) -> Unit> =
        mapOf(
            "tray.refreshSummary" to { section ->
                val summary = refreshSummary()
                summaryText = summary
                summaryRow = section.info("距上次整刷", summary)
                startSummaryTicker()
            },
        )

    /** 立即整屏刷新，并在闪烁结束后更新摘要。 */
    private fun requestManualRefresh() {
        val policy = runCatching { AppServices.refresh as? RefreshPolicy }.getOrNull()
        if (policy != null) {
            runCatching { policy.requestFullRefresh(RefreshReason.MANUAL) }
        } else {
            RefreshScheduler.requestFullRefresh(RefreshReason.MANUAL)
        }
        // 两次闪烁共约三百毫秒，稍后刷新摘要以显示新的整刷时刻
        summaryHandler.removeCallbacks(summaryTick)
        summaryHandler.postDelayed(summaryTick, MANUAL_SUMMARY_DELAY_MS)
    }

    /** 启动摘要刷新任务；页面重建时先撤销旧任务，避免多个任务同时写入。 */
    private fun startSummaryTicker() {
        summaryHandler.removeCallbacks(summaryTick)
        summaryHandler.postDelayed(summaryTick, SUMMARY_INTERVAL_MS)
    }

    /** 更新摘要行文字；内容未变化时不写入，避免重复重绘。 */
    private fun updateSummary() {
        val row = summaryRow ?: return
        val text = refreshSummary()
        if (text == summaryText) return
        summaryText = text
        row.setValue(text)
    }

    /**
     * 生成距上次整屏刷新的时间摘要。
     *
     * 优先读取刷新策略记录的完成时刻，策略未装配时退回调度器的记录。
     *
     * @return 形如“3 分钟”或“不到 1 分钟”的摘要文字，从未整刷时返回“尚未整刷”。
     */
    private fun refreshSummary(): String {
        val millis =
            runCatching {
                (AppServices.refresh as? RefreshPolicy)?.millisSinceFullRefresh()
                    ?: RefreshScheduler.millisSinceFullRefresh()
            }.getOrDefault(Long.MAX_VALUE)
        if (millis == Long.MAX_VALUE || millis < 0L) return "尚未整刷"
        val minutes = millis / 60_000L
        return if (minutes <= 0L) "不到 1 分钟" else "$minutes 分钟"
    }

    override fun onDestroy() {
        summaryHandler.removeCallbacksAndMessages(null)
        summaryRow = null
        super.onDestroy()
    }

    private companion object {
        /** 摘要文字的刷新周期，单位毫秒。 */
        const val SUMMARY_INTERVAL_MS = 10_000L

        /** 手动整刷后提前刷新摘要的延迟，单位毫秒，需大于两次闪烁的总时长。 */
        const val MANUAL_SUMMARY_DELAY_MS = 600L
    }
}
