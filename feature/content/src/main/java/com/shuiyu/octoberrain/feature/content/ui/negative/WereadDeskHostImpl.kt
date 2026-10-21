package com.shuiyu.octoberrain.feature.content.ui.negative

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.inkread.weekread.kernel.desk.DeskHost
import com.inkread.weekread.kernel.desk.DeskPrefs
import com.inkread.weekread.kernel.desk.DeskStyle
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.NegativeHost
import com.shuiyu.octoberrain.core.common.WereadDeskConfig
import com.shuiyu.octoberrain.core.design.InkColors
import java.io.File
import kotlin.math.max

/**
 * 负一屏墨台分区视图使用的内核宿主实现。
 *
 * 从 [WereadDeskHost] 拆出，使内核宿主契约的十一个方法集中在一个类中，宿主对象只负责
 * 持有尺寸与主题等协作对象。方法数量由内核 [DeskHost] 接口决定，因此对函数数量度量
 * 局部豁免。
 *
 * @param moduleId 当前模块标识，取值为 bill、rank、fp
 * @param menu 墨单外观选项读取器
 * @param appContext 应用上下文，用于定位微读数据目录与屏幕尺寸
 * @param screenHost 负一屏宿主，用于请求重绘与页面跳转
 */
@Suppress("TooManyFunctions")
internal class WereadDeskHostImpl(
    private val moduleId: String,
    private val menu: WereadDeskMenu,
    private val appContext: Context,
    private val screenHost: NegativeHost,
    private val onWallpaper: () -> Unit,
    private val onFingerprint: () -> Unit,
    private val onSettings: () -> Unit,
) : DeskHost {
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 返回当前模块在页面中的显示位。
     *
     * 每个负一屏模块只渲染自身分区，因此掩码仅包含该分区对应的位；
     * 位值与内核 `DeskPrefs.bitOf` 一致，依次为墨单 1、阅读资产 2、阅读指纹 8。
     *
     * @return 当前模块的显示位，未知标识返回零
     */
    override fun deskModuleMask(): Int = if (deskConfig().isVisible(moduleId)) DeskPrefs.bitOf(moduleId) else 0

    /**
     * 返回分区绘制顺序。
     *
     * 单分区页面的顺序只含当前模块标识，内核据此决定绘制哪一类分区。
     *
     * @return 仅含当前模块标识的数组
     */
    override fun deskOrder(): Array<String> = arrayOf(moduleId)

    /** 读取墨单字符串类外观选项，分组不为 cfg 时返回默认值。 */
    override fun menuPrefs(
        group: String,
        key: String,
        default: String,
    ): String = menu.prefs(group, key, default)

    /** 读取墨单开关类外观选项。 */
    override fun menuBool(
        key: String,
        default: Boolean,
    ): Boolean = menu.bool(key, default)

    /** 读取墨单整数类外观选项。 */
    override fun menuInt(
        key: String,
        default: Int,
    ): Int = menu.int(key, default)

    /** 读取墨台设置中的真实字号，内核按 16–19 档排版。 */
    override fun fpFontPx(): Int = deskConfig().effectiveFpFontPx

    private fun deskConfig(): WereadDeskConfig =
        runCatching { AppServices.config.state.value.integrations.wereadDesk }
            .getOrDefault(WereadDeskConfig())

    /** 把内核颜色令牌映射为当前主题的墨水色。 */
    override fun styleColor(
        token: String,
        default: Int,
    ): Int =
        when (token) {
            DeskStyle.TOKEN_INK -> InkColors.INK
            DeskStyle.TOKEN_GRAY -> InkColors.INK_2
            DeskStyle.TOKEN_LIGHT -> InkColors.PAPER
            DeskStyle.TOKEN_LINE -> InkColors.INK_4
            DeskStyle.TOKEN_RULE -> InkColors.INK_3
            else -> default
        }

    /** 返回微读数据目录，与微读墨记写入的原盘数据目录一致。 */
    override fun filesDir(): File = File(appContext.filesDir, WEREAD_DIRECTORY)

    /**
     * 返回内核排版尺寸单位。
     *
     * 与微读墨记的墨台一致：取屏幕长边像素乘固定比例，使字号与行距按屏幕长边等比缩放。
     * 480×800 面板上结果为 1.2。
     *
     * @return 排版尺寸单位，单位为像素
     */
    override fun scale(): Float {
        val metrics = appContext.resources.displayMetrics
        return max(metrics.widthPixels, metrics.heightPixels) * UNIT_RATIO
    }

    /** 请求负一屏重绘，用于分区内容变化后刷新显示。 */
    override fun requestRedraw() {
        mainHandler.post { screenHost.requestRedraw() }
    }

    /** 处理分区内的动作：设置态、壁纸预览与指纹选书在页内完成，其余目标仅请求重绘。 */
    override fun openPage(tab: String) {
        mainHandler.post {
            when (tab) {
                TAB_SETTINGS -> onSettings()
                TAB_WALLPAPER -> onWallpaper()
                TAB_FINGERPRINT -> onFingerprint()
                else -> screenHost.requestRedraw()
            }
        }
    }

    private companion object {
        /** 微读原盘数据目录名。 */
        const val WEREAD_DIRECTORY: String = "weread"

        /** 排版尺寸单位换算比例，与微读 InkTheme.UNIT_RATIO 一致。 */
        const val UNIT_RATIO: Float = 0.0015f

        /** 账单分区目标标识。 */
        const val TAB_BILL: String = "bill"

        /** 阅读资产分区目标标识。 */
        const val TAB_RANK: String = "rank"

        /** 阅读资产分区别名。 */
        const val TAB_ASSETS: String = "assets"

        /** 阅读指纹分区目标标识。 */
        const val TAB_FP: String = "fp"

        /** 设置页目标标识。 */
        const val TAB_SETTINGS: String = "settings"

        /** 壁纸预览目标标识。 */
        const val TAB_WALLPAPER: String = "wallpaper"

        /** 指纹选书目标标识。 */
        const val TAB_FINGERPRINT: String = "fingerprint"
    }
}
