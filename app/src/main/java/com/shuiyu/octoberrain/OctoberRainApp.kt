package com.shuiyu.octoberrain

import android.app.Application
import android.os.Build
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.ConfigStore
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.LauncherModule
import com.shuiyu.octoberrain.core.common.RefreshReason
import com.shuiyu.octoberrain.core.data.AppCatalogImpl
import com.shuiyu.octoberrain.core.data.AppearanceRepositoryImpl
import com.shuiyu.octoberrain.core.data.BackupServiceImpl
import com.shuiyu.octoberrain.core.data.FontsRepository
import com.shuiyu.octoberrain.core.data.IconOverrideStoreImpl
import com.shuiyu.octoberrain.core.data.JsonConfigStore
import com.shuiyu.octoberrain.core.data.NotificationCenter
import com.shuiyu.octoberrain.core.data.SystemControlsImpl
import com.shuiyu.octoberrain.core.data.ThemeRepositoryImpl
import com.shuiyu.octoberrain.core.data.UpdateServiceImpl
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.device.DeviceRuntime
import com.shuiyu.octoberrain.core.device.PermissionCenter
import com.shuiyu.octoberrain.core.render.RefreshScheduler
import com.shuiyu.octoberrain.core.render.ThumbCacheRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

/**
 * 应用入口：完成设备画像、设计运行时与全局服务的装配。
 *
 * 服务装配顺序固定：配置与设备 → 设计令牌 → 数据服务 → 宿主代理服务 → 功能模块。
 * 功能模块通过反射加载，缺失时跳过，保证并行开发期间始终可以构建。
 */
class OctoberRainApp : Application() {
    /** 应用级协程作用域，用于监听配置变化并同步运行时。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 系统能力实现，退出时用于注销广播接收器。 */
    private var systemControls: SystemControlsImpl? = null

    private var launcherProcess = true

    private val screenPowerReceiver =
        object : android.content.BroadcastReceiver() {
            override fun onReceive(
                context: android.content.Context,
                intent: android.content.Intent,
            ) {
                com.shuiyu.octoberrain.core.common.UiVisibility.setInteractive(
                    intent.action == android.content.Intent.ACTION_SCREEN_ON,
                )
            }
        }

    override fun onCreate() {
        super.onCreate()
        val processName =
            if (Build.VERSION.SDK_INT >= 28) {
                getProcessName()
            } else {
                File("/proc/self/cmdline").inputStream().use { input ->
                    val buffer = ByteArray(256)
                    String(buffer, 0, input.read(buffer).coerceAtLeast(0)).substringBefore('\u0000')
                }
            }
        launcherProcess = !processName.startsWith("$packageName:reader_pdf")
        // isolated UID只接收PDF描述符，不能初始化配置、文件仓库、网络与桌面模块。
        if (!launcherProcess) return
        observeScreenPower()
        DeviceRuntime.install(this)

        val config = JsonConfigStore(this)
        val appearance = AppearanceRepositoryImpl(this, config)
        val fonts = FontsRepository(this, config)
        val iconOverrides = IconOverrideStoreImpl(this, config)
        val themes = ThemeRepositoryImpl(this, config)

        // 设计运行时依赖设备画像与外观配置，必须最先就绪
        DesignRuntime.install(DeviceRuntime.profile, config.state.value.appearance, fonts)

        val system = SystemControlsImpl(this)
        NotificationCenter.install(this)

        AppServices.config = config
        AppServices.catalog = AppCatalogImpl(this)
        AppServices.system = system
        AppServices.notifications = NotificationCenter
        AppServices.fonts = fonts
        AppServices.picker = HostFilePicker
        AppServices.appearance = appearance
        AppServices.updates = UpdateServiceImpl(this)
        AppServices.permissions = PermissionCenter(this)
        AppServices.router = LauncherRouter
        AppServices.content = PlaceholderContentSource()
        AppServices.lock = LockGateImpl(this, config)
        AppServices.tray = PlaceholderTrayController()
        AppServices.refresh = InkRefreshController
        AppServices.backup = BackupServiceImpl(this)
        AppServices.iconOverrides = iconOverrides
        AppServices.themes = themes
        HostWereadSync.attach(filesDir)
        AppServices.wereadSync = HostWereadSync
        AppServices.wereadKeyEntry =
            com.shuiyu.octoberrain.feature.wereadapp.ui.qr
                .OnboardingKeyEntryAdapter()
        HostWereadTodos.attach(filesDir)
        AppServices.wereadTodos = HostWereadTodos

        // 服务装配完成后启动系统监听：广播接收器与状态刷新均在此时注册
        systemControls = system
        system.start()

        installModules(config)
        observeRuntimeState(config)
        RefreshScheduler.attach(
            AppServices.refresh,
            config.state.value.tray.autoRefreshMinutes,
        )
        InkLog.d("启动", "服务就绪，屏幕 ${DeviceRuntime.profile.widthPx}x${DeviceRuntime.profile.heightPx}")
    }

