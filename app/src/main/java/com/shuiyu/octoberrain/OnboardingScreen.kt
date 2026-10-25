package com.shuiyu.octoberrain

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

    override fun createView(context: Context): View {
        this.context = context
        step =
            AppServices.config.state.value.onboarding.step
                .coerceIn(0, 5)
        val tokens = DesignRuntime.tokens
        page =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(InkColors.PAPER)
                setPadding(tokens.pageGutter, tokens.pageTop, tokens.pageGutter, tokens.pageBottom)
            }
        body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val scroll =
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
        render()
        return page
    }

    /** 渲染当前步骤。 */
    private fun render() {
        AppServices.config.edit { it.copy(onboarding = it.onboarding.copy(step = step)) }
        body.removeAllViews()
        when (step) {
            0 -> renderWelcome()
            1 -> renderLayout()
            2 -> renderWereadChoice()
            3 -> renderConnection()
            4 -> renderMode()
            else -> renderFinish()
        }
    }

    private fun renderWelcome() {
        body.addView(title("十雨桌面"))
        body.addView(paragraph("一个为墨水屏设计的极简桌面：没有动画、没有阴影，只有清晰的黑白与稳定的刷新。"))
        body.addView(paragraph("桌面支持应用宫格与列表两种形态，可放置阅读、天气、时钟、月历、图片等组件，并接管系统返回键与通知。"))
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
        body.addView(paragraph("1. 手机微信中打开 weread.qq.com/r/weread-skills，按官方提示登录并复制 API Key。"))
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
        AppServices.config.edit { it.copy(integrations = it.integrations.copy(wereadApiKey = key)) }
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
                        AppServices.wereadSync?.refresh(
                            com.shuiyu.octoberrain.core.common
                                .WereadRefreshRequest("book"),
                        ) { _, _ -> }
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

    private fun renderFinish() {
        body.addView(title("完成"))
        body.addView(paragraph("左右滑动翻页，长按图标进入整理模式，按返回键回到上一页。"))
        body.addView(paragraph("所有设置都可以在桌面的设置入口中修改，组件与布局的扩展会在后续版本中继续加入。"))
        body.addView(
            button("开始使用") {
                AppServices.config.edit { current ->
                    current.copy(
                        onboarding =
                            current.onboarding.copy(
                                completed = true,
                                createdAt = System.currentTimeMillis(),
                            ),
                    )
                }
                onFinished()
            },
        )
        body.addView(
            button("上一步", filled = false) {
                moveTo(if (AppServices.config.state.value.onboarding.wereadDecision == true) 3 else 2)
            },
        )
    }

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
            layoutParams =
                LinearLayout
                    .LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = DesignRuntime.tokens.gap }
        }

    /** 形态选择行，右侧显示当前选中。 */
    private fun choiceRow(
        label: String,
        selected: Boolean,
        onClick: () -> Unit,
    ): View {
        val row = InkWidgets.row(context, label, if (selected) "已选择" else "", onClick)
        row.layoutParams =
            LinearLayout
                .LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = DesignRuntime.tokens.gap }
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
                    5 ->
                        if (AppServices.config.state.value.onboarding.wereadDecision ==
                            true
                        ) {
                            3
                        } else {
                            2
                        }
                    ; else -> step - 1
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
        }
    }
}
