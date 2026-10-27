package com.shuiyu.octoberrain

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.DesktopKind
import com.shuiyu.octoberrain.core.common.DesktopMode
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.common.WereadApiKeyEntry
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkWidgets

/**
 * 首次启动引导：布局、微信读书连接、桌面模式与完成说明。
 *
 * 每一步都独立渲染，返回上一步可重新选择；完成后写入 onboarding.completed，
 * 由宿主切换到桌面。
 */
class OnboardingScreen(
    private val onFinished: () -> Unit,
) : Screen {
    private var step = 0
    private val handler = Handler(Looper.getMainLooper())
    private var active = true
    private var verifying = false
    private var connectionMessage = ""
    private var requestId = 0
    private var entryDialog: android.app.Dialog? = null
    private var resumeKeyEntry = false
    private lateinit var context: Context
    private lateinit var body: LinearLayout
    private lateinit var page: LinearLayout
    private lateinit var navigation: LinearLayout
    private lateinit var scroll: ScrollView

    override fun createView(context: Context): View {
        this.context = context
        step =
            AppServices.config.state.value.onboarding.step
                .coerceIn(0, 7)
        val tokens = DesignRuntime.tokens
        page =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(InkColors.PAPER)
                setPadding(tokens.pageGutter, tokens.pageTop, tokens.pageGutter, tokens.pageBottom)
            }
        body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        scroll =
            ScrollView(context).apply {
                isFillViewport = true
                addView(
                    body,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        page.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        navigation = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        page.addView(navigation)
        render()
        return page
    }

    /** 渲染当前步骤。 */
    private fun render() {
        AppServices.config.edit { it.copy(onboarding = it.onboarding.copy(step = step)) }
        body.removeAllViews()
        navigation.removeAllViews()
        when (step) {
            0 -> renderWelcome()
            1 -> renderLayout()
            2 -> renderWereadChoice()
            3 -> renderConnection()
            4 -> renderMode()
            5 -> renderFeatures()
            6 -> renderSettingsGuide()
            else -> renderFinish()
        }
        placeNavigation()
    }

    private fun placeNavigation() {
        // 导航固定在正文之外，小屏与横屏均可触达。
        for (index in body.childCount - 1 downTo 0) {
            val child = body.getChildAt(index)
            if (child.tag == NAVIGATION_TAG) {
                body.removeViewAt(index)
                navigation.addView(child, 0)
            }
        }
        if (navigation.childCount == 3) {
            val secondary = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            repeat(2) { index ->
                val child = navigation.getChildAt(1)
                navigation.removeViewAt(1)
                secondary.addView(
                    child,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        topMargin = DesignRuntime.tokens.dp(12f)
                        if (index == 0) marginEnd = DesignRuntime.tokens.dp(12f)
                    },
                )
            }
            navigation.addView(secondary)
        }
    }

    private fun renderWelcome() {
        body.addView(title("十雨桌面"))
        body.addView(paragraph("一个为墨水屏设计的极简桌面：没有动画、没有阴影，只有清晰的黑白与稳定的刷新。"))
        body.addView(paragraph("桌面支持应用宫格与列表，可放置微信读书、时钟、月历和图片等小组件。"))
        body.addView(paragraph("本机屏幕信息：" + deviceSummary()))
        body.addView(
            button("开始设置") {
                step = 1
                render()
            },
        )
    }

    private fun renderLayout() {
        body.addView(title("选择桌面形态"))
        body.addView(paragraph("宫格适合以图标为主的使用方式，列表适合条目较多、名称较长的场景。两种形态都可以在设置中随时切换。"))
        val current = AppServices.config.state.value.desktop.kind
        body.addView(
            choiceRow("宫格桌面", current == DesktopKind.GRID) {
                AppServices.config.edit { current ->
                    current.copy(desktop = current.desktop.copy(kind = DesktopKind.GRID))
                }
                render()
            },
        )
        body.addView(
            choiceRow("列表桌面", current == DesktopKind.LIST) {
                AppServices.config.edit { current ->
                    current.copy(desktop = current.desktop.copy(kind = DesktopKind.LIST))
                }
                render()
            },
        )
        body.addView(
            button("下一步") {
                step = 2
                render()
            },
        )
        body.addView(
            button("上一步", filled = false) {
                step = 0
                render()
            },
        )
    }

    private fun moveTo(next: Int) {
        resumeKeyEntry = false
        entryDialog?.dismiss()
        entryDialog = null
        AppServices.wereadKeyEntry?.stop()
        requestId++
        verifying = false
        step = next
        render()
        scroll.scrollTo(0, 0)
    }

    private fun renderWereadChoice() {
        body.addView(title("要在十雨上使用微信读书吗？"))
        body.addView(paragraph("连接后可显示阅读书架与统计，无需安装原微信读书 App。以后也可在设置中连接。"))
        body.addView(
            button("启用微信读书") {
                AppServices.config.edit { it.copy(onboarding = it.onboarding.copy(wereadDecision = true)) }
                moveTo(3)
            },
        )
        body.addView(
            button("暂不启用", filled = false) {
                AppServices.config.edit {
                    it.copy(
                        onboarding = it.onboarding.copy(wereadDecision = false),
                        integrations = it.integrations.copy(wereadEnabled = false),
                        desktop = it.desktop.copy(mode = DesktopMode.NORMAL),
                    )
                }
                moveTo(5)
            },
        )
        body.addView(button("上一步", filled = false) { moveTo(1) })
    }

    private fun renderConnection() {
        body.addView(title("连接微信读书"))
        body.addView(paragraph("1. 微信读书手机客户端 → 设置 → 微信读书 SKILL → 复制 API KEY。"))
        body.addView(paragraph("备用方式：在手机微信中打开 weread.qq.com/r/weread-skills，按官方提示获取。"))
        body.addView(paragraph("2. 扫描本机二维码，在手机页面粘贴并提交；回到墨水屏查看保存结果。"))
        body.addView(paragraph("手机与墨水屏需在可互通的局域网；提交后十雨会自动保存，再尝试验证连接。"))
        body.addView(button("手机扫码录入本机", filled = false) { startKeyEntry() })
        val input =
            EditText(context).apply {
                hint = "也可在本机输入 Key"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                isSingleLine = true
                setTextColor(InkColors.INK)
            }
        body.addView(input)
        body.addView(
            button(if (verifying) "正在验证…" else "保存并验证") {
                if (!verifying) acceptKey(input.text.toString())
            },
        )
        if (AppServices.config.state.value.integrations.wereadApiKey
                .isNotBlank()
        ) {
            body.addView(
                button("验证已保存的 Key", filled = false) {
                    if (!verifying) verifyKey()
                },
            )
        }
        if (connectionMessage.isNotEmpty()) body.addView(paragraph(connectionMessage))
        body.addView(
            button("稍后配置，进入普通桌面", filled = false) {
                AppServices.config.edit { it.copy(desktop = it.desktop.copy(mode = DesktopMode.NORMAL)) }
                moveTo(5)
            },
        )
        body.addView(button("上一步", filled = false) { moveTo(2) })
    }

    private fun startKeyEntry() {
        resumeKeyEntry = false
        val entry = AppServices.wereadKeyEntry
        val id = ++requestId
        val session =
            entry?.start(
                object : WereadApiKeyEntry.Listener {
                    override fun onAccepted(apiKey: String) {
                        handler.post { if (active && step == 3 && requestId == id) acceptKey(apiKey) }
                    }

                    override fun onClosed(reason: WereadApiKeyEntry.CloseReason) {
                        handler.post {
                            val currentSession = active && step == 3 && requestId == id
                            if (currentSession && reason != WereadApiKeyEntry.CloseReason.STOPPED) {
                                connectionMessage = "录入会话已结束，请重新扫码或在本机输入"
                                render()
                            }
                        }
                    }
                },
            )
        if (session == null) {
            connectionMessage = "无法启动局域网录入，请检查网络，或在本机输入 Key"
            render()
        } else {
            entryDialog = OnboardingConnection.showEntry(context, session)
        }
    }

    private fun acceptKey(value: String) {
        val key = value.trim()
        if (key.isEmpty()) {
            connectionMessage = "请先录入 Key"
            render()
            return
        }
        entryDialog?.dismiss()
        entryDialog = null
        AppServices.config.edit {
            it.copy(integrations = it.integrations.copy(wereadApiKey = key, wereadEnabled = true))
        }
        if (AppServices.config.state.value.integrations.wereadApiKey != key) {
            connectionMessage = "Key 保存失败，请重试"
            render()
            return
        }
        AppServices.wereadKeyEntry?.stop()
        verifyKey()
    }

    private fun verifyKey() {
        val key = AppServices.config.state.value.integrations.wereadApiKey
        verifying = true
        connectionMessage = "Key 已保存，正在验证连接"
        val id = ++requestId
        render()
        OnboardingConnection.verify(key) { result ->
            handler.post {
                if (active && step == 3 && requestId == id) {
                    verifying = false
                    if (result == OnboardingConnection.Result.CONNECTED) {
                        AppServices.config.edit { it.copy(integrations = it.integrations.copy(wereadEnabled = true)) }
                        AppServices.wereadSync?.syncNow { _, _ -> }
                        moveTo(4)
                    } else {
                        connectionMessage =
                            if (result == OnboardingConnection.Result.INVALID) {
                                "Key 已保存，但密钥无效，请重新获取后提交"
                            } else {
                                "暂时无法验证，请检查网络后重试；已保留待验证的 Key"
                            }
                        render()
                    }
                }
            }
        }
    }

    private fun renderMode() {
        body.addView(title("已连接，选择桌面模式"))
        body.addView(paragraph("普通桌面保留宫格或列表；沉浸模式以本书为主。以后可在桌面偏好中切换。"))
        listOf("进入普通桌面" to DesktopMode.NORMAL, "进入沉浸模式" to DesktopMode.IMMERSIVE).forEach { (label, mode) ->
            body.addView(
                button(label, filled = false) {
                    AppServices.config.edit { it.copy(desktop = it.desktop.copy(mode = mode)) }
                    moveTo(5)
                },
            )
        }
        body.addView(button("上一步", filled = false) { moveTo(3) })
    }

    private fun renderFeatures() {
        body.addView(title("认识十雨"))
        guide("桌面", "放置桌面小组件；长按空白处可更换背景、进入美化模式。点按应用打开，长按应用可拖动排布。")
        guide("负一屏", "在桌面首页向右滑，进入左侧负一屏，查看书架、最近图片、墨单与阅读统计。显示内容可在设置中调整。")
        guide("文件", "“文件”是十雨内置的文件管理器，支持文件浏览管理、多种文档阅读与照片处理。")
        body.addView(button("下一步") { moveTo(6) })
        body.addView(button("跳过介绍", filled = false) { moveTo(7) })
        body.addView(button("上一步", filled = false) { moveTo(previousSetupStep()) })
    }

    private fun renderSettingsGuide() {
        body.addView(title("设置从这里开始"))
        guide("桌面偏好", "调整桌面布局、外观、小组件、状态栏、墨水屏刷新、锁屏密码，以及权限与备份。")
        guide("美化与图标", "更换应用图标，调整应用顺序。")
        guide("微读墨记", "配置微信读书连接，查看阅读数据、笔记并管理待办。")
        guide("检查更新", "查看当前版本、更新日志，检查新版本。")
        body.addView(button("下一步") { moveTo(7) })
        body.addView(button("上一步", filled = false) { moveTo(5) })
    }

    private fun guide(
        label: String,
        description: String,
    ) {
        body.addView(InkWidgets.heading(context, label))
        body.addView(paragraph(description))
    }

    private fun renderFinish() {
        body.addView(title("最后一步，按需设置"))
        body.addView(paragraph("设置后返回仍会停留在这里。也可跳过，稍后在设置中调整。"))
        val isHome =
            com.shuiyu.octoberrain.core.device.HomeRole
                .isDefault(context)
        body.addView(
            choiceRow("主屏幕应用", isHome, if (isHome) "已设为十雨" else "未设置") {
                val activity = context as? Activity
                if (activity != null) {
                    com.shuiyu.octoberrain.core.device.HomeRole
                        .request(activity, 153)
                }
            },
        )
        val permissions = AppServices.permissions
        val snapshot = permissions.snapshot()
        finishPermission("存储访问", "用于访问文件、导入壁纸和字体", snapshot["storage"] == true) {
            permissions.openStorageSettings()
        }
        finishPermission("通知读取", "用于桌面通知角标", snapshot["notification_listener"] == true) {
            permissions.openNotificationListenerSettings()
        }
        finishPermission("无障碍服务", "用于悬浮状态栏交互", snapshot["accessibility"] == true) {
            permissions.openAccessibilitySettings()
        }
        body.addView(paragraph("若后台刷新受到系统限制，可按需调整电池优化。"))
        body.addView(button("电池优化设置", filled = false) { permissions.openBatterySettings() })
        body.addView(button("完成") { finish() })
        body.addView(button("跳过设置", filled = false) { finish() })
        body.addView(button("上一步", filled = false) { moveTo(6) })
    }

    private fun finishPermission(
        label: String,
        hint: String,
        granted: Boolean,
        open: () -> Unit,
    ) {
        body.addView(choiceRow(label, granted, if (granted) "已授权" else "未授权", open))
        body.addView(paragraph(hint))
    }

    private fun finish() {
        AppServices.config.edit {
            it.copy(onboarding = it.onboarding.copy(completed = true, createdAt = System.currentTimeMillis()))
        }
        if (AppServices.config.state.value.onboarding.completed) onFinished()
    }

    private fun previousSetupStep(): Int =
        if (AppServices.config.state.value.onboarding.wereadDecision == true) 3 else 2

    /** 标题文本。 */
    private fun title(text: String): View =
        InkWidgets.title(context, text).apply {
            setPadding(0, 0, 0, DesignRuntime.tokens.gap)
        }

    /** 说明段落。 */
    private fun paragraph(text: String): View =
        InkWidgets.body(context, text).apply {
            setPadding(0, 0, 0, DesignRuntime.tokens.gap)
        }

    /** 主按钮。 */
    private fun button(
        label: String,
        filled: Boolean = true,
        onClick: () -> Unit,
    ): View =
        InkWidgets.button(context, label, filled, onClick).apply {
            minimumHeight = DesignRuntime.tokens.dp(56f)
            setPadding(
                DesignRuntime.tokens.gapLarge,
                DesignRuntime.tokens.dp(8f),
                DesignRuntime.tokens.gapLarge,
                DesignRuntime.tokens.dp(8f),
            )
            if (label in listOf("开始设置", "下一步", "上一步", "跳过介绍", "完成", "跳过设置")) tag = NAVIGATION_TAG
            layoutParams =
                LinearLayout
                    .LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = DesignRuntime.tokens.dp(12f) }
        }

    /** 形态选择行，右侧显示当前选中。 */
    private fun choiceRow(
        label: String,
        selected: Boolean,
        status: String? = null,
        onClick: () -> Unit,
    ): View {
        val row = InkWidgets.row(context, label, status ?: if (selected) "已选择" else "", onClick)
        row.minimumHeight = DesignRuntime.tokens.dp(64f)
        row.setPadding(
            DesignRuntime.tokens.dp(12f),
            DesignRuntime.tokens.dp(10f),
            DesignRuntime.tokens.dp(12f),
            DesignRuntime.tokens.dp(10f),
        )
        row.background =
            InkWidgets.background(
                DesignRuntime.tokens.radius,
                InkColors.PAPER,
                DesignRuntime.tokens.stroke,
                if (selected) InkColors.INK else InkColors.INK_3,
            )
        row.layoutParams =
            LinearLayout
                .LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = DesignRuntime.tokens.dp(12f) }
        return row
    }

    /** 设备摘要文本。 */
    private fun deviceSummary(): String {
        val profile = com.shuiyu.octoberrain.core.device.DeviceRuntime.profile
        return "${profile.widthPx}×${profile.heightPx} 像素，" +
            "${profile.widthDp}×${profile.heightDp} dp，${profile.display.name}"
    }

    override fun onBack(): Boolean {
        if (step >
            0
        ) {
            moveTo(
                when (step) {
                    5 -> previousSetupStep()
                    else -> step - 1
                },
            )
        }
        return true
    }

    override fun onPause() {
        active = false
        requestId++
        if (verifying) {
            verifying = false
            connectionMessage = "验证已暂停，返回后可验证已保存的 Key"
        }
        resumeKeyEntry = step == 3 &&
            entryDialog?.isShowing == true &&
            AppServices.wereadKeyEntry?.isRunning == true
        entryDialog?.dismiss()
        entryDialog = null
        AppServices.wereadKeyEntry?.stop()
    }

    override fun onDestroy() {
        active = false
        entryDialog?.dismiss()
        entryDialog = null
        requestId++
        AppServices.wereadKeyEntry?.stop()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onResume() {
        active = true
        if (step == 3) {
            render()
            if (resumeKeyEntry) startKeyEntry()
        } else if (step == 7) {
            render()
        }
    }

    private companion object {
        const val NAVIGATION_TAG = "onboarding_navigation"
    }
}
