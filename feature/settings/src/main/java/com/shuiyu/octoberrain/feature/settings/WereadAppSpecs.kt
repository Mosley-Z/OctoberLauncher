package com.shuiyu.octoberrain.feature.settings

import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.design.InkPage

/**
 * 设置页的微读墨记入口声明。
 *
 * 桌面偏好页通过 [render] 生成一个分组与一条跳转行，点击进入
 * [Destination.WereadApp] 全屏页面。入口条目不进入 [SettingsCatalog] 的声明表，
 * 因为该页面由 feature/weread-app 独立注册，无需参与设置项标识与分组的全局校验。
 */
internal object WereadAppSpecs {
    /** 微读墨记入口分组标识。 */
    const val GROUP_WEREAD_APP: String = "weread.app"

    /** 打开微读墨记条目标识。 */
    const val SPEC_OPEN: String = "weread.app.open"

    /** 入口分组声明，标题与说明与页面功能一致。 */
    val groups: List<SettingsGroupSpec> =
        listOf(
            SettingsGroupSpec(
                id = GROUP_WEREAD_APP,
                title = "微读墨记",
                subtitle = "阅读、设置、实验室与待办",
                notes = listOf("页面内的显示与卡片设置即时写入配置，返回桌面后卡片与负一屏随之重绘。"),
            ),
        )

    /** 入口条目声明：点击行为由桌面偏好页提供。 */
    val specs: List<SettingSpec> =
        listOf(
            SettingSpec(
                id = SPEC_OPEN,
                group = GROUP_WEREAD_APP,
                title = "打开微读墨记",
                hint = "以全屏页面打开：阅读、设置、实验室与待办",
                control = SettingControl.Action(value = { "打开" }),
                effect = SettingEffect.NONE,
            ),
        )

    /**
     * 在页面底部生成微读墨记入口分组。
     *
     * 参数 page 为承载内容的设置页；点击行时经路由打开微读墨记页面。
     */
    fun render(page: InkPage) {
        val group = groups.first()
        val spec = specs.first()
        val action = spec.control as SettingControl.Action
        val value = action.value?.invoke(LauncherConfig())
        val section = SettingsUi.section(page, group.title, group.subtitle)
        section.action(
            label = spec.title,
            value = value,
            hint = spec.hint,
        ) {
            AppServices.router.navigate(Destination.WereadApp)
        }
        group.notes.forEach { note -> SettingsUi.note(page, note) }
    }
}
