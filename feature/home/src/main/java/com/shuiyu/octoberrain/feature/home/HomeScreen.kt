package com.shuiyu.octoberrain.feature.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.View
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.MinuteTicker
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.common.VolumeKeySink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * 桌面屏幕。
 *
 * 负责桌面视图的数据订阅与生命周期：收集配置与应用目录、监听安装包变化、
 * 回到前台时刷新应用列表与组件内容，接收宿主转发的音量键翻页，
 * 并在销毁时释放协程、广播接收器与分钟计时器。
 */
internal class HomeScreen :
    Screen,
    VolumeKeySink {
    private var view: HomeView? = null
    private var scope: CoroutineScope? = null
    private var receiver: BroadcastReceiver? = null
    private var appContext: Context? = null
    private var minuteTicker: MinuteTicker? = null

    /** 创建桌面视图并建立数据订阅。 */
    override fun createView(context: Context): View {
        val home = HomeView(context)
        view = home
        // 首页继续右滑时进入负一屏，跳转由路由器完成。
        home.onNegativePageRequested = {
            AppServices.router.navigate(Destination.Negative)
        }
        val job = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = job
        // 整分事件交给组件声明决定刷新范围，仅声明按分钟刷新的组件重新取数与重绘。
        minuteTicker = MinuteTicker { home.refreshWidgetByMinute() }
        // 配置与应用目录任一变化都重新组装入口；两者均为 StateFlow，首次订阅即拿到当前值。
        job.launch {
            AppServices.config.state.collect { config ->
                home.applyData(AppServices.catalog.apps.value, config)
                // 配置变化后通知组件重新读取配置并刷新内容。
                home.refreshCurrentWidget(true)
            }
        }
        job.launch {
            AppServices.catalog.apps.collect { apps ->
                home.applyData(apps, AppServices.config.state.value)
            }
        }
        // 通知变化时重绘桌面，使应用角标与未读计数保持同步。
        job.launch {
            AppServices.notifications.items.collect { home.refreshNow() }
        }
        // 图标覆盖设置或清除后清空桌面图标内存缓存并局部重绘。
        // StateFlow 本身只在值变化时发射，因此首帧用 drop(1) 跳过订阅时的当前值。
        if (AppServices.hasIconOverrides()) {
            job.launch {
                AppServices.iconOverrides.version
                    .drop(1)
                    .collect { home.invalidateIcons() }
            }
        }
        registerPackageReceiver(context, job)
        return home
    }

    /** 回到前台时重新枚举应用、刷新内容源并通知组件更新。 */
    override fun onResume() {
        val job = scope ?: return
        minuteTicker?.start()
        val home = view
        home?.refreshCurrentWidget(true)
        job.launch { runCatching { AppServices.catalog.refresh() } }
        job.launch {
            // 内容源刷新完成后再通知组件，保证组件读取到的是刷新后的数据。
            runCatching { AppServices.content.refreshAll() }
            home?.refreshCurrentWidget(true)
        }
    }

    /** 离开前台时暂停分钟刷新，避免页面不可见时持续重绘。 */
    override fun onPause() {
        minuteTicker?.stop()
    }

    /** 返回键优先交给桌面处理，用于退出整理模式。 */
    override fun onBack(): Boolean = view?.handleBack() ?: false

    /**
     * 处理宿主转发的音量键。
     *
     * 参数 up 为 true 表示音量上键，翻到上一页；为 false 表示音量下键，翻到下一页。
     * 桌面视图处于任何状态时都返回 true，确保系统不会因音量键改变媒体音量。
     */
    override fun onVolumeKey(up: Boolean): Boolean = view?.turnPageByVolume(up) ?: true

    /** 释放订阅、广播、计时器与组件资源。 */
    override fun onDestroy() {
        minuteTicker?.stop()
        minuteTicker = null
        val context = appContext
        val registered = receiver
        if (context != null && registered != null) {
            runCatching { context.unregisterReceiver(registered) }
        }
        receiver = null
        appContext = null
        scope?.cancel()
        scope = null
        view?.release()
        view = null
    }

    /**
     * 注册安装包变化广播。
     *
     * 参数 context 用于取得应用上下文；job 为广播回调使用的协程作用域。
     * 收到广播后调用应用目录刷新，由 StateFlow 驱动桌面重建。
     */
    private fun registerPackageReceiver(
        context: Context,
        job: CoroutineScope,
    ) {
        val app = context.applicationContext
        appContext = app
        val registered =
            object : BroadcastReceiver() {
                override fun onReceive(
                    receiverContext: Context?,
                    intent: Intent?,
                ) {
                    job.launch { runCatching { AppServices.catalog.refresh() } }
                }
            }
        receiver = registered
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // 目标版本 33 以上必须显式声明导出状态；包变化广播只来自系统，使用非导出接收器。
                app.registerReceiver(registered, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                app.registerReceiver(registered, filter)
            }
        }
    }
}
