package com.shuiyu.octoberrain

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.DesktopOrientation
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.DocumentRef
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.NotificationPanelAccess
import com.shuiyu.octoberrain.core.common.RefreshReason
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.common.ScreenRegistry
import com.shuiyu.octoberrain.core.common.UiVisibility
import com.shuiyu.octoberrain.core.common.displayLabel
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.core.device.HomeRole
import com.shuiyu.octoberrain.core.render.RefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** 从其他应用返回桌面时触发整刷的最短后台时长，单位毫秒。 */
private const val EXIT_REFRESH_THRESHOLD_MS = 1500L

/** 宿主界面偏好使用的 SharedPreferences 名称。 */
private const val UI_PREFS = "octoberrain_ui"

/** 上次拒绝设为默认桌面的时间戳键。 */
private const val KEY_HOME_ROLE_DISMISSED_AT = "home_role_dismissed_at"

/** 外部文档入口诊断日志标签，仅记录动作、scheme 与授权状态。 */
private const val TAG_INCOMING = "MainIncoming"

/** 拒绝设为默认桌面后的静默时长：三天。 */
private const val HOME_ROLE_PROMPT_INTERVAL_MS = 3L * 24 * 60 * 60 * 1000

/**
 * 宿主 Activity：维护页面栈、状态栏与锁屏覆盖层。
 *
 * 页面由 ScreenRegistry 按目标创建，缺失时显示占位页，保证功能模块分头开发期间可用；
 * 锁屏、状态栏与刷新都通过全局服务访问，不直接依赖具体模块实现。
 */
