package com.shuiyu.octoberrain.feature.wereadapp.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.feature.wereadapp.WereadAppPrefs
import com.shuiyu.octoberrain.feature.wereadapp.WereadAppSettings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 翻页段。
 *
 * 仅保留不依赖外部设备的分组骨架：连接方式三选一与角色三选一、
 * 当前状态胶囊、折叠注意事项；热点、蓝牙、远程会话与本机晃动四块不再展示。
 * 段末保留实验内容（数据目录、文件状态与偏好快照）与无障碍入口。
 *
 * @param context 创建视图的上下文
 * @param onLabCollapsed 实验内容收起后的回调
 */
@Suppress("TooManyFunctions")
internal class WereadLabFlipPage(
    context: Context,
    private val onLabCollapsed: () -> Unit,
) : LabPageView(context) {
    /** 时间格式，展示文件修改时间。 */
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun render() {
        addConnectionSection()
        addRoleSection()
        addNotesSection()
        addLabSection()
        addAccessibilityEntry()
    }

    /** 连接方式分组：整组置灰，只作形态展示。 */
    private fun addConnectionSection() {
        content.addView(labSectionTitle(context, "连接方式"))
        content.addView(labRadioGroup(context, CONNECTION_LABELS, selectedIndex = 0, enabled = false))
        content.addView(labCaption(context, "由微读墨记 App 维护；十雨桌面只展示分组形态。"))
        content.addView(labSpacer(context))
        content.addView(labPill(context, "当前：关闭"))
    }

    /** 角色分组：单选并持久化，说明文案随角色变化。 */
    private fun addRoleSection() {
        val store = WereadAppPrefs.store(context)
        val role = store.getInt(KEY_REMOTE_ROLE, ROLE_OFF).coerceIn(ROLE_OFF, ROLE_PHONE)
        content.addView(labSectionTitle(context, "角色"))
        content.addView(
            labRadioGroup(context, ROLE_LABELS, role) { index ->
                store.putInt(KEY_REMOTE_ROLE, index)
                store.flush()
                refresh()
            },
        )
        content.addView(labCaption(context, roleHint(role)))
    }

    /** 折叠注意事项。 */
    private fun addNotesSection() {
        val body =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(labCaption(context, "· 同一时间只能有一个应用的无障碍服务接管音量键。"))
                addView(labCaption(context, "· 连接方式与角色由微读墨记 App 维护，本页只同步展示。"))
            }
        content.addView(labSpacer(context))
        content.addView(labFold(context, "注意事项", body))
    }

    /** 实验内容：未展开时给出门禁提示，展开后展示目录、文件与偏好快照。 */
    private fun addLabSection() {
        content.addView(labSpacer(context, 2f))
        content.addView(labSectionTitle(context, "实验内容"))
        if (!WereadAppPrefs.labEnabled(context)) {
            addLabGate()
            return
        }
        content.addView(labCaption(context, "数据目录：${WereadAppPrefs.dataDir(context).absolutePath}"))
        content.addView(labSpacer(context))
        addFileSection()
        addPrefsSection()
        addLabActions()
    }

    /** 实验内容未展开时的说明与版本行。 */
    private fun addLabGate() {
        content.addView(labCaption(context, "实验内容尚未展开。"))
        content.addView(labCaption(context, "在设置页连续点击版本号五次即可展开，展开后显示数据目录与偏好快照。"))
        content.addView(labSpacer(context))
        content.addView(InkWidgets.labelRow(context, "版本", WereadAppPrefs.versionName(context)))
    }

    /** 数据目录文件清单。 */
    private fun addFileSection() {
        content.addView(labDivider(context))
        val files =
            WereadAppPrefs
                .dataDir(context)
                .listFiles()
                ?.sortedBy { it.name }
                .orEmpty()
        if (files.isEmpty()) {
            content.addView(labCaption(context, "数据目录为空"))
            return
        }
        files.forEach { file ->
            content.addView(
                InkWidgets.labelRow(
                    context,
                    file.name,
                    "${sizeText(file)}  ${timeFormat.format(Date(file.lastModified()))}",
                ),
            )
        }
    }

    /** 偏好快照：基础项、外观项与实验内容状态。 */
    private fun addPrefsSection() {
        content.addView(labSpacer(context, 2f))
        content.addView(labSectionTitle(context, "偏好快照"))
        content.addView(labDivider(context))
        val settings = WereadAppPrefs.settings(context)
        addPrefsRowsBasic(settings)
        addPrefsRowsAppearance(settings)
    }

    /** 偏好快照基础行。 */
    private fun addPrefsRowsBasic(settings: WereadAppSettings) {
        val binds =
            buildList {
                if (settings.bindWeek) add("本周")
                if (settings.bindMonth) add("本月")
                if (settings.bindBook) add("本书")
            }
        content.addView(
            InkWidgets.labelRow(context, "安装角色", if (settings.installRole == 0) "阅读器端" else "手机端"),
        )
        content.addView(
            InkWidgets.labelRow(context, "仅一页模式", if (settings.singlePage) "开" else "关"),
        )
        content.addView(InkWidgets.labelRow(context, "启用墨台", if (settings.deskEnabled) "开" else "关"))
        content.addView(
            InkWidgets.labelRow(context, "刷新绑定", binds.joinToString("、").ifEmpty { "无" }),
        )
        content.addView(
            InkWidgets.labelRow(context, "阅读成就提示", if (settings.achievementEnabled) "开" else "关"),
        )
        content.addView(InkWidgets.labelRow(context, "周目标档位", settings.weekKind.toString()))
        content.addView(InkWidgets.labelRow(context, "月目标档位", settings.monthKind.toString()))
        content.addView(
            InkWidgets.labelRow(context, "导出图纸张档位", settings.exportTemplate.toString()),
        )
        content.addView(
            InkWidgets.labelRow(context, "导出图字号档位", settings.exportSize.toString()),
        )
    }

    /** 偏好快照外观行。 */
    private fun addPrefsRowsAppearance(settings: WereadAppSettings) {
        content.addView(
            InkWidgets.labelRow(context, "导出图显示署名", if (settings.noteSign) "开" else "关"),
        )
        content.addView(
            InkWidgets.labelRow(context, "导出图显示日期", if (settings.noteDate) "开" else "关"),
        )
        content.addView(
            InkWidgets.labelRow(context, "榜单只取书架", if (settings.rankShelfOnly) "开" else "关"),
        )
        content.addView(
            InkWidgets.labelRow(context, "卡片总开关", if (settings.cardEnabled) "开" else "关"),
        )
        content.addView(InkWidgets.labelRow(context, "形态池掩码", settings.poolMask.toString()))
        content.addView(
            InkWidgets.labelRow(context, "月历热力图", if (settings.monthHeatmap) "开" else "关"),
        )
        content.addView(
            InkWidgets.labelRow(context, "实验内容", if (WereadAppPrefs.labEnabled(context)) "已展开" else "已收起"),
        )
    }

    /** 实验内容操作区：重开卡片与收起实验内容。 */
    private fun addLabActions() {
        content.addView(labSpacer(context, 2f))
        content.addView(
            labButtonRow(
                context,
                InkWidgets.button(context, "重开卡片") {
                    AppServices.refresh.requestPartial()
                    InkDialogs.toast(context, "已请求重绘桌面卡片")
                },
                InkWidgets.button(context, "收起实验内容") {
                    WereadAppPrefs.setLabEnabled(context, false)
                    InkDialogs.toast(context, "实验内容已收起")
                    onLabCollapsed()
                },
            ),
        )
    }

    /** 页尾无障碍入口。 */
    private fun addAccessibilityEntry() {
        content.addView(labSpacer(context, 2f))
        content.addView(
            labWideButton(context, "打开系统无障碍设置") {
                val opened =
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                        true
                    }.getOrDefault(false)
                if (!opened) {
                    InkDialogs.toast(context, "未能打开系统无障碍设置")
                }
            },
        )
        content.addView(labCaption(context, "遥控与音量键接管由系统无障碍服务负责，本页只提供入口。"))
    }

    /** 角色说明文案。 */
    private fun roleHint(role: Int): String =
        when (role) {
            ROLE_EINK -> "墨水屏作为被控端，手机通过热点或蓝牙发送翻页指令。"
            ROLE_PHONE -> "手机作为遥控端，接收墨水屏转发的事件。"
            else -> "不接管音量键，保持系统默认行为。"
        }

    /** 文件大小文本，超过 1 KB 显示 KB，超过 1 MB 显示 MB。 */
    private fun sizeText(file: File): String {
        val bytes = file.length()
        return when {
            bytes >= BYTES_PER_MB -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / BYTES_PER_MB)
            bytes >= BYTES_PER_KB -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / BYTES_PER_KB)
            else -> "$bytes B"
        }
    }

    private companion object {
        /** 角色偏好键。 */
        const val KEY_REMOTE_ROLE: String = "remote_role"

        /** 角色关闭。 */
        const val ROLE_OFF: Int = 0

        /** 角色墨水屏。 */
        const val ROLE_EINK: Int = 1

        /** 角色手机。 */
        const val ROLE_PHONE: Int = 2

        /** 连接方式文案。 */
        val CONNECTION_LABELS: List<CharSequence> =
            listOf("关闭（默认）", "热点连接 · Wi-Fi 直连（手机开热点）", "蓝牙连接 · 手机当蓝牙键盘（免热点）")

        /** 角色文案。 */
        val ROLE_LABELS: List<CharSequence> = listOf("关闭（默认）", "墨水屏", "手机")

        /** 每 KB 字节数。 */
        const val BYTES_PER_KB: Long = 1024L

        /** 每 MB 字节数。 */
        const val BYTES_PER_MB: Long = 1024L * 1024L
    }
}
