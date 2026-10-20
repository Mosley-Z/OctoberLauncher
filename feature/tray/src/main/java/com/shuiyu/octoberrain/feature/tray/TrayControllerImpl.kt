package com.shuiyu.octoberrain.feature.tray

import android.content.Context
import android.view.View
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.TrayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * 快捷托盘控制器：持有托盘覆盖层与状态栏视图，并聚合系统状态变化。
 *
 * 覆盖层由宿主在 Activity 中创建；无障碍服务也会通过 [createOverlay] 创建一份，
 * 因此这里以弱引用记录全部实例，开合时统一切换可见性。
 * 监听使用应用级协程，视图销毁后弱引用自然失效。
 */
class TrayControllerImpl(
    context: Context,
) : TrayController {
    private val appContext = context.applicationContext

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val statusBars = mutableListOf<WeakReference<StatusBarView>>()

    private val panels = mutableListOf<WeakReference<TrayPanelView>>()

    private var open = false

    override val isOpen: Boolean get() = open

    init {
        observe()
    }

    override fun createOverlay(context: Context): View {
        val panel = TrayPanelView(context, this)
        panel.visibility = if (open) View.VISIBLE else View.GONE
        panels.add(WeakReference(panel))
        return panel
    }

    override fun createStatusBar(context: Context): View {
        val bar = StatusBarView(context)
        statusBars.add(WeakReference(bar))
        return bar
    }

    override fun open() {
        if (open) return
        val enabled = runCatching { AppServices.config.state.value.tray.enabled }.getOrDefault(true)
        if (!enabled) return
        open = true
        alivePanels().forEach {
            it.visibility = View.VISIBLE
            it.refresh()
        }
        AppServices.refresh.requestPartial()
    }

    override fun close() {
        if (!open) return
        open = false
        alivePanels().forEach { it.visibility = View.GONE }
        AppServices.refresh.requestPartial()
    }

    override fun toggle() {
        if (open) close() else open()
    }

    /**
     * 写入前光双通道档位。
     *
     * 参数 cold 与 warm 为 0 到 10 的档位；先更新配置再交给系统能力层写入，
     * 写入失败时由系统能力层跳转显示设置。
     */
    fun setFrontLight(
        cold: Int,
        warm: Int,
    ) {
        val safeCold = cold.coerceIn(0, 10)
        val safeWarm = warm.coerceIn(0, 10)
        runCatching {
            AppServices.config.edit { config ->
                config.copy(
                    tray = config.tray.copy(brightnessCold = safeCold, brightnessWarm = safeWarm),
                )
            }
        }
        runCatching { AppServices.system.setFrontLight(safeCold, safeWarm) }
        refreshPanels()
        AppServices.refresh.requestPartial()
    }

    /** 刷新全部状态栏与托盘视图。 */
    fun refreshAll() {
        aliveStatusBars().forEach { it.refresh() }
        refreshPanels()
    }

    /** 仅刷新托盘内容。 */
    fun refreshPanels() {
        if (!open) return
        alivePanels().forEach { it.refresh() }
    }

    /** 订阅系统状态与配置变化，推送到已挂载的视图。 */
    private fun observe() {
        val system = runCatching { AppServices.system }.getOrNull() ?: return
        val config = runCatching { AppServices.config }.getOrNull() ?: return
        scope.launch { system.battery.collect { refreshAll() } }
        scope.launch { system.wifi.collect { refreshAll() } }
        scope.launch { system.bluetooth.collect { refreshAll() } }
        scope.launch { system.frontLight.collect { refreshAll() } }
        scope.launch { system.media.collect { refreshPanels() } }
        scope.launch { AppServices.notifications.items.collect { refreshPanels() } }
        scope.launch { config.state.collect { refreshAll() } }
    }

    /** 存活的状态栏视图。 */
    private fun aliveStatusBars(): List<StatusBarView> {
        statusBars.removeAll { it.get() == null }
        return statusBars.mapNotNull { it.get() }
    }

    /** 存活的托盘视图。 */
    private fun alivePanels(): List<TrayPanelView> {
        panels.removeAll { it.get() == null }
        return panels.mapNotNull { it.get() }
    }

    /** 应用上下文，供视图侧统一取资源。 */
    internal val context: Context get() = appContext
}