class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var wallpaperLayer: WallpaperView
    private lateinit var contentFrame: FrameLayout
    private lateinit var statusHolder: FrameLayout
    private lateinit var lockHolder: FrameLayout

    /** 锁屏壁纸层，位于锁屏容器底层，PIN 面板叠加其上。 */
    private lateinit var lockWallpaperLayer: LockWallpaperView

    /** 页面栈条目：页面契约实例、视图与目标，目标用于状态栏背景判定。 */
    private data class Entry(
        val screen: Screen,
        val view: View,
        val destination: Destination? = null,
    )

    private val stack = mutableListOf<Entry>()
    private var lockOverlay: View? = null
    private var resumedScreen: Screen? = null
    private var homeRoleAsked = false
    private val configScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 主线程调度器，用于投递退出应用后的延迟整刷。 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 进入后台的时刻，取自 SystemClock.elapsedRealtime；0 表示不处于后台。 */
    private var backgroundedAtElapsed = 0L

    /** 挂起的退出应用整刷任务，重复返回时先撤销再按新延迟重新投递。 */
    private var pendingExitRefresh: Runnable? = null

    /** 本次进入后台是否由熄屏引起，用于避免亮屏解锁后触发整刷。 */
    private var backgroundedByScreenOff = false

    /** 请求 HOME 角色的请求码。 */
    private val homeRoleRequestCode = 1001

    private val screenOffReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                    backgroundedByScreenOff = true
                    AppServices.lock.onScreenOff()
                    if (AppServices.lock.isLocked) showLock()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDesktopOrientation(AppServices.config.state.value.desktop.orientation)
        configScope.launch {
            AppServices.config.state.map { it.desktop.orientation }.distinctUntilChanged().collect {
                applyDesktopOrientation(it)
            }
        }
        buildLayout()
        configureWindow()
        if (com.shuiyu.octoberrain.core.device.DeviceRuntime.profile.physicalSideInsetPx > 0) {
            configScope.launch {
                NotificationPanelAccess.available.collect {
                    configureWindow()
                    rebuildStatusBar()
                }
            }
        }
        InkRefreshController.attach(root)
        LauncherRouter.attach(this, ::navigate, ::goBack)
        HostFilePicker.attach(this)
        (AppServices.lock as? LockGateImpl)?.observe {
            mainHandler.post { syncLockState() }
        }
        if (AppServices.lock.isLocked) showLock()
        registerReceiver(
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
        )
        configScope.launch {
            UiVisibility.active.collect { active ->
                RefreshScheduler.setActive(active)
                if (active) {
                    HostWereadSync.syncAutomaticallyIfNeeded()
                    resumeTopScreen()
                    onReturnedToForeground()
                } else {
                    pauseTopScreen()
                    currentRefreshPolicy()?.onBackgrounded()
                    cancelPendingExitRefresh()
                }
            }
        }
        val deferHome = AppServices.config.state.value.onboarding.completed && isIncomingDocument(intent)
        startInitialScreen(deferHome)
        if (!openIncomingDocument(intent) && deferHome) resumeTopScreen()
    }

    /** 仅请求本 Activity 方向，不改变系统全局自动旋转。 */
    private fun applyDesktopOrientation(orientation: DesktopOrientation) {
        val requested =
            when (orientation) {
                DesktopOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                DesktopOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                DesktopOrientation.REVERSE_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            }
        if (requestedOrientation != requested) requestedOrientation = requested
    }

    /** P4仅在无障碍入口已实际连接时隐藏系统栏，否则保留可下拉的真实栏。 */
    private fun configureWindow() {
        window.setBackgroundDrawableResource(android.R.color.white)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            if (com.shuiyu.octoberrain.core.device.DeviceRuntime.profile.physicalSideInsetPx > 0 &&
                !NotificationPanelAccess.available.value
            ) {
                window.setDecorFitsSystemWindows(true)
                window.statusBarColor = Color.WHITE
                window.insetsController?.setSystemBarsAppearance(
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                )
                window.insetsController?.show(
                    android.view.WindowInsets.Type
                        .statusBars(),
                )
            } else {
                if (com.shuiyu.octoberrain.core.device.DeviceRuntime.profile.physicalSideInsetPx > 0) {
                    // 通知面板收起时也保持全屏尺寸，避免短暂状态栏inset把桌面压缩79px。
                    window.setDecorFitsSystemWindows(false)
                    window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                    window.insetsController?.systemBarsBehavior =
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
                window.insetsController?.hide(
                    android.view.WindowInsets.Type
                        .statusBars(),
                )
            }
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    /** 搭建分层布局：状态栏、内容、锁屏。 */
    private fun buildLayout() {
        root =
            FrameLayout(this).apply {
                setBackgroundColor(InkColors.PAPER)
                addOnLayoutChangeListener { view, left, top, right, bottom, _, _, _, _ ->
                    val profile = com.shuiyu.octoberrain.core.device.DeviceRuntime.profile
                    val insets =
                        com.shuiyu.octoberrain.core.device.DisplaySafeArea.insets(
                            profile.physicalSideInsetPx,
                            right - left,
                            bottom - top,
                        )
                    val current =
                        com.shuiyu.octoberrain.core.device.DisplaySafeArea.Insets(
                            view.paddingLeft,
                            view.paddingTop,
                            view.paddingRight,
                            view.paddingBottom,
                        )
                    if (current != insets) view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
                }
            }

        val statusBarHeight = statusBarHeight()
        // 壁纸层铺满整块屏幕，位于所有内容之下；状态栏与桌面内容区保持透明。
        wallpaperLayer =
            WallpaperView(this).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }
        statusHolder =
            FrameLayout(this).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        statusBarHeight,
                        Gravity.TOP,
                    )
                setBackgroundColor(Color.TRANSPARENT)
            }
        contentFrame =
            FrameLayout(this).apply {
                layoutParams =
                    FrameLayout
                        .LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ).apply { topMargin = statusBarHeight }
            }
        lockHolder =
            FrameLayout(this).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                visibility = View.GONE
            }
        // 锁屏壁纸层固定在容器第 0 层，PIN 面板每次上锁时叠加到其上。
        lockWallpaperLayer =
            LockWallpaperView(this).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }
        lockHolder.addView(lockWallpaperLayer)

        root.addView(wallpaperLayer)
        root.addView(contentFrame)
        root.addView(lockHolder)
        root.addView(statusHolder)
        setContentView(
            root,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )

        rebuildStatusBar()
    }

    private fun statusBarHeight(): Int {
        val tokens = DesignRuntime.tokens
        val systemOwnsIndicators =
            com.shuiyu.octoberrain.core.device.DeviceRuntime.profile.physicalSideInsetPx > 0 &&
                !NotificationPanelAccess.available.value
        return if (systemOwnsIndicators) minOf(tokens.statusBarHeight, tokens.trayTouch) else tokens.statusBarHeight
    }

    /** 服务连接变化时同步指示归属与栏高度，不重建桌面内容。 */
    private fun rebuildStatusBar() {
        val height = statusBarHeight()
        statusHolder.layoutParams = statusHolder.layoutParams.apply { this.height = height }
        contentFrame.layoutParams = (contentFrame.layoutParams as FrameLayout.LayoutParams).apply { topMargin = height }
        statusHolder.removeAllViews()
        runCatching {
            statusHolder.addView(
                AppServices.tray.createStatusBar(this),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    /** 首次进入：未完成引导时展示引导页，否则进入桌面。 */
    private fun startInitialScreen(deferHome: Boolean = false) {
        val config = AppServices.config.state.value
        if (!config.onboarding.completed) {
            pushScreen(
                OnboardingScreen {
                    pauseTopScreen()
                    stack.forEach {
                        it.screen.onDestroy()
                    }
                    stack.clear()
                    contentFrame.removeAllViews()
                    navigate(Destination.Home)
                },
                null,
            )
        } else if (deferHome) {
            val home = ScreenRegistry.create(Destination.Home) ?: PlaceholderScreen(Destination.Home.displayLabel())
            pushScreen(home, Destination.Home, resume = false)
        } else {
            navigate(Destination.Home)
        }
    }

    /**
     * 导航到目标页面；无模块提供时展示占位页。
     *
     * 页面栈中已存在相同目标时回退并复用该页面，已在栈顶时保持不变，
     * 避免重复创建页面与叠加相同层级。
     */
    private fun navigate(destination: Destination) {
        val existing = stack.indexOfLast { it.destination == destination }
        if (existing >= 0) {
            if (existing != stack.size - 1) popTo(existing) else InkRefreshController.requestPartial()
            return
        }
        val screen = ScreenRegistry.create(destination) ?: PlaceholderScreen(destination.displayLabel())
        pushScreen(screen, destination)
    }

    /**
     * 出栈到指定下标，被移除的页面依次销毁。
     *
     * 参数 index 为保留页面在栈中的下标；调用方需保证下标有效。
     */
    private fun popTo(index: Int) {
        if (index < 0 || index >= stack.size) return
        pauseTopScreen()
        while (stack.size - 1 > index) {
            val top = stack.removeAt(stack.size - 1)
            contentFrame.removeView(top.view)
            runCatching { top.screen.onDestroy() }
        }
        resumeTopScreen()
        updateStatusBarBackground()
        InkRefreshController.requestPartial()
    }

    /** 入栈并展示新页面。 */
    private fun pushScreen(
        screen: Screen,
        destination: Destination?,
        resume: Boolean = true,
    ) {
        val startedAt = SystemClock.elapsedRealtime()
        val view =
            runCatching { screen.createView(this) }.getOrElse {
                PlaceholderScreen("页面创建失败").createView(this)
            }
        val label = destination?.let { it.displayLabel() } ?: screen.javaClass.simpleName
        InkLog.d(
            "OctoberRain",
            "page=$label create=${SystemClock.elapsedRealtime() - startedAt}ms stack=${stack.size}",
        )
        pauseTopScreen()
        stack.add(Entry(screen, view, destination))
        contentFrame.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        if (resume) resumeTopScreen()
        updateStatusBarBackground()
        InkRefreshController.requestPartial()
    }

    /**
     * 按当前页面更新状态栏底色。
     *
     * 桌面根部保持透明，让壁纸贯通状态栏；锁屏期间同样保持透明，
     * 由锁屏壁纸层提供背景并使状态栏文字保持墨色；
     * 其余页面使用纸白底色，避免深色壁纸影响状态栏文字与图标辨识。
     */
    private fun updateStatusBarBackground() {
        val locked = runCatching { AppServices.lock.isLocked }.getOrDefault(false)
        val onHome = stack.lastOrNull()?.destination == Destination.Home
        statusHolder.setBackgroundColor(
            if (locked || onHome) Color.TRANSPARENT else InkColors.PAPER,
        )
    }

    /** 返回上一页；根桌面消耗返回键，兼容实体HOME映射为BACK。 */
    private fun goBack(): Boolean {
        if (AppServices.lock.isLocked) return true
        val top = stack.lastOrNull() ?: return false
        if (top.screen.onBack()) return true
        if (stack.size <= 1) {
            if (top.destination == Destination.Home) return true
            moveTaskToBack(true)
            return true
        }
        pauseTopScreen()
        stack.removeAt(stack.size - 1)
        contentFrame.removeView(top.view)
        runCatching { top.screen.onDestroy() }
        resumeTopScreen()
        // 退出页面后按新的栈顶校正状态栏底色，桌面根部保持透明。
        updateStatusBarBackground()
        InkRefreshController.requestPartial()
        return true
    }

    /** 展示锁屏覆盖层；已有实例时恢复可见，避免熄屏重新上锁后不显示。 */
    private fun showLock() {
        val existing = lockOverlay
        if (existing != null) {
            lockHolder.visibility = View.VISIBLE
            existing.visibility = View.VISIBLE
            updateStatusBarBackground()
            return
        }
        val overlay =
            AppServices.lock.createOverlay(
                this,
                ::requestEmergencyReset,
            ) ?: return
        lockOverlay = overlay
        lockHolder.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        lockHolder.visibility = View.VISIBLE
        updateStatusBarBackground()
    }

    /** 按锁契约的当前状态同步覆盖层，供启动、回到前台与触摸交互时校正。 */
    private fun syncLockState() {
        if (AppServices.lock.isLocked) showLock() else hideLock()
    }

    /** 隐藏锁屏覆盖层，保留壁纸层避免下次上锁重新解码。 */
    private fun hideLock() {
        lockOverlay?.let { overlay -> lockHolder.removeView(overlay) }
        lockOverlay = null
        lockHolder.visibility = View.GONE
        updateStatusBarBackground()
    }

    /**
     * 锁屏长按删除键满十秒后的应急清除入口。
     *
     * 由锁屏覆盖层回调，先弹出确认框；确认后清除 PIN、关闭锁屏开关并收起覆盖层，
     * 取消时按锁契约状态重新同步一次显隐。
     */
    private fun requestEmergencyReset() {
        if (lockOverlay == null) return
        InkDialogs.confirm(
            this,
            "是否清除密码？",
            "确认后删除已保存的 PIN 摘要并关闭锁屏，随后直接进入桌面。",
            confirmLabel = "清除密码",
            cancelLabel = "取消",
            onConfirm = { performEmergencyReset() },
            onCancel = { syncLockState() },
        )
    }

    /** 执行应急清除：清除 PIN 摘要、关闭锁屏开关并回到桌面。 */
    private fun performEmergencyReset() {
        runCatching { AppServices.lock.clearPin() }
        // clearPin 已把锁屏开关写为 false，此处补写一次以明确关闭状态，随后收起覆盖层。
        AppServices.config.edit { config -> config.copy(lock = config.lock.copy(enabled = false)) }
        syncLockState()
    }

    /**
     * 已存在任务栈时再次启动的回调。
     *
     * 应用以 singleTask 启动，重复点击桌面图标复用当前实例；此时回到首页根页面，
     * 与系统桌面按 HOME 键返回的行为保持一致。
     */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!AppServices.config.state.value.onboarding.completed) return
        if (openIncomingDocument(intent)) return
        val homeIndex = stack.indexOfLast { it.destination == Destination.Home }
        if (homeIndex >= 0 && homeIndex < stack.lastIndex) popTo(homeIndex)
    }

    /** 外部授权文档复用同一宿主，锁屏覆盖层仍由原锁契约控制。 */
    private fun openIncomingDocument(incoming: Intent?): Boolean {
        val uri = incoming?.data
        val valid = isIncomingDocument(incoming)
        Log.i(TAG_INCOMING, "incoming action=${incoming?.action} scheme=${uri?.scheme} valid=$valid")
        val opened = valid && AppServices.reader?.open(DocumentRef.ContentUri(uri.toString())) == true
        Log.i(TAG_INCOMING, "incoming opened=$opened")
        return opened
    }

    private fun isIncomingDocument(incoming: Intent?): Boolean =
        incoming?.action == Intent.ACTION_VIEW &&
            incoming.data?.scheme == "content" &&
            incoming.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0

    /**
     * 返回键处理。
     *
     * Android 11 及更早版本的手势返回由系统直接调用本方法，因此保留平台回调；
     * 三键导航的按键路径在 [onKeyDown] 中处理。
     */
    override fun onBackPressed() {
        goBack()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        // 锁屏覆盖层自行校验成功后不回调 Activity，触摸时按契约状态校正一次显隐。
        if (lockOverlay != null) syncLockState()
    }

    private fun resumeTopScreen() {
        val screen = stack.lastOrNull()?.screen ?: return
        if (!UiVisibility.active.value || resumedScreen === screen) return
        resumedScreen = screen
        screen.onResume()
    }

    private fun pauseTopScreen() {
        val screen = resumedScreen ?: return
        resumedScreen = null
        screen.onPause()
    }

    override fun onResume() {
        super.onResume()
        com.shuiyu.octoberrain.core.common.VendorSettingsSession
            .clear()
        UiVisibility.setForeground(true)
        // 回到前台后重新按栈顶页面校正状态栏底色，避免停在非桌面页后返回仍保留白底。
        updateStatusBarBackground()
        // 覆盖层可见时按锁契约校正，处理解锁后直接回到前台的情况。
        syncLockState()
        requestHomeRoleIfNeeded()
    }

    /** 引导用户把应用设为默认桌面；用户选择「以后」后三天内不再提示。 */
    private fun requestHomeRoleIfNeeded() {
        if (homeRoleAsked) return
        val config = AppServices.config.state.value
        if (!config.onboarding.completed) return
        if (HomeRole.isDefault(this)) return
        if (elapsedSinceHomeRoleDismissed() < HOME_ROLE_PROMPT_INTERVAL_MS) return
        homeRoleAsked = true
        InkDialogs.confirm(
            this,
            "设为默认桌面",
            "按 HOME 键时直接回到十雨桌面，需要把本应用设为默认主屏幕应用。",
            confirmLabel = "去设置",
            cancelLabel = "以后",
            onConfirm = {
                HomeRole.request(this, homeRoleRequestCode)
            },
            onCancel = {
                rememberHomeRoleDismissed()
            },
        )
    }

    /**
     * 读取距离上次拒绝设为默认桌面经过的毫秒数。
     *
     * 未记录过时返回 Long.MAX_VALUE，表示应当提示。
     *
     * @return 经过的毫秒数。
     */
    private fun elapsedSinceHomeRoleDismissed(): Long {
        val dismissedAt =
            getSharedPreferences(UI_PREFS, MODE_PRIVATE)
                .getLong(KEY_HOME_ROLE_DISMISSED_AT, 0L)
        if (dismissedAt <= 0L) return Long.MAX_VALUE
        return System.currentTimeMillis() - dismissedAt
    }

    /** 记录用户本次拒绝设为默认桌面的时刻。 */
    private fun rememberHomeRoleDismissed() {
        getSharedPreferences(UI_PREFS, MODE_PRIVATE)
            .edit()
            .putLong(KEY_HOME_ROLE_DISMISSED_AT, System.currentTimeMillis())
            .apply()
    }

    override fun onPause() {
        super.onPause()
        UiVisibility.setForeground(false)
        // 记录离开前台的时刻，供 onResume 判断是否属于从其他应用返回
        backgroundedAtElapsed = SystemClock.elapsedRealtime()
        currentRefreshPolicy()?.onBackgrounded()
        cancelPendingExitRefresh()
    }

    /**
     * 回到前台后恢复刷新策略，并按配置决定是否执行一次整屏刷新。
     *
     * 离开时长以 [SystemClock.elapsedRealtime] 计算，短于阈值的切换（例如打开系统对话框后返回）
     * 不触发整刷，避免在桌面内部操作时频繁闪屏。
     */
    private fun onReturnedToForeground() {
        currentRefreshPolicy()?.onForegrounded()
        val leftAt = backgroundedAtElapsed
        backgroundedAtElapsed = 0L
        val byScreenOff = backgroundedByScreenOff
        backgroundedByScreenOff = false
        if (leftAt == 0L) return
        // 熄屏离开不视为从其他应用返回，亮屏解锁后不触发整刷
        if (byScreenOff) return
        if (SystemClock.elapsedRealtime() - leftAt < EXIT_REFRESH_THRESHOLD_MS) return
        val tray = runCatching { AppServices.config.state.value.tray }.getOrNull() ?: return
        if (!tray.refreshOnAppExit) return
        scheduleExitRefresh(tray.refreshOnAppExitDelayMs.toLong().coerceAtLeast(0L))
    }

    /**
     * 投递退出应用后的整屏刷新请求。
     *
     * 延迟用于等待窗口内容合成完成，避免闪烁覆盖在上一帧画面上。
     * 请求经 [RefreshScheduler] 转发，整刷完成时刻与定时整刷共用同一时间基准。
     *
     * @param delayMs 延迟毫秒数。
     */
    private fun scheduleExitRefresh(delayMs: Long) {
        cancelPendingExitRefresh()
        val task =
            Runnable {
                pendingExitRefresh = null
                RefreshScheduler.requestFullRefresh(RefreshReason.APP_EXIT)
            }
        pendingExitRefresh = task
        mainHandler.postDelayed(task, delayMs)
    }

    /** 撤销尚未执行的退出应用整刷任务。 */
    private fun cancelPendingExitRefresh() {
        pendingExitRefresh?.let { mainHandler.removeCallbacks(it) }
        pendingExitRefresh = null
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: android.content.Intent?,
    ) {
        val handled =
            HostFilePicker.onActivityResult(requestCode, resultCode, data)
        if (!handled) {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onKeyDown(
        keyCode: Int,
        event: android.view.KeyEvent,
    ): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK) return goBack()
        val up =
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_VOLUME_UP -> true
                android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> false
                else -> return super.onKeyDown(keyCode, event)
            }
        if (!AppServices.config.state.value.tray.volumeKeyFlip) return super.onKeyDown(keyCode, event)
        val sink = stack.lastOrNull()?.screen as? com.shuiyu.octoberrain.core.common.VolumeKeySink
        return sink?.onVolumeKey(up) ?: super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        UiVisibility.setForeground(false)
        pauseTopScreen()
        configScope.cancel()
        (AppServices.lock as? LockGateImpl)?.observe(null)
        cancelPendingExitRefresh()
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenOffReceiver) }
        HostFilePicker.detach()
        stack.forEach { runCatching { it.screen.onDestroy() } }
        stack.clear()
        LauncherRouter.detach()
        InkRefreshController.detach()
        super.onDestroy()
    }
}

/**
 * 占位页面：在功能模块尚未提供页面时展示目标名称与返回按钮。
 */
class PlaceholderScreen(
    private val label: String,
) : Screen {
    override fun createView(context: Context): View {
        val tokens = DesignRuntime.tokens
        val container =
            FrameLayout(context).apply {
                setBackgroundColor(InkColors.PAPER)
            }
        val column =
            android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(tokens.pageGutter, tokens.pageTop, tokens.pageGutter, tokens.pageBottom)
            }
        column.addView(InkWidgets.title(context, label))
        column.addView(InkWidgets.caption(context, "页面模块尚未接入"))
        column.addView(InkWidgets.button(context, "返回") { AppServices.router.back() })
        container.addView(
            column,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        return container
    }
}
