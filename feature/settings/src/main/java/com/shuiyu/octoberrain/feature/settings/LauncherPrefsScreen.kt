package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.DesktopKind
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.IntegrationsConfig
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkGlyphs
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.design.InkWidgets

/**
 * 桌面偏好分组页。
 *
 * 按纸面排印的低密度原则组织：分组标题为小号说明文字，组内为白底描边卡片，
 * 每行只保留 24dp 线稿图标、一行标题、右侧不超过 8 字的状态与箭头。
 * 入口顺序按使用频率排列：个性化、安全与系统、内容与数据、关于。
 */
class LauncherPrefsScreen : SettingsBaseScreen("桌面偏好") {
    override fun build(
        page: InkPage,
        config: LauncherConfig,
    ) {
        val context = page.context
        prefGroups(context, config).forEach { group ->
            addGroup(page, group)
        }
        SettingsUi.note(page, "改动立即写入本地配置，重启后保留。")
    }

    /**
     * 构建分组入口表。
     *
     * 参数 context 用于读取版本名；config 提供各行右侧状态文案。
     */
    private fun prefGroups(
        context: Context,
        config: LauncherConfig,
    ): List<PrefGroup> =
        listOf(
            PrefGroup(
                title = "微读墨记",
                entries =
                    listOf(
                        PrefEntry("阅读与待办", PrefGlyph.SHELF, "内置模块") {
                            navigate(Destination.WereadApp)
                        },
                    ),
            ),
            PrefGroup(
                title = "个性化",
                entries =
                    listOf(
                        PrefEntry("外观与主题", PrefGlyph.APPEARANCE, appearanceStatus(config)) {
                            navigate(Destination.Appearance)
                        },
                        PrefEntry("桌面与布局", PrefGlyph.GRID, desktopStatus(config)) {
                            navigate(Destination.DesktopPrefs)
                        },
                        PrefEntry("快捷托盘", PrefGlyph.TRAY, trayStatus(config)) {
                            navigate(Destination.TrayPrefs)
                        },
                        PrefEntry("通知展示", PrefGlyph.BELL, notificationStatus(config)) {
                            navigate(Destination.NotificationPrefs)
                        },
                        PrefEntry("钟面样式", PrefGlyph.CLOCK, clockStatus(config)) {
                            navigate(Destination.ClockStyle)
                        },
                    ),
            ),
            PrefGroup(
                title = "安全与系统",
                entries =
                    listOf(
                        PrefEntry("锁屏与安全", PrefGlyph.LOCK, lockStatus(config)) {
                            navigate(Destination.LockSettings)
                        },
                        PrefEntry("权限与授权", PrefGlyph.SHIELD, permissionStatus()) {
                            navigate(Destination.Permissions)
                        },
                    ),
            ),
            PrefGroup(
                title = "内容与数据",
                entries =
                    listOf(
                        PrefEntry("内容与数据", PrefGlyph.CONTENT, integrationsStatus(config)) {
                            showContentData()
                        },
                        PrefEntry("书架设置", PrefGlyph.SHELF, bookshelfStatus(config)) {
                            navigate(Destination.BookshelfSettings)
                        },
                        PrefEntry("备份与恢复", PrefGlyph.BACKUP, "本地备份") {
                            navigate(Destination.Backup)
                        },
                    ),
            ),
            PrefGroup(
                title = "关于",
                entries =
                    listOf(
                        PrefEntry("关于本机", PrefGlyph.INFO, AppPackageInfo.versionName(context)) {
                            navigate(Destination.About)
                        },
                        PrefEntry("检查更新", PrefGlyph.UPDATE, "手动检查") {
                            navigate(Destination.Update)
                        },
                    ),
            ),
        )

    /**
     * 追加一个分组：小号标题与白底描边卡片。
     *
     * 参数 page 为页面；group 为分组数据。
     */
    private fun addGroup(
        page: InkPage,
        group: PrefGroup,
    ) {
        val tokens = DesignRuntime.tokens
        val context = page.context
        if (page.content.childCount > 0) {
            page.content.addView(InkWidgets.spacer(context, tokens.gapLarge))
        }
        page.content.addView(
            InkWidgets.caption(context, group.title).apply {
                setPadding(0, 0, 0, tokens.gap / 2)
            },
        )
        val card =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = InkWidgets.background(tokens.radius, InkColors.PAPER, tokens.stroke, InkColors.INK)
            }
        group.entries.forEachIndexed { index, entry ->
            if (index > 0) card.addView(InkWidgets.divider(context))
            card.addView(buildRow(context, entry))
        }
        page.content.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    /** 构建单行：图标、标题、状态与箭头。 */
    private fun buildRow(
        context: Context,
        entry: PrefEntry,
    ): LinearLayout {
        val tokens = DesignRuntime.tokens
        val iconSize = tokens.dp(24f)
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = tokens.listRowHeight + tokens.gap
                setPadding(tokens.gap, tokens.gap / 2, tokens.gap, tokens.gap / 2)
            }
        row.addView(
            PrefGlyphView(context, entry.glyph, iconSize),
            LinearLayout.LayoutParams(iconSize, iconSize).apply {
                rightMargin = tokens.gap
            },
        )
        row.addView(
            titleView(context, entry.label),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(statusView(context, entry.status))
        row.addView(
            InkGlyphs.view(context, InkGlyphs.Kind.CHEVRON, tokens.dp(16f)),
            LinearLayout.LayoutParams(tokens.dp(16f), tokens.dp(16f)).apply {
                leftMargin = tokens.gap / 2
            },
        )
        row.setOnClickListener { entry.onOpen() }
        row.setOnTouchListener(pressFeedback)
        return row
    }