    private fun observeScreenPower() {
        com.shuiyu.octoberrain.core.common.UiVisibility.setInteractive(
            getSystemService(android.os.PowerManager::class.java).isInteractive,
        )
        registerReceiver(
            screenPowerReceiver,
            android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_SCREEN_ON)
                addAction(android.content.Intent.ACTION_SCREEN_OFF)
            },
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (launcherProcess && level >= TRIM_MEMORY_RUNNING_LOW) ThumbCacheRegistry.trimMemory()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (launcherProcess) ThumbCacheRegistry.trimMemory()
    }

    /** 按模块类名加载功能模块，模块缺失与安装失败均不阻断启动。 */
    private fun installModules(config: ConfigStore) {
        val names =
            listOf(
                "com.shuiyu.octoberrain.feature.home.HomeModule",
                "com.shuiyu.octoberrain.feature.widgets.WidgetsModule",
                "com.shuiyu.octoberrain.feature.tray.TrayModule",
                "com.shuiyu.octoberrain.feature.settings.SettingsModule",
                "com.shuiyu.octoberrain.feature.content.ContentModule",
                "com.shuiyu.octoberrain.feature.files.FilesModule",
                "com.shuiyu.octoberrain.feature.reader.ReaderModule",
                "com.shuiyu.octoberrain.feature.wereadapp.WereadAppModule",
            )
        for (name in names) {
            runCatching {
                val type = Class.forName(name)
                val module = type.getDeclaredConstructor().newInstance() as LauncherModule
                module.install(this)
            }.onFailure { InkLog.w("模块", "加载 $name 失败", it) }
        }
        // 模块安装完成后按最新配置重建设计令牌
        DesignRuntime.update(config.state.value.appearance)
    }

    /**
     * 释放系统能力监听。
     *
     * 应用进程正常终止时调用；真机通常不会回调该方法，进程被系统回收时接收器随进程一并释放。
     */
    override fun onTerminate() {
        systemControls?.dispose()
        systemControls = null
        scope.cancel()
        if (launcherProcess) unregisterReceiver(screenPowerReceiver)
        super.onTerminate()
    }

    /** 外观变化按刷新策略重绘，托盘间隔变化时更新调度器。 */
    private fun observeRuntimeState(config: ConfigStore) {
        scope.launch {
            config.state
                .map { it.appearance }
                .distinctUntilChanged()
                .collect { appearance ->
                    DesignRuntime.update(appearance)
                    // 外观变化属于单次快速闪烁场景，统一经刷新策略处理
                    (currentRefreshPolicy() ?: InkRefreshController)
                        .requestFullRefresh(RefreshReason.APPEARANCE)
                }
        }
        scope.launch {
            config.state
                .map { it.tray.autoRefreshMinutes }
                .distinctUntilChanged()
                .collect { minutes -> RefreshScheduler.updateInterval(minutes) }
        }
    }
}
