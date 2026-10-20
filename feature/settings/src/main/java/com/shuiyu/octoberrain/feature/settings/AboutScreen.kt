package com.shuiyu.octoberrain.feature.settings

import android.os.Build
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.device.DeviceRuntime

/**
 * 关于本机页面。
 *
 * 展示应用版本、构建类型与设备画像，并提供检查更新入口。
 */
class AboutScreen : SettingsBaseScreen("关于本机") {
    override fun build(
        page: InkPage,
        config: LauncherConfig,
    ) {
        val context = page.context

        val app = SettingsUi.section(page, "应用", "版本与构建信息")
        app.info("应用名称", "十雨桌面")
        app.info("版本名", AppPackageInfo.versionName(context))
        app.info("版本号", AppPackageInfo.versionCode(context).toString())
        app.info("构建类型", if (AppPackageInfo.isDebuggable(context)) "调试构建" else "发布构建")
        app.info("配置版本", config.version.toString())
        app.info("数据来源", ThirdPartyLicenses.WEREAD_SOURCE_NOTICE)

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

        val update = SettingsUi.section(page, "更新", "版本检查与更新日志")
        update.action(
            label = "检查更新",
            value = "当前 ${AppPackageInfo.versionName(context)}",
            hint = "读取更新地址并比较版本号",
        ) {
            AppServices.router.navigate(Destination.Update)
        }

        val licenses = SettingsUi.section(page, "开源许可", "第三方项目与许可协议")
        ThirdPartyLicenses.entries.forEach { entry ->
            licenses.info(entry.name, entry.summary)
        }
        SettingsUi.note(page, ThirdPartyLicenses.WEREAD_SOURCE_DETAIL)

        SettingsUi.note(
            page,
            "本应用为墨水屏阅读设备定制，界面按纸面阅读场景设计；更新安装需要系统允许安装未知来源应用。",
        )
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