    /** 标题文本：单行省略，避免长标签挤压状态。 */
    private fun titleView(
        context: Context,
        label: CharSequence,
    ): TextView =
        InkWidgets.textView(context, label, DesignRuntime.tokens.textHeading, InkColors.INK).apply {
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }

    /** 状态文本：右对齐、弱化颜色，最多 8 字。 */
    private fun statusView(
        context: Context,
        status: CharSequence,
    ): TextView {
        val tokens = DesignRuntime.tokens
        return InkWidgets
            .textView(
                context,
                status.toString().take(STATUS_MAX_CHARS),
                tokens.textCaption,
                InkColors.INK_2,
            ).apply {
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.END
                setPadding(tokens.gap, 0, 0, 0)
            }
    }

    private val pressFeedback =
        View.OnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> view.alpha = 0.55f
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.alpha = 1f
            }
            false
        }

    /** 跳转到指定设置子页。 */
    private fun navigate(destination: Destination) {
        AppServices.router.navigate(destination)
    }

    // 以下为各行状态文案，长度控制在 8 字以内。

    private fun appearanceStatus(config: LauncherConfig): String {
        val appearance = config.appearance
        val theme = SettingsUi.themeLabel(appearance.themeId).take(2)
        return "$theme·${SettingsUi.percent(appearance.textScale)}"
    }

    private fun desktopStatus(config: LauncherConfig): String {
        val desktop = config.desktop
        val kind = if (desktop.kind == DesktopKind.LIST) "列表" else "宫格"
        return if (desktop.columns > 0) "$kind·${desktop.columns}列" else "$kind·自动"
    }

    private fun trayStatus(config: LauncherConfig): String = if (config.tray.enabled) "已启用" else "已停用"

    private fun notificationStatus(config: LauncherConfig): String {
        val notifications = config.notifications
        return if (notifications.badgeEnabled) "上限 ${notifications.badgeMax}" else "已关闭"
    }

    private fun clockStatus(config: LauncherConfig): String = if (config.appearance.twelveHour) "12 小时制" else "24 小时制"

    private fun lockStatus(config: LauncherConfig): String {
        if (config.lock.pinHash == null) return "未设 PIN"
        return if (config.lock.enabled) "已启用" else "已停用"
    }

    private fun permissionStatus(): String {
        val snapshot = runCatching { AppServices.permissions.snapshot() }.getOrDefault(emptyMap())
        if (snapshot.isEmpty()) return "未知"
        return "${snapshot.count { it.value }}/${snapshot.size} 授权"
    }

    private fun integrationsStatus(config: LauncherConfig): String {
        val integrations = config.integrations
        var count = 0
        if (integrations.bookshelfLocalEnabled) count += 1
        if (integrations.bookshelfWereadEnabled) count += 1
        if (integrations.koreaderEnabled) count += 1
        if (integrations.weatherEnabled) count += 1
        if (integrations.usageEnabled) count += 1
        return if (count == 0) "未启用" else "$count 个来源"
    }

    private fun bookshelfStatus(config: LauncherConfig): String = "${config.integrations.bookshelfRows} 行/页"

    /**
     * 打开内容与数据子页。
     *
     * 该子页不属于公共 Destination，使用全屏对话框承载，返回栏与系统返回键均可关闭。
     */
    private fun showContentData() {
        val context = contextOrNull() ?: return
        SettingsUi.overlayPage(context, "内容与数据") { overlay ->
            buildContentData(overlay)
        }
    }

    /** 填充内容与数据子页。 */
    private fun buildContentData(page: InkPage) {
        val sources = SettingsUi.section(page, "阅读来源", "本地书籍与微信读书的开关位于书架设置")
        sources.toggle(
            "KOReader 统计",
            get = { SettingsUi.settings().integrations.koreaderEnabled },
            hint = "读取 KOReader 的阅读时长与高亮",
        ) { enabled -> updateIntegrations { it.copy(koreaderEnabled = enabled) } }

        val weather = SettingsUi.section(page, "天气", "桌面组件使用的城市")
        weather.toggle(
            "天气组件",
            get = { SettingsUi.settings().integrations.weatherEnabled },
            hint = "关闭后天气组件显示占位",
        ) { enabled -> updateIntegrations { it.copy(weatherEnabled = enabled) } }
        weather.action(
            "城市名称",
            value =
                SettingsUi
                    .settings()
                    .integrations.weatherCity
                    .ifBlank { "未设置" },
            hint = "用于查询天气的城市名",
        ) {
            promptText("城市名称", SettingsUi.settings().integrations.weatherCity) { value ->
                updateIntegrations { it.copy(weatherCity = value) }
            }
        }

        val usage = SettingsUi.section(page, "使用统计", "从使用情况权限读取前台应用时长")
        usage.toggle(
            "使用统计",
            get = { SettingsUi.settings().integrations.usageEnabled },
            hint = "开启前需要在权限页授予使用情况访问",
        ) { enabled -> updateIntegrations { it.copy(usageEnabled = enabled) } }

        SettingsUi.note(page, "以上字段保存在本地配置中，修改后立即生效。")
    }

    /** 修改整合配置并请求局部刷新。 */
    private fun updateIntegrations(transform: (IntegrationsConfig) -> IntegrationsConfig) {
        SettingsUi.edit { config -> config.copy(integrations = transform(config.integrations)) }
        SettingsUi.refresh()
    }

    /** 弹出文本输入框，确认非空后写回配置。 */
    private fun promptText(
        title: CharSequence,
        initial: String,
        onDone: (String) -> Unit,
    ) {
        val context = contextOrNull() ?: return
        InkDialogs.input(context, title, initial = initial) { value ->
            if (value.isEmpty()) {
                SettingsUi.toast(context, "内容不能为空")
            } else {
                onDone(value)
            }
        }
    }

    /** 桌面偏好单个入口。 */
    private data class PrefEntry(
        val label: String,
        val glyph: PrefGlyph,
        val status: String,
        val onOpen: () -> Unit,
    )

    /** 桌面偏好分组。 */
    private data class PrefGroup(
        val title: String,
        val entries: List<PrefEntry>,
    )

    private companion object {
        /** 右侧状态最大字数。 */
        private const val STATUS_MAX_CHARS = 8
    }
}

