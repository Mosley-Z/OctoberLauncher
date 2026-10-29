package com.shuiyu.octoberrain.core.common

import kotlinx.coroutines.flow.StateFlow

/**
 * S4 系统壁纸目标。
 *
 * 对应厂商应用的「锁屏壁纸」入口，在本机语义上表现为设备休眠后显示的画面；
 * 与 Android 桌面壁纸、十雨密码页背景无关。
 */
enum class WallpaperTarget(
    /** 界面显示文案。 */
    val label: String,
) {
    /** 休眠壁纸，厂商界面称锁屏壁纸。 */
    STANDBY("休眠壁纸"),

    /** 关机壁纸。 */
    SHUTDOWN("关机壁纸"),
}

/**
 * 一次设置请求覆盖的目标组合。
 *
 * 顺序固定为先休眠后关机，「同时设置」按该顺序串行执行并分别记录结果。
 */
enum class WallpaperScope(
    /** 菜单显示文案。 */
    val label: String,
    /** 本次需要执行的目标，顺序即执行顺序。 */
    val targets: List<WallpaperTarget>,
) {
    /** 仅设置休眠壁纸。 */
    STANDBY("休眠壁纸", listOf(WallpaperTarget.STANDBY)),

    /** 仅设置关机壁纸。 */
    SHUTDOWN("关机壁纸", listOf(WallpaperTarget.SHUTDOWN)),

    /** 同时设置休眠与关机壁纸。 */
    BOTH("同时设置", listOf(WallpaperTarget.STANDBY, WallpaperTarget.SHUTDOWN)),
}

/** 单个目标的执行结果。 */
data class WallpaperOutcome(
    /** 目标。 */
    val target: WallpaperTarget,
    /** 是否成功。 */
    val success: Boolean,
    /** 面向用户的结果说明。 */
    val message: String,
)

/** 一次设置请求的最终结果。 */
sealed interface WallpaperResult {
    /** 全部目标完成。 */
    data class AllDone(
        val outcomes: List<WallpaperOutcome>,
    ) : WallpaperResult

    /** 部分目标完成，其余未执行。 */
    data class Partial(
        val outcomes: List<WallpaperOutcome>,
        val message: String,
    ) : WallpaperResult

    /** 全部失败或未执行。 */
    data class Failed(
        val outcomes: List<WallpaperOutcome>,
        val message: String,
    ) : WallpaperResult

    /** 当前设备或通道不支持，未执行任何动作。 */
    data class Unavailable(
        val message: String,
    ) : WallpaperResult
}

/** 通道当前阶段，供界面显示进度。 */
sealed interface WallpaperProgress {
    /** 空闲。 */
    data object Idle : WallpaperProgress

    /** 准备共享副本与无障碍会话。 */
    data object Preparing : WallpaperProgress

    /** 正在设置指定目标。 */
    data class Setting(
        val target: WallpaperTarget,
    ) : WallpaperProgress

    /** 已结束，携带最终结果。 */
    data class Done(
        val result: WallpaperResult,
    ) : WallpaperProgress
}

/**
 * 系统壁纸通道。
 *
 * 由服务层实现，界面层只依赖该接口。通道同一时间只接受一个任务，重复请求在入口处拒绝；
 * 目标能力检测失败时返回 [WallpaperResult.Unavailable]，不以桌面壁纸接口替代。
 */
interface WallpaperChannel {
    /** 当前阶段，界面可订阅显示。 */
    val progress: StateFlow<WallpaperProgress>

    /** 当前设备是否具备该通道能力，包含机型与厂商组件检测。 */
    fun supports(): Boolean

    /** 是否已有任务在执行。 */
    fun isBusy(): Boolean

    /**
     * 执行一次设置请求。
     *
     * 参数 sourcePath 为源图片绝对路径，通道内部按目标尺寸生成共享副本；
     * scope 为本次目标组合。方法挂起直至流程结束或取消，结果逐目标记录。
     */
    suspend fun apply(
        sourcePath: String,
        scope: WallpaperScope,
    ): WallpaperResult

    /** 撤销在途任务，停止后续点击动作。 */
    fun cancel()
}
