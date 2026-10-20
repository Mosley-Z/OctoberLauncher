package com.shuiyu.octoberrain

import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.DesktopKind
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkWidgets

/**
 * 首次启动引导：四步完成布局选择、权限授予与手势说明。
 *
 * 每一步都独立渲染，返回上一步可重新选择；完成后写入 onboarding.completed，
 * 由宿主切换到桌面。
 */
class OnboardingScreen(
    private val onFinished: () -> Unit,
) : Screen {
    private var step = 0
    private lateinit var context: Context
    private lateinit var body: LinearLayout
    private lateinit var page: LinearLayout

    override fun createView(context: Context): View {
        this.context = context
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
        body.removeAllViews()
        when (step) {
            0 -> renderWelcome()
            1 -> renderLayout()
            2 -> renderPermissions()
            else -> renderFinish()
        }
    }

    private fun renderWelcome() {
        body.addView(title("十雨桌面"))
        body.addView(paragraph("一个为墨水屏设计的极简桌面：没有动画、没有阴影，只有清晰的黑白与稳定的刷新。"))
        body.addView(paragraph("桌面支持应用宫格与列表两种形态，可放置阅读、天气、时钟、日历、使用统计等组件，并接管系统返回键与通知。"))
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

    private fun renderPermissions() {
        body.addView(title("授予必要权限"))
        body.addView(paragraph("通知读取用于在托盘中显示最近通知；悬浮窗用于状态栏覆盖；使用情况用于统计今日使用时长。全部为可选项，跳过不影响桌面基本功能。"))
        body.addView(
            permissionRow("通知监听", AppServices.notifications.canRead()) {
                AppServices.permissions.openNotificationListenerSettings()
            },
        )
        body.addView(
            permissionRow("悬浮窗", hasOverlay()) {
                AppServices.permissions.openOverlaySettings()
            },
        )
        body.addView(
            permissionRow("使用情况访问", hasUsage()) {
                AppServices.permissions.openUsageSettings()
            },
        )
        body.addView(
            permissionRow("通知权限", hasNotificationPermission()) {
                requestNotificationPermission()
            },
        )
        body.addView(
            button("下一步") {
                step = 3
                render()
            },
        )
        body.addView(
            button("上一步", filled = false) {
                step = 2 - 1
                render()
            },
        )
    }

    private fun renderFinish() {
        body.addView(title("完成"))
        body.addView(paragraph("左右滑动翻页，长按图标进入整理模式，从宫格向下滑动打开快捷托盘，按返回键回到上一页或收起托盘。"))
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
                step = 2
                render()
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

    /** 权限行，右侧显示授权状态。 */
    private fun permissionRow(
        label: String,
        granted: Boolean,
        onClick: () -> Unit,
    ): View {
        val row = InkWidgets.row(context, label, if (granted) "已授权" else "去授权", onClick)
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

    /** 是否已授予悬浮窗权限。 */
    private fun hasOverlay(): Boolean = Settings.canDrawOverlays(context)

    /** 是否已授予使用情况访问权限，判定逻辑见 core:device 的 UsageAccessChecker。 */
    private fun hasUsage(): Boolean =
        com.shuiyu.octoberrain.core.device.UsageAccessChecker
            .isGranted(context)

    /** 是否已授予通知运行时权限。 */
    private fun hasNotificationPermission(): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    /** 请求通知运行时权限，系统弹窗由宿主 Activity 处理。 */
    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val activity = context as? android.app.Activity
            activity?.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        } else {
            AppServices.permissions.openAppNotificationSettings()
        }
    }

    override fun onResume() {
        if (step == 2) render()
    }
}
