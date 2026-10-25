package com.shuiyu.octoberrain.feature.settings

import android.app.AlertDialog
import android.os.Build
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.device.DeviceRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * 关于本机页面。
 *
 * 展示设备画像与开源许可，由检查更新页底部进入。
 */
class AboutScreen : SettingsBaseScreen("关于") {
    override fun build(
        page: InkPage,
        config: LauncherConfig,
    ) {
        val context = page.context

        val device = SettingsUi.section(page, "设备", "当前运行环境")
        device.info("设备型号", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
        device.info("系统版本", "Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）")

        if (DeviceRuntime.isReady()) {
            val profile = DeviceRuntime.profile
            device.info("屏幕分辨率", "${profile.widthPx} × ${profile.heightPx} 像素")
            device.info("逻辑尺寸", "${profile.widthDp} × ${profile.heightDp} dp")
            device.info("屏幕密度", "${profile.density} dpi")
            device.info(
                "尺寸分类",
                "${sizeClassLabel(profile.sizeClass.name)} · ${heightClassLabel(profile.heightClass.name)}",
            )
            device.info("屏幕类型", displayLabel(profile.display.name))
            device.info("前光", if (profile.hasFrontLight) "支持" else "不支持")
            device.info("每行图标", "${profile.gridColumns()} 个")
        } else {
            device.info("设备画像", "尚未初始化")
        }

        buildLicenses(page)
        SettingsUi.note(page, ThirdPartyLicenses.WEREAD_SOURCE_DETAIL)
    }

    private fun buildLicenses(page: InkPage) {
        val context = page.context
        val licenses = SettingsUi.section(page, "开源许可", "第三方项目与许可协议")
        ThirdPartyLicenses.entries.forEach { entry ->
            val asset = entry.asset
            if (asset == null) {
                licenses.info(entry.name, entry.summary)
            } else {
                licenses.action(entry.name, entry.license, "查看许可原文") {
                    scope.launch {
                        try {
                            val text =
                                withContext(Dispatchers.IO) {
                                    context.assets
                                        .open(asset)
                                        .bufferedReader()
                                        .use { it.readText() }
                                }
                            AlertDialog
                                .Builder(context)
                                .setTitle(entry.name)
                                .setMessage(text)
                                .setPositiveButton("关闭", null)
                                .show()
                        } catch (_: IOException) {
                            AppServices.router.toast("许可文本暂不可用")
                        }
                    }
                }
            }
        }
    }

    /** 宽度分类转可读文本。 */
    private fun sizeClassLabel(name: String): String =
        when (name) {
            "Compact" -> "紧凑宽度"
            "Medium" -> "中等宽度"
            "Expanded" -> "宽屏"
            else -> name
        }

    /** 高度分类转可读文本。 */
    private fun heightClassLabel(name: String): String =
        when (name) {
            "Short" -> "低高度"
            "Normal" -> "常规高度"
            "Tall" -> "高屏"
            else -> name
        }

    /** 屏幕类型转可读文本。 */
    private fun displayLabel(name: String): String =
        when (name) {
            "EinkFast" -> "墨水屏 · 支持局部刷新"
            "EinkFull" -> "墨水屏 · 仅整屏刷新"
            "Lcd" -> "液晶屏"
            else -> name
        }
}
