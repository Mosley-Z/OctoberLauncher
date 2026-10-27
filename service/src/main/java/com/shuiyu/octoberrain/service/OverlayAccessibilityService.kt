package com.shuiyu.octoberrain.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.MinuteTicker
import com.shuiyu.octoberrain.core.common.NotificationPanelAccess
import com.shuiyu.octoberrain.core.common.UiVisibility
import com.shuiyu.octoberrain.core.design.DesignRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 无障碍悬浮服务：在桌面之外显示悬浮状态栏，并提供整屏闪烁刷新。
 *
 * 服务按前台应用包名判断归属：前台为十雨桌面时隐藏全部悬浮窗口，避免与 Activity 内
 * 的状态栏重复；其余情况在配置开启悬浮状态栏后挂载悬浮窗口。
 */
class OverlayAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var windowManager: WindowManager? = null

    private var statusView: OverlayStatusBarView? = null

    private var statusParams: WindowManager.LayoutParams? = null

    private var statusAttached = false

    private var flashView: View? = null

    private var flashing = false

    private var foregroundPackage: String = ""

    private val statusTicker = MinuteTicker { updateStatus() }

    private val debounce = Runnable { applyOverlayVisibility() }

    override fun onCreate() {
        super.onCreate()
        windowManager = runCatching { getSystemService(WindowManager::class.java) }.getOrNull()
        foregroundPackage = packageName
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = runCatching { getSystemService(WindowManager::class.java) }.getOrNull()
        observeStates()
        applyOverlayVisibility()
        NotificationPanelAccess.connect { performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        NotificationPanelAccess.disconnect()
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isEmpty()) return
        foregroundPackage = pkg
        // 200 毫秒去抖，避免窗口连续变化时重复挂载窗口
        handler.removeCallbacks(debounce)
        handler.postDelayed(debounce, DEBOUNCE_MS)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        NotificationPanelAccess.disconnect()
        handler.removeCallbacksAndMessages(null)
        statusTicker.stop()
        removeStatusWindow()
        removeFlashWindow()
        scope.cancel()
        super.onDestroy()
    }

    /** 订阅电池、网络与托盘配置，状态变化时刷新悬浮界面。 */
    private fun observeStates() {
        val system = runCatching { AppServices.system }.getOrNull() ?: return
        scope.launch { system.battery.collect { updateStatus() } }
        scope.launch { system.wifi.collect { updateStatus() } }
        scope.launch { AppServices.config.state.collect { applyOverlayVisibility() } }
        scope.launch { UiVisibility.interactive.collect { applyOverlayVisibility() } }
    }

    /** 依据前台包名与配置决定悬浮窗口的挂载状态。 */
    private fun applyOverlayVisibility() {
        val config = runCatching { AppServices.config.state.value.tray }.getOrNull() ?: return
        val launcherVisible = foregroundPackage == packageName
        if (launcherVisible || !config.overlayEnabled || !UiVisibility.interactive.value) {
            removeStatusWindow()
            statusTicker.stop()
            return
        }
        showStatusWindow()
        statusTicker.start()
    }

    /** 挂载悬浮状态栏窗口。 */
    private fun showStatusWindow() {
        val manager = windowManager ?: return
        if (statusAttached) {
            updateStatus()
            return
        }
        val view =
            statusView ?: OverlayStatusBarView(this, ::flashScreen).also {
                statusView = it
                statusParams =
                    overlayParams(
                        width = WindowManager.LayoutParams.MATCH_PARENT,
                        height = DesignRuntime.tokens.statusBarHeight,
                        flags = WINDOW_FLAGS,
                    )
            }
        val params = statusParams ?: return
        val attached =
            runCatching {
                manager.addView(view, params)
                true
            }.getOrDefault(false)
        statusAttached = attached
        updateStatus()
    }

    /** 移除悬浮状态栏窗口。 */
    private fun removeStatusWindow() {
        val manager = windowManager ?: return
        val view = statusView ?: return
        if (!statusAttached) return
        runCatching { manager.removeView(view) }
        statusAttached = false
    }

    /** 刷新悬浮状态栏显示。 */
    private fun updateStatus() {
        if (statusAttached && UiVisibility.interactive.value) statusView?.refresh()
    }

    /** 整屏闪烁刷新：黑屏 500 毫秒后转白，再 500 毫秒后移除覆盖层。 */
    private fun flashScreen() {
        val manager = windowManager ?: return
        if (flashing) return
        flashing = true
        val view = View(this).apply { setBackgroundColor(Color.BLACK) }
        flashView = view
        val params =
            overlayParams(
                width = WindowManager.LayoutParams.MATCH_PARENT,
                height = WindowManager.LayoutParams.MATCH_PARENT,
                flags = WINDOW_FLAGS,
            )
        val added =
            runCatching {
                manager.addView(view, params)
                true
            }.getOrDefault(false)
        if (!added) {
            flashing = false
            flashView = null
            return
        }
        handler.postDelayed({
            runCatching { view.setBackgroundColor(Color.WHITE) }
        }, FLASH_STEP_MS)
        handler.postDelayed({
            runCatching { manager.removeView(view) }
            flashView = null
            flashing = false
        }, FLASH_STEP_MS * 2)
    }

    /** 移除残留的闪烁覆盖层。 */
    private fun removeFlashWindow() {
        val manager = windowManager ?: return
        val view = flashView ?: return
        runCatching { manager.removeView(view) }
        flashView = null
        flashing = false
    }

    /** 构造悬浮窗口参数。 */
    private fun overlayParams(
        width: Int,
        height: Int,
        flags: Int,
    ): WindowManager.LayoutParams =
        WindowManager
            .LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

    private companion object {
        /** 不可获焦、不阻塞触摸、布局到屏幕物理区域。 */
        const val WINDOW_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        const val DEBOUNCE_MS = 200L

        const val FLASH_STEP_MS = 500L
    }
}
