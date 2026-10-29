package com.shuiyu.octoberrain.service.wallpaper

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.shuiyu.octoberrain.core.common.WallpaperOutcome
import com.shuiyu.octoberrain.core.common.WallpaperTarget
import kotlinx.coroutines.delay

/**
 * 单次壁纸设置会话。
 *
 * 会话从厂商主界面开始，按固定间隔轮询当前窗口，识别界面类型并推进点击；点击图片后
 * 按提示文案或固定等待收尾。固定等待只作为兜底，结果说明要求用户核对实际效果。
 *
 * 参数 accessibility 为已连接的无障碍服务，target 为本次目标，copyName 为副本显示名，
 * cancelled 用于响应取消请求；返回该目标的结果，超时、取消与动作超限都记为失败。
 *
 * 会话把识别、点击、等待与结果判定放在同一状态机内，函数按界面推进步骤聚合；
 * 拆分到多个类型会让同一次会话的状态需要跨对象传递，故保留在本类型内。
 */
@Suppress("TooManyFunctions")
internal class WallpaperSession(
    private val accessibility: AccessibilityService,
    private val target: WallpaperTarget,
    private val copyName: String,
    private val cancelled: () -> Boolean,
) {
    private val startedAt = SystemClock.uptimeMillis()
    private val clickedKeys = mutableSetOf<String>()
    private var actions = 0
    private var lastActionAt = 0L
    private var pickEnteredAt = 0L
    private var clickedImageAt = 0L
    private var promptSeenAt = 0L
    private var imageIndex = 0

    /** 执行完整会话直至得到结果。 */
    suspend fun run(): WallpaperOutcome {
        while (true) {
            val now = SystemClock.uptimeMillis()
            val stopped = stopReason(now)
            if (stopped != null) return fail(stopped)
            val root = runCatching { accessibility.rootInActiveWindow }.getOrNull()
            if (root == null) {
                delay(TICK_MS)
                continue
            }
            val outcome =
                try {
                    observe(root, now)
                } finally {
                    WallpaperNodes.recycle(root)
                }
            if (outcome != null) return outcome
            delay(TICK_MS)
        }
    }

    /** 检查取消与总超时，返回失败说明或 null。 */
    private fun stopReason(now: Long): String? =
        when {
            cancelled() -> TEXT_CANCELLED
            now - startedAt > SESSION_TIMEOUT_MS -> TEXT_TIMEOUT
            else -> null
        }

    /** 按当前窗口归属分派到厂商内或厂商外处理。 */
    private fun observe(
        root: AccessibilityNodeInfo,
        now: Long,
    ): WallpaperOutcome? {
        val packageName = root.packageName?.toString().orEmpty()
        return if (packageName == VENDOR_PACKAGE) insideVendor(root, now) else outsideVendor(now)
    }

    /** 厂商应用不在前台时，按已点击图片或出现宽限判定结果。 */
    private fun outsideVendor(now: Long): WallpaperOutcome? =
        when {
            settled(now) -> success(TEXT_SETTLED)
            now - startedAt > APPEAR_TIMEOUT_MS -> fail(TEXT_NO_VENDOR)
            else -> null
        }

    /** 厂商应用在前台时，先读文案，再推进界面点击。 */
    private fun insideVendor(
        root: AccessibilityNodeInfo,
        now: Long,
    ): WallpaperOutcome? {
        val texts = WallpaperNodes.collectTexts(root)
        return textOutcome(texts, now)
            ?: if (settled(now)) success(TEXT_SETTLED) else advance(root, texts, now)
    }

    /** 点击图片后是否已满收尾等待。 */
    private fun settled(now: Long): Boolean = clickedImageAt > 0 && now - clickedImageAt >= IMAGE_SETTLE_MS

    /** 按界面文案判定失败、成功或提示消失。 */
    private fun textOutcome(
        texts: List<String>,
        now: Long,
    ): WallpaperOutcome? =
        when {
            texts.any { it.contains(FAILURE_MARK) } -> fail(TEXT_FAILED)
            texts.any { it.contains(SUCCESS_MARK) } -> success(TEXT_SET)
            else -> promptGone(texts, now)
        }

    /** 记录「正在设置壁纸」提示的出现与消失。 */
    private fun promptGone(
        texts: List<String>,
        now: Long,
    ): WallpaperOutcome? {
        val prompt = texts.any { it.contains(PROMPT_MARK) }
        if (prompt && promptSeenAt == 0L) promptSeenAt = now
        val gone = !prompt && promptSeenAt > 0 && now - promptSeenAt >= PROMPT_GONE_MS
        return if (gone) success(TEXT_PROMPT_DONE) else null
    }

    /** 识别当前界面并在允许的间隔后执行一次点击。 */
    private fun advance(
        root: AccessibilityNodeInfo,
        texts: List<String>,
        now: Long,
    ): WallpaperOutcome? {
        val screen = detectScreen(root, texts)
        trackPick(screen, now)
        val ready = now - lastActionAt >= SAME_SCREEN_MS && pickReady(screen, now)
        return if (ready) perform(root, screen, now) else null
    }

    /** 记录进入与离开选图页的时刻。 */
    private fun trackPick(
        screen: Screen,
        now: Long,
    ) {
        if (screen == Screen.PICK) {
            if (pickEnteredAt == 0L) pickEnteredAt = now
        } else {
            pickEnteredAt = 0L
        }
    }

    /** 选图页需等网格就绪后才允许点击。 */
    private fun pickReady(
        screen: Screen,
        now: Long,
    ): Boolean = screen != Screen.PICK || (pickEnteredAt > 0L && now - pickEnteredAt >= GRID_WAIT_MS)

    /** 执行一次点击并维护动作计数。 */
    private fun perform(
        root: AccessibilityNodeInfo,
        screen: Screen,
        now: Long,
    ): WallpaperOutcome? {
        val acted = step(root, screen) ?: return null
        actions += 1
        lastActionAt = now
        if (acted == Screen.PICK) {
            clickedImageAt = now
            imageIndex += 1
        }
        return if (actions > MAX_ACTIONS || imageIndex > MAX_IMAGE_TRIES) fail(TEXT_ACTION_LIMIT) else null
    }

    /** 按界面类型点击一次，同一会话内同名控件只点击一次。 */
    private fun step(
        root: AccessibilityNodeInfo,
        screen: Screen,
    ): Screen? {
        val key = keyOf(screen) ?: return null
        val acted = !clickedKeys.contains(key) && clickScreen(root, screen)
        if (acted) clickedKeys += key
        return if (acted) screen else null
    }

    /** 按界面类型执行一次点击，返回是否命中可点击控件。 */
    private fun clickScreen(
        root: AccessibilityNodeInfo,
        screen: Screen,
    ): Boolean =
        when (screen) {
            Screen.MAIN -> WallpaperNodes.clickByTextOrId(accessibility, root, targetLabel(), targetIdSuffix())
            Screen.DIALOG -> WallpaperNodes.clickByText(accessibility, root, DIALOG_LABEL)
            Screen.CONFIG -> WallpaperNodes.clickByText(accessibility, root, TYPE_LABEL)
            Screen.PICK -> WallpaperNodes.clickPickImage(accessibility, root, copyName)
            Screen.UNKNOWN -> false
        }

    /** 供点击去重使用的控件标识。 */
    private fun keyOf(screen: Screen): String? =
        when (screen) {
            Screen.MAIN -> "main:${target.name}"
            Screen.DIALOG -> DIALOG_LABEL
            Screen.CONFIG -> TYPE_LABEL
            Screen.PICK -> PICK_LABEL
            Screen.UNKNOWN -> null
        }

    /** 目标入口文案。 */
    private fun targetLabel(): String = if (target == WallpaperTarget.STANDBY) MAIN_STANDBY else MAIN_SHUTDOWN

    /** 目标入口 id 后缀。 */
    private fun targetIdSuffix(): String = if (target == WallpaperTarget.STANDBY) ID_STANDBY else ID_SHUTDOWN

    /** 判定当前窗口形态。 */
    private fun detectScreen(
        root: AccessibilityNodeInfo,
        texts: List<String>,
    ): Screen =
        when {
            hasNode(root, ID_PAGE) && hasNode(root, ID_GRID) -> Screen.PICK
            texts.any { it == DIALOG_LABEL } -> Screen.DIALOG
            hasNode(root, ID_CONFIG) -> Screen.CONFIG
            texts.any { it == MAIN_STANDBY || it == MAIN_SHUTDOWN } -> Screen.MAIN
            else -> Screen.UNKNOWN
        }

    private fun hasNode(
        root: AccessibilityNodeInfo,
        suffix: String,
    ): Boolean {
        val node = WallpaperNodes.findByIdSuffix(root, suffix) ?: return false
        if (node !== root) WallpaperNodes.recycle(node)
        return true
    }

    /** 构造成功结果。 */
    private fun success(message: String): WallpaperOutcome = WallpaperOutcome(target, true, message)

    /** 构造失败结果。 */
    private fun fail(message: String): WallpaperOutcome = WallpaperOutcome(target, false, message)

    /** 界面形态。 */
    private enum class Screen {
        /** 厂商主界面。 */
        MAIN,

        /** 未导出设置活动的入口弹窗。 */
        DIALOG,

        /** 壁纸类型配置页。 */
        CONFIG,

        /** 图片选择页。 */
        PICK,

        /** 未识别界面。 */
        UNKNOWN,
    }

    private companion object {
        const val VENDOR_PACKAGE = "com.wetao.wallpaper"
        const val MAIN_STANDBY = "锁屏壁纸"
        const val MAIN_SHUTDOWN = "关机壁纸"
        const val DIALOG_LABEL = "自定义壁纸"
        const val TYPE_LABEL = "壁纸类型"
        const val PICK_LABEL = "选图"

        const val ID_STANDBY = "/sleep_wallpaper"
        const val ID_SHUTDOWN = "/shutdown_wallpaper"
        const val ID_CONFIG = "/ll_wallpaper_setting"
        const val ID_PAGE = "/page_text"
        const val ID_GRID = "/recycler_view"

        const val PROMPT_MARK = "正在设置壁纸"
        const val SUCCESS_MARK = "设置成功"
        const val FAILURE_MARK = "设置失败"

        const val TEXT_CANCELLED = "已取消"
        const val TEXT_TIMEOUT = "设置超时，请核对实际效果"
        const val TEXT_SETTLED = "界面流程已结束，请核对实际效果"
        const val TEXT_NO_VENDOR = "壁纸应用未出现"
        const val TEXT_SET = "壁纸设置成功"
        const val TEXT_PROMPT_DONE = "界面提示设置完成，请核对实际效果"
        const val TEXT_FAILED = "壁纸设置失败"
        const val TEXT_ACTION_LIMIT = "界面动作次数超限"

        const val TICK_MS = 150L
        const val SAME_SCREEN_MS = 800L
        const val GRID_WAIT_MS = 600L
        const val IMAGE_SETTLE_MS = 1800L
        const val PROMPT_GONE_MS = 500L
        const val APPEAR_TIMEOUT_MS = 8000L
        const val SESSION_TIMEOUT_MS = 45000L

        const val MAX_ACTIONS = 12
        const val MAX_IMAGE_TRIES = 2
    }
}