/** 行首线稿图标类型。 */
private enum class PrefGlyph {
    APPEARANCE,
    GRID,
    TRAY,
    BELL,
    CLOCK,
    LOCK,
    SHIELD,
    CONTENT,
    SHELF,
    BACKUP,
    INFO,
    UPDATE,
}

/**
 * 24dp 线稿图标视图。
 *
 * 以描边绘制单一语义图形，尺寸由构造参数固定，避免行高随内容变化。
 */
private class PrefGlyphView(
    context: Context,
    private val glyph: PrefGlyph,
    private val sizePx: Int,
) : View(context) {
    private val paint =
        DesignRuntime
            .strokePaint(
                InkColors.INK,
                DesignRuntime.tokens.glyphStroke,
            ).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        setMeasuredDimension(sizePx, sizePx)
    }

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val radius = size * 0.28f
        when (glyph) {
            PrefGlyph.APPEARANCE -> {
                canvas.drawCircle(cx, cy, radius, paint)
                for (step in 0 until 6) {
                    val angle = Math.toRadians(step * 60.0)
                    val startX = cx + (radius * 1.4f * kotlin.math.cos(angle)).toFloat()
                    val startY = cy + (radius * 1.4f * kotlin.math.sin(angle)).toFloat()
                    val endX = cx + (radius * 1.9f * kotlin.math.cos(angle)).toFloat()
                    val endY = cy + (radius * 1.9f * kotlin.math.sin(angle)).toFloat()
                    canvas.drawLine(startX, startY, endX, endY, paint)
                }
            }

            PrefGlyph.GRID -> {
                val cell = size * 0.24f
                val gap = size * 0.06f
                for (row in 0 until 2) {
                    for (column in 0 until 2) {
                        val left = cx - cell - gap / 2 + column * (cell + gap)
                        val top = cy - cell - gap / 2 + row * (cell + gap)
                        canvas.drawRoundRect(
                            RectF(left, top, left + cell, top + cell),
                            size * 0.05f,
                            size * 0.05f,
                            paint,
                        )
                    }
                }
            }

            PrefGlyph.TRAY -> {
                val rect = RectF(cx - size * 0.3f, cy - size * 0.08f, cx + size * 0.3f, cy + size * 0.24f)
                canvas.drawRoundRect(rect, size * 0.07f, size * 0.07f, paint)
                canvas.drawLine(cx - size * 0.12f, cy - size * 0.08f, cx + size * 0.12f, cy - size * 0.08f, paint)
            }

            PrefGlyph.BELL -> {
                canvas.drawArc(
                    RectF(cx - radius, cy - radius * 1.1f, cx + radius, cy + radius),
                    180f,
                    180f,
                    false,
                    paint,
                )
                canvas.drawLine(cx - radius, cy, cx - radius, cy + radius * 0.5f, paint)
                canvas.drawLine(cx + radius, cy, cx + radius, cy + radius * 0.5f, paint)
                canvas.drawLine(
                    cx - radius * 1.15f,
                    cy + radius * 0.5f,
                    cx + radius * 1.15f,
                    cy + radius * 0.5f,
                    paint,
                )
                canvas.drawCircle(cx, cy + radius * 0.85f, size * 0.05f, paint)
            }

            PrefGlyph.CLOCK -> {
                canvas.drawCircle(cx, cy, radius, paint)
                canvas.drawLine(cx, cy, cx, cy - radius * 0.6f, paint)
                canvas.drawLine(cx, cy, cx + radius * 0.5f, cy, paint)
            }

            PrefGlyph.LOCK -> {
                canvas.drawRoundRect(
                    RectF(cx - radius, cy - radius * 0.2f, cx + radius, cy + radius * 0.9f),
                    size * 0.06f,
                    size * 0.06f,
                    paint,
                )
                canvas.drawArc(
                    RectF(cx - radius * 0.6f, cy - radius, cx + radius * 0.6f, cy + radius * 0.4f),
                    180f,
                    180f,
                    false,
                    paint,
                )
            }

            PrefGlyph.SHIELD -> {
                val path = Path()
                path.moveTo(cx, cy - radius * 1.1f)
                path.lineTo(cx + radius, cy - radius * 0.6f)
                path.lineTo(cx + radius * 0.75f, cy + radius * 0.45f)
                path.lineTo(cx, cy + radius * 1.15f)
                path.lineTo(cx - radius * 0.75f, cy + radius * 0.45f)
                path.lineTo(cx - radius, cy - radius * 0.6f)
                path.close()
                canvas.drawPath(path, paint)
            }

            PrefGlyph.CONTENT -> {
                for (step in 0 until 3) {
                    val y = cy - radius * 0.7f + step * radius * 0.7f
                    canvas.drawLine(cx - radius, y, cx + radius, y, paint)
                }
                canvas.drawLine(cx - radius, cy + radius * 0.7f, cx + radius * 0.2f, cy + radius * 0.7f, paint)
            }

            PrefGlyph.SHELF -> {
                canvas.drawLine(cx - radius, cy + radius * 0.9f, cx + radius, cy + radius * 0.9f, paint)
                canvas.drawRect(RectF(cx - radius, cy - radius, cx - radius * 0.35f, cy + radius * 0.7f), paint)
                canvas.drawRect(
                    RectF(cx - radius * 0.15f, cy - radius * 0.7f, cx + radius * 0.35f, cy + radius * 0.7f),
                    paint,
                )
                canvas.drawLine(cx + radius * 0.6f, cy - radius * 0.4f, cx + radius, cy + radius * 0.7f, paint)
            }

            PrefGlyph.BACKUP -> {
                canvas.drawRoundRect(
                    RectF(cx - radius, cy - radius * 0.4f, cx + radius, cy + radius),
                    size * 0.06f,
                    size * 0.06f,
                    paint,
                )
                canvas.drawLine(cx, cy - radius, cx, cy + radius * 0.1f, paint)
                canvas.drawLine(cx, cy - radius, cx - radius * 0.45f, cy - radius * 0.5f, paint)
                canvas.drawLine(cx, cy - radius, cx + radius * 0.45f, cy - radius * 0.5f, paint)
            }

            PrefGlyph.INFO -> {
                canvas.drawCircle(cx, cy, radius, paint)
                canvas.drawLine(cx, cy - radius * 0.35f, cx, cy + radius * 0.55f, paint)
                canvas.drawCircle(cx, cy - radius * 0.62f, size * 0.035f, paint)
            }

            PrefGlyph.UPDATE -> {
                canvas.drawArc(
                    RectF(cx - radius, cy - radius, cx + radius, cy + radius),
                    40f,
                    280f,
                    false,
                    paint,
                )
                canvas.drawLine(
                    cx + radius * 0.55f,
                    cy - radius * 0.95f,
                    cx + radius * 0.95f,
                    cy - radius * 0.55f,
                    paint,
                )
                canvas.drawLine(
                    cx + radius * 0.55f,
                    cy - radius * 0.95f,
                    cx + radius * 0.25f,
                    cy - radius * 0.45f,
                    paint,
                )
            }
        }
    }
}
