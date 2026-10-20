package com.shuiyu.octoberrain.feature.tray

import android.content.Context
import android.view.View
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.data.NotificationCenter
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.design.InkWidgets

/**
 * 通知展示设置页：控制折叠与归并规则、应用名显示以及桌面角标。
 *
 * 每次点击后重建内容，保证配置值与角标预览实时刷新；页面内容可滚动，
 * 适配短屏设备。返回键由宿主路由统一处理。
 */
class NotificationPrefsScreen : Screen {
    private lateinit var page: InkPage

    override fun createView(context: Context): View {
        page =
            InkPage(context, "通知展示") {
                runCatching { AppServices.router.back() }
            }
        build()
        return page
    }

    override fun onResume() {
        if (::page.isInitialized) build()
    }

    /** 依据当前配置重建全部行。 */
    private fun build() {
        val config = AppServices.config.state.value
        val context = page.context
        val content = page.content
        content.removeAllViews()

        content.addView(InkWidgets.caption(context, "折叠与归并"))
        content.addView(
            toggleRow(context, "按应用折叠", config.notifications.foldByApp) {
                edit { it.copy(notifications = it.notifications.copy(foldByApp = !config.notifications.foldByApp)) }
            },
        )
        content.addView(
            toggleRow(context, "合并同一会话", config.notifications.mergeConversation) {
                edit {
                    it.copy(
                        notifications =
                            it.notifications.copy(
                                mergeConversation = !config.notifications.mergeConversation,
                            ),
                    )
                }
            },
        )
        content.addView(
            toggleRow(context, "丢弃系统汇总条目", config.notifications.stripSummary) {
                edit {
                    it.copy(
                        notifications =
                            it.notifications.copy(
                                stripSummary = !config.notifications.stripSummary,
                            ),
                    )
                }
            },
        )

        content.addView(InkWidgets.spacer(context, InkWidgets.dp(context, 10f)))
        content.addView(InkWidgets.caption(context, "应用信息"))
        content.addView(
            toggleRow(context, "显示应用名", config.notifications.showAppLabel) {
                edit {
                    it.copy(
                        notifications =
                            it.notifications.copy(
                                showAppLabel = !config.notifications.showAppLabel,
                            ),
                    )
                }
            },
        )

        content.addView(InkWidgets.spacer(context, InkWidgets.dp(context, 10f)))
        content.addView(InkWidgets.caption(context, "桌面角标"))
        content.addView(
            toggleRow(context, "启用桌面角标", config.notifications.badgeEnabled) {
                edit {
                    it.copy(
                        notifications =
                            it.notifications.copy(
                                badgeEnabled = !config.notifications.badgeEnabled,
                            ),
                    )
                }
            },
        )
        content.addView(
            valueRow(context, "角标上限", "${config.notifications.badgeMax}") {
                edit {
                    it.copy(
                        notifications =
                            it.notifications.copy(
                                badgeMax = nextBadgeMax(config.notifications.badgeMax),
                            ),
                    )
                }
            },
        )
        content.addView(
            valueRow(context, "当前角标", currentBadgeLabel()) {
                runCatching { AppServices.refresh.requestPartial() }
            },
        )
        content.addView(
            valueRow(context, "清空全部通知", "执行") {
                runCatching { AppServices.notifications.dismissAll() }
                build()
            },
        )

        content.addView(
            InkWidgets.caption(
                context,
                "按应用折叠会把同一应用的多条通知合并为一条，数量显示在标题右侧，长按条目可撤销整组；" +
                    "合并同一会话仅在通知携带会话名时生效。角标上限决定桌面角标的最大显示值，" +
                    "超出时显示为“上限+”。",
            ),
        )
    }

    /** 应用配置修改并重建界面。 */
    private fun edit(transform: (LauncherConfig) -> LauncherConfig) {
        runCatching { AppServices.config.edit(transform) }
        build()
    }

    /** 开关行。 */
    private fun toggleRow(
        context: Context,
        label: String,
        value: Boolean,
        onClick: () -> Unit,
    ): View = InkWidgets.row(context, label, if (value) "开" else "关", onClick)

    /** 带当前值的动作行。 */
    private fun valueRow(
        context: Context,
        label: String,
        value: String,
        onClick: () -> Unit,
    ): View = InkWidgets.row(context, label, value, onClick)

    /** 角标上限循环档位，取值限定在 1 到 99 之间。 */
    private fun nextBadgeMax(current: Int): Int =
        when (current.coerceIn(1, 99)) {
            9 -> 99
            99 -> 9
            else -> 9
        }

    /** 角标预览文案；未启用时给出提示。 */
    private fun currentBadgeLabel(): String {
        val config = AppServices.config.state.value
        if (!config.notifications.badgeEnabled) return "已关闭"
        val text =
            runCatching { NotificationCenter.badgeText(config.notifications) }
                .getOrDefault("")
        return text.ifEmpty { "无" }
    }
}
