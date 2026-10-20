package com.shuiyu.octoberrain.feature.wereadapp.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.TextView
import com.shuiyu.octoberrain.core.design.InkWidgets

/**
 * 续航优化段。
 *
 * 补齐权限命令与复制、三项系统开关、电池优化入口与 Shizuku 入口。
 * 三项开关直接读写系统设置，写入需要 `WRITE_SECURE_SETTINGS`；未授权时提示先复制命令。
 *
 * @param context 创建视图的上下文
 */
@Suppress("TooManyFunctions")
internal class WereadLabPowerPage(
    context: Context,
) : LabPageView(context) {
    override fun render() {
        addIntro()
        addPermissionSection()
        addSwitchSection()
        addBatteryEntry()
        addShizukuEntry()
    }

    /** 简介文案。 */
    private fun addIntro() {
        content.addView(labBody(context, "续航优化通过系统开关减少唤醒与刷新，适合长期插电的墨水屏设备。"))
        content.addView(labSpacer(context, 0.5f))
        content.addView(labCaption(context, "修改系统设置需要一次性授权，授权命令见下方。"))
    }

    /** 权限状态、授权命令与复制按钮。 */
    private fun addPermissionSection() {
        content.addView(labSpacer(context))
        content.addView(labSectionTitle(context, "写入权限"))
        content.addView(InkWidgets.labelRow(context, "权限状态", if (hasPermission()) "已授权" else "未授权"))
        content.addView(labSpacer(context, 0.5f))
        content.addView(labCaption(context, "在电脑上执行一次："))
        content.addView(commandText())
        content.addView(labSpacer(context, 0.5f))
        content.addView(
            labButtonRow(
                context,
                InkWidgets.button(context, "复制命令") { copyCommand() },
            ),
        )
        content.addView(
            labCaption(context, "执行后回到本页即可直接勾选；未授权时勾选不会写入系统设置。"),
        )
    }

    /** 授权命令文本。 */
    private fun commandText(): TextView =
        InkWidgets.body(context, grantCommand()).apply {
            setPadding(
                com.shuiyu.octoberrain.core.design.DesignRuntime.tokens.gap,
                com.shuiyu.octoberrain.core.design.DesignRuntime.tokens.gap,
                com.shuiyu.octoberrain.core.design.DesignRuntime.tokens.gap,
                com.shuiyu.octoberrain.core.design.DesignRuntime.tokens.gap,
            )
            background =
                InkWidgets.background(
                    com.shuiyu.octoberrain.core.design.DesignRuntime.tokens.radius,
                    com.shuiyu.octoberrain.core.design.InkColors.PAPER,
                    com.shuiyu.octoberrain.core.design.DesignRuntime.tokens.stroke,
                    com.shuiyu.octoberrain.core.design.InkColors.INK,
                )
        }

    /** 三项系统开关。 */
    private fun addSwitchSection() {
        content.addView(labSpacer(context, 2f))
        content.addView(labSectionTitle(context, "系统开关"))
        content.addView(
            labCheckRow(context, "关掉「开放网络可用」提示", isOff(Settings.Global::class.java, KEY_WIFI_NOTIF)) {
                applyToggle(WIFI_NOTIF_KEYS, it)
            },
        )
        content.addView(labCaption(context, "关闭后系统不再提示可用的开放网络。"))
        content.addView(
            labCheckRow(context, "关掉界面动画", isAnyOff(Settings.Global::class.java, ANIM_KEYS)) {
                applyToggle(ANIM_KEYS, it)
            },
        )
        content.addView(labCaption(context, "同时关闭窗口动画、过渡动画与动画时长三档。"))
        content.addView(
            labCheckRow(context, "关掉时钟屏保", isOff(Settings.Secure::class.java, KEY_SCREENSAVER)) {
                applyToggle(SCREENSAVER_KEYS, it)
            },
        )
        content.addView(labCaption(context, "关闭后系统不再启动时钟屏保。"))
    }

    /** 电池优化系统入口。 */
    private fun addBatteryEntry() {
        content.addView(labSpacer(context, 2f))
        content.addView(
            labWideButton(context, "打开系统「电池优化」页") {
                val opened =
                    openIntent(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) ||
                        openIntent(Intent(Settings.ACTION_SETTINGS))
                if (!opened) labToast(context, "未能打开电池优化设置")
            },
        )
        content.addView(labCaption(context, "在系统页把十雨桌面设为「不优化」，避免后台刷新被限流。"))
    }

    /** Shizuku 系统入口。 */
    private fun addShizukuEntry() {
        content.addView(labSpacer(context, 2f))
        content.addView(
            labWideButton(context, "Shizuku 入口（系统应用信息）") {
                val opened =
                    openIntent(shizukuIntent()) ||
                        openIntent(Intent(Settings.ACTION_SETTINGS))
                if (!opened) labToast(context, "未能打开系统设置")
            },
        )
        content.addView(labCaption(context, "Shizuku 可用时可通过其授权执行高级操作；未安装时跳转系统设置。"))
    }

    /** Shizuku 的应用信息页意图。 */
    private fun shizukuIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$SHIZUKU_PACKAGE"))

    /** 打开系统页面，返回是否成功。 */
    private fun openIntent(intent: Intent): Boolean =
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)

    /** 判断写入权限是否已授予。 */
    private fun hasPermission(): Boolean =
        context.packageManager.checkPermission(
            Manifest.permission.WRITE_SECURE_SETTINGS,
            context.packageName,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** 授权命令文本。 */
    private fun grantCommand(): String =
        "adb shell pm grant ${context.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}"

    /** 复制授权命令到剪贴板。 */
    private fun copyCommand() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            labToast(context, "剪贴板不可用")
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("授权命令", grantCommand()))
        labToast(context, "授权命令已复制")
    }

    /** 判断单个二进制开关是否已关闭。 */
    private fun isOff(
        clazz: Class<*>,
        key: String,
    ): Boolean = readSetting(clazz, key, BASELINE_ON) == VALUE_OFF

    /** 判断多键开关中是否有一项已关闭。 */
    private fun isAnyOff(
        clazz: Class<*>,
        keys: List<String>,
    ): Boolean = keys.any { isOff(clazz, it) }

    /** 读取系统设置值，缺失时返回回退值。 */
    private fun readSetting(
        clazz: Class<*>,
        key: String,
        fallback: Int,
    ): Int =
        runCatching {
            if (clazz == Settings.Secure::class.java) {
                Settings.Secure.getInt(context.contentResolver, key, fallback)
            } else {
                Settings.Global.getInt(context.contentResolver, key, fallback)
            }
        }.getOrDefault(fallback)

    /** 应用一次开关切换；无权限时提示并回滚勾选状态。 */
    private fun applyToggle(
        keys: List<String>,
        checked: Boolean,
    ) {
        if (!hasPermission()) {
            labToast(context, "未授权：先复制命令到电脑执行一次")
            refresh()
            return
        }
        keys.forEach { key ->
            if (checked) {
                captureBaseline(key)
                writeSetting(key, VALUE_OFF)
            } else {
                writeSetting(key, captureOf(key))
            }
        }
        refresh()
    }

    /** 记录关闭前的原值，供恢复使用。 */
    private fun captureBaseline(key: String) {
        val prefs = context.getSharedPreferences(POWER_PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(captureKey(key))) return
        val source = if (key == KEY_SCREENSAVER) Settings.Secure::class.java else Settings.Global::class.java
        prefs.edit().putInt(captureKey(key), readSetting(source, key, BASELINE_ON)).apply()
    }

    /** 读取记录的原值，缺失时使用基线 1。 */
    private fun captureOf(key: String): Int =
        context
            .getSharedPreferences(POWER_PREFS, Context.MODE_PRIVATE)
            .getInt(captureKey(key), BASELINE_ON)

    /** 写入系统设置；无权限时静默失败并提示。 */
    private fun writeSetting(
        key: String,
        value: Int,
    ) {
        val ok =
            runCatching {
                if (key == KEY_SCREENSAVER) {
                    Settings.Secure.putInt(context.contentResolver, key, value)
                } else {
                    Settings.Global.putInt(context.contentResolver, key, value)
                }
            }.getOrDefault(false)
        if (!ok) labToast(context, "写入失败：请确认已授予写入安全设置权限")
    }

    /** 原值记录的偏好键。 */
    private fun captureKey(key: String): String = "cap_$key"

    private companion object {
        /** 关闭「开放网络可用」提示的系统键。 */
        const val KEY_WIFI_NOTIF: String = "wifi_networks_available_notification_on"

        /** 时钟屏保的系统键。 */
        const val KEY_SCREENSAVER: String = "screensaver_enabled"

        /** 关闭状态的取值。 */
        const val VALUE_OFF: Int = 0

        /** 系统设置的基线取值。 */
        const val BASELINE_ON: Int = 1

        /** 记录原值的偏好文件名。 */
        const val POWER_PREFS: String = "power_cfg"

        /** Shizuku 包名。 */
        const val SHIZUKU_PACKAGE: String = "moe.shizuku.privileged.api"

        /** 单键开关列表。 */
        val WIFI_NOTIF_KEYS: List<String> = listOf(KEY_WIFI_NOTIF)

        /** 屏保开关列表。 */
        val SCREENSAVER_KEYS: List<String> = listOf(KEY_SCREENSAVER)

        /** 三档动画的系统键。 */
        val ANIM_KEYS: List<String> =
            listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
    }
}
