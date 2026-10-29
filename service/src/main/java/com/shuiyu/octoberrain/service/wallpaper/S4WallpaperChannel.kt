package com.shuiyu.octoberrain.service.wallpaper

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import com.shuiyu.octoberrain.core.common.WallpaperChannel
import com.shuiyu.octoberrain.core.common.WallpaperOutcome
import com.shuiyu.octoberrain.core.common.WallpaperProgress
import com.shuiyu.octoberrain.core.common.WallpaperResult
import com.shuiyu.octoberrain.core.common.WallpaperScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * S4 系统壁纸通道实现。
 *
 * 厂商壁纸应用的设置活动未导出，外部只能从导出的主活动进入，因此整个流程依赖限定会话的
 * 无障碍点击：启动厂商主活动，按界面特征依次点击目标入口、自定义壁纸、壁纸类型与图片项，
 * 再按界面文案判定结果。通道不写系统属性、不碰桌面壁纸接口，只记录界面流程结果；固定等待
 * 只作为兜底，结果说明中明确要求核对实际效果。
 *
 * 参数 context 为应用上下文，用于准备图片副本。无障碍服务连接后由服务调用 [attach]，
 * 断开时调用 [detach]；未连接期间 [apply] 返回不可用。
 */
class S4WallpaperChannel(
    private val context: Context,
) : WallpaperChannel {
    private val state = MutableStateFlow<WallpaperProgress>(WallpaperProgress.Idle)
    override val progress: StateFlow<WallpaperProgress> = state.asStateFlow()

    /** 当前连接的无障碍服务，为空表示通道不可用。 */
    private var service: AccessibilityService? = null

    /** 是否有任务正在执行。 */
    private var busy = false

    /** 取消标记，供 [cancel] 与 [detach] 置位。 */
    private var cancelled = false

    /** 绑定无障碍服务实例。 */
    fun attach(service: AccessibilityService) {
        this.service = service
    }

    /** 解绑无障碍服务实例，并终止在途任务。 */
    fun detach() {
        cancelled = true
        service = null
    }

    override fun supports(): Boolean = isS4Device() && service != null && vendorIntent() != null

    override fun isBusy(): Boolean = busy

    override fun cancel() {
        cancelled = true
    }

    override suspend fun apply(
        sourcePath: String,
        scope: WallpaperScope,
    ): WallpaperResult {
        if (busy) return WallpaperResult.Failed(emptyList(), "正在设置壁纸")
        val failure = precheckChannel(service, ::supports)
        return failure ?: run(sourcePath, scope)
    }

    /** 准备副本、启动厂商应用并逐目标执行会话。 */
    private suspend fun run(
        sourcePath: String,
        scope: WallpaperScope,
    ): WallpaperResult {
        busy = true
        cancelled = false
        state.value = WallpaperProgress.Preparing
        val result =
            try {
                val copyName =
                    withContext(Dispatchers.IO) {
                        WallpaperCopy.prepare(context, sourcePath)
                    }
                when {
                    copyName == null -> WallpaperResult.Failed(emptyList(), "无法准备壁纸副本")
                    !startVendor() -> WallpaperResult.Failed(emptyList(), "未能打开壁纸应用")
                    else -> collect(scope, copyName)
                }
            } finally {
                WallpaperNavigator.returnHome(context, service)
                busy = false
            }
        state.value = WallpaperProgress.Done(result)
        return result
    }

    /** 依次执行每个目标的会话，两个目标之间退回厂商主界面。 */
    private suspend fun collect(
        scope: WallpaperScope,
        copyName: String,
    ): WallpaperResult {
        val accessibility = service
        if (accessibility == null) return WallpaperResult.Unavailable("无障碍服务已断开")
        val outcomes = ArrayList<WallpaperOutcome>(scope.targets.size)
        for (target in scope.targets) {
            state.value = WallpaperProgress.Setting(target)
            outcomes +=
                if (cancelled) {
                    WallpaperOutcome(target, false, "已取消")
                } else {
                    WallpaperSession(accessibility, target, copyName) { cancelled }.run()
                }
            if (!outcomes.last().success || cancelled) break
            // 两个目标之间退回厂商主界面，下一个目标重新从入口点起
            WallpaperNavigator.backToVendorMain(accessibility)
        }
        return combine(outcomes)
    }

    /** 构造厂商主活动的显式启动意图，组件缺失或非 S4 时返回 null。 */
    private fun vendorIntent(): Intent? {
        if (!isS4Device()) return null
        val intent =
            Intent().apply {
                setClassName(VENDOR_PACKAGE, VENDOR_ACTIVITY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        val resolved =
            runCatching {
                context.packageManager.resolveActivity(intent, 0)
            }.getOrNull()
        return intent.takeIf { resolved?.activityInfo != null }
    }

    /** 启动厂商主活动。 */
    private fun startVendor(): Boolean {
        val intent = vendorIntent() ?: return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}

/** 当前机型是否为 S4。 */
private fun isS4Device(): Boolean = Build.MODEL.equals(MODEL_S4, ignoreCase = true)

private const val MODEL_S4 = "S4"
private const val VENDOR_PACKAGE = "com.wetao.wallpaper"
private const val VENDOR_ACTIVITY = "com.wetao.wallpaper.MainActivity"

/** 检查通道可用性：无障碍未连接或机型不支持时返回不可用结果。 */
private fun precheckChannel(
    service: AccessibilityService?,
    supports: () -> Boolean,
): WallpaperResult? =
    when {
        service == null -> WallpaperResult.Unavailable("无障碍服务未启用")
        !supports() -> WallpaperResult.Unavailable("当前设备不支持壁纸通道")
        else -> null
    }

/** 组合逐目标结果：全部成功、部分成功与全部失败分别对应不同状态。 */
private fun combine(outcomes: List<WallpaperOutcome>): WallpaperResult {
    if (outcomes.isEmpty()) return WallpaperResult.Failed(emptyList(), "未执行任何目标")
    val done = outcomes.count { it.success }
    return when {
        done == outcomes.size -> WallpaperResult.AllDone(outcomes)
        done == 0 -> WallpaperResult.Failed(outcomes, "壁纸设置失败")
        else -> WallpaperResult.Partial(outcomes, "部分目标未完成")
    }
}

/** 供测试引用：目标数量与成功数量的组合规则与 [combine] 一致。 */
internal fun combineOutcomes(outcomes: List<WallpaperOutcome>): WallpaperResult = combine(outcomes)
