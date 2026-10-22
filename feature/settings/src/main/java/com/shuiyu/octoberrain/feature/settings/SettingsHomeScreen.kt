package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkGlyphs
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.design.InkWidgets

/**
 * 设置中心首页。
 *
 * 版式对齐图墨桌面设置页：不设标题栏与返回箭头，页面顶部为齿轮徽标与「十雨」字样，
 * 下方 2 列系统设置卡片。首行为「微读墨记」与「阅读与数据」，
 * 其后为「桌面偏好」「美化与图标」「网络与互联网」等系统设置卡片。
 * 卡片尺寸、描边与圆角全部取自设计令牌，页面内容位于 InkPage 的滚动容器内。
 * 返回操作由系统返回键承担。
 */
class SettingsHomeScreen : SettingsBaseScreen(null, showBack = false) {
    override fun build(
        page: InkPage,
        config: LauncherConfig,
    ) {
        val context = page.context
        page.content.addView(buildWordmark(context))
        page.content.addView(buildCards(context))
        SettingsUi.note(
            page,
            "书架设置位于负一屏设置；书源与行数在书架标题行调整。" +
                "「桌面偏好」集中外观、桌面、托盘、锁屏与权限等十雨桌面专属设置；" +
                "「美化与图标」按桌面顺序整理应用并更换图标；其余卡片直接跳转系统设置页。",
        )
    }

    /**
     * 顶部标识区：左侧 40dp 圆角方块内绘制齿轮，右侧「十雨」标题。
     *
     * @param context 页面上下文。
     * @return 横向排布的标题栏视图。
     */
    private fun buildWordmark(context: Context): View {
        val tokens = DesignRuntime.tokens
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

        val badge =
            FrameLayout(context).apply {
                background =
                    InkWidgets.background(
                        tokens.dp(10f),
                        InkColors.PAPER,
                        tokens.stroke,
                        InkColors.INK,
                    )
            }
        val gearSize = tokens.dp(22f)
        badge.addView(
            InkGlyphs.view(context, InkGlyphs.Kind.GEAR, gearSize),
            FrameLayout.LayoutParams(gearSize, gearSize, Gravity.CENTER),
        )
        val badgeSize = tokens.dp(40f)
        row.addView(
            badge,
            LinearLayout.LayoutParams(badgeSize, badgeSize).apply {
                rightMargin = tokens.dp(12f)
            },
        )

        val title = InkWidgets.textView(context, "十雨", tokens.textTitle, InkColors.INK)
        row.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        row.layoutParams =
            LinearLayout
                .LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = tokens.gapLarge }
        return row
    }

    /**
     * 2 列卡片网格。
     *
     * @param context 页面上下文。
     * @return 纵向排列的网格容器，行内两张卡片等宽等高。
     */
    private fun buildCards(context: Context): View {
        val tokens = DesignRuntime.tokens
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val gap = tokens.dp(10f)
        val columnGap = tokens.dp(10f)

        cards().chunked(2).forEachIndexed { rowIndex, rowCards ->
            if (rowIndex > 0) {
                grid.addView(
                    View(context),
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        gap,
                    ),
                )
            }
            val row =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    isBaselineAligned = false
                }
            rowCards.forEachIndexed { index, spec ->
                if (index > 0) {
                    row.addView(
                        View(context),
                        LinearLayout.LayoutParams(
                            columnGap,
                            LinearLayout.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }
                row.addView(
                    buildCard(context, spec),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
            }
            // 卡片总数为奇数时，末行补一个透明占位保持半宽栅格，避免单张卡片拉满整行。
            if (rowCards.size == 1) {
                row.addView(
                    View(context),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
            }
            grid.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            equalizeRow(row)
        }
        return grid
    }

    /**
     * 单张设置卡片。
     *
     * @param context 页面上下文。
     * @param spec 卡片的标签、可选说明、图标与点击动作。
     * @return 白底描边卡片视图，图标居左上、标签居左下。
     */
    private fun buildCard(
        context: Context,
        spec: CardSpec,
    ): View {
        val tokens = DesignRuntime.tokens
        val cardHeight = tokens.dp(104f)
        val card =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                minimumHeight = cardHeight
                setPadding(tokens.dp(14f), tokens.dp(14f), tokens.dp(14f), tokens.dp(14f))
                background =
                    InkWidgets.background(
                        tokens.radius,
                        InkColors.PAPER,
                        tokens.stroke,
                        InkColors.INK,
                    )
                isClickable = true
                isFocusable = true
                setOnClickListener { spec.onClick(context) }
            }

        val iconSize = tokens.dp(22f)
        card.addView(spec.icon(context), LinearLayout.LayoutParams(iconSize, iconSize))

        val label = InkWidgets.textView(context, spec.label, tokens.textSize(16f), InkColors.INK)
        label.isSingleLine = true
        label.ellipsize = TextUtils.TruncateAt.END
        card.addView(
            label,
            LinearLayout
                .LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = tokens.dp(10f) },
        )

        if (spec.hint != null) {
            val hint = InkWidgets.textView(context, spec.hint, tokens.textCaption, InkColors.INK_3)
            hint.isSingleLine = true
            hint.ellipsize = TextUtils.TruncateAt.END
            card.addView(
                hint,
                LinearLayout
                    .LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = tokens.dp(2f) },
            )
        }

        addPressFeedback(card)
        return card
    }

    /**
     * 让同一行的卡片等高，避免带说明行的卡片与相邻卡片高低不齐。
     *
     * @param row 已加入卡片的横向容器。
     */
    private fun equalizeRow(row: LinearLayout) {
        row.post {
            var tallest = 0
            for (index in 0 until row.childCount) {
                tallest = maxOf(tallest, row.getChildAt(index).height)
            }
            if (tallest <= 0) return@post
            for (index in 0 until row.childCount) {
                val child = row.getChildAt(index)
                val params = child.layoutParams as? LinearLayout.LayoutParams ?: continue
                if (params.width == 0) {
                    params.height = tallest
                    child.layoutParams = params
                }
            }
        }
    }

    /** 按下时降低透明度，提供触感反馈。 */
    private fun addPressFeedback(view: View) {
        view.setOnTouchListener { target, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> target.alpha = 0.55f
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> target.alpha = 1f
            }
            false
        }
    }

    /** 十张卡片的定义与点击行为。 */
    private fun cards(): List<CardSpec> =
        listOf(
            CardSpec(
                label = "微读墨记",
                hint = "阅读、设置、实验室与待办",
                icon = glyph(InkGlyphs.Kind.BOOK),
            ) {
                runCatching { AppServices.router.navigate(Destination.WereadApp) }
                    .onFailure { error -> SettingsUi.toast(it, "无法打开微读墨记：${error.message ?: "未知错误"}") }
            },
            CardSpec(
                label = "桌面偏好",
                hint = "外观、桌面、托盘、锁屏与权限",
                icon = custom(CustomGlyph.DESKTOP),
            ) {
                runCatching { AppServices.router.navigate(Destination.LauncherPrefs) }
                    .onFailure { error -> SettingsUi.toast(it, "无法打开桌面偏好：${error.message ?: "未知错误"}") }
            },
            CardSpec(
                label = "美化与图标",
                hint = "更换图标、保存主题",
                icon = custom(CustomGlyph.ICON),
            ) {
                runCatching { AppServices.router.navigate(Destination.Beautify) }
                    .onFailure { error -> SettingsUi.toast(it, "无法打开美化模式：${error.message ?: "未知错误"}") }
            },
            CardSpec("网络与互联网", icon = glyph(InkGlyphs.Kind.WIFI)) {
                openSystem(it, Settings.ACTION_WIFI_SETTINGS)
            },
            CardSpec("蓝牙与连接", icon = glyph(InkGlyphs.Kind.BLUETOOTH)) {
                openSystem(it, Settings.ACTION_BLUETOOTH_SETTINGS)
            },
            CardSpec("应用管理", icon = glyph(InkGlyphs.Kind.LIST)) {
                openSystem(it, Settings.ACTION_APPLICATION_SETTINGS)
            },
            CardSpec("电源与显示", icon = glyph(InkGlyphs.Kind.BATTERY)) {
                openSystem(it, Settings.ACTION_DISPLAY_SETTINGS)
            },
            CardSpec("存储空间", icon = glyph(InkGlyphs.Kind.FOLDER)) {
                openSystem(it, Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            },
            CardSpec("设备信息", icon = custom(CustomGlyph.DEVICE)) {
                openSystem(it, Settings.ACTION_DEVICE_INFO_SETTINGS)
            },
            CardSpec("系统设置", icon = glyph(InkGlyphs.Kind.GEAR)) {
                runCatching { AppServices.router.navigate(Destination.SystemSettings) }
                    .onFailure { error -> SettingsUi.toast(it, "无法打开系统设置：${error.message ?: "未知错误"}") }
            },
        )

    /** 使用设计层线性图标。 */
    private fun glyph(kind: InkGlyphs.Kind): (Context) -> View =
        { context ->
            InkGlyphs.view(context, kind, DesignRuntime.tokens.dp(22f))
        }

    /** 使用本页自绘图标。 */
    private fun custom(kind: CustomGlyph): (Context) -> View =
        { context ->
            CardGlyphView(context, kind, DesignRuntime.tokens.dp(22f))
        }

    /**
     * 跳转系统设置页。
     *
     * 先解析目标 action 是否有可处理页面，缺失时回退到 ACTION_SETTINGS；
     * 回退仍失败时给出提示。全过程在主线程执行并捕获异常。
     *
     * @param context 页面上下文。
     * @param action 目标系统设置 action。
     */
    private fun openSystem(
        context: Context,
        action: String,
    ) {
        val target = Intent(action)
        val resolved =
            runCatching {
                target.resolveActivity(context.packageManager)
            }.getOrNull()

        val primaryLaunched =
            if (resolved != null) {
                runCatching { context.startActivity(target) }.isSuccess
            } else {
                false
            }
        if (primaryLaunched) return

        val fallback = Intent(Settings.ACTION_SETTINGS)
        val fallbackLaunched = runCatching { context.startActivity(fallback) }.isSuccess
        if (!fallbackLaunched) {
            SettingsUi.toast(context, "无法打开系统设置")
        }
    }

    /** 卡片定义。 */
    private data class CardSpec(
        val label: String,
        val hint: String? = null,
        val icon: (Context) -> View,
        val onClick: (Context) -> Unit,
    )

    /** 需要自绘的图标种类。 */
    private enum class CustomGlyph {
        /** 手机轮廓，用于设备信息。 */
        DEVICE,

        /** 四宫格，用于桌面偏好。 */
        DESKTOP,

        /** 图片画框，用于美化与图标。 */
        ICON,
    }

    /**
     * 自绘线性图标视图，尺寸固定为 22dp。
     *
     * 线宽与设计层 InkGlyphs 保持一致，按视图边长比例计算。
     */
    private class CardGlyphView(
        context: Context,
        private val kind: CustomGlyph,
        private val sizePx: Int,
    ) : View(context) {
        private val paint = DesignRuntime.fillPaint(InkColors.INK)

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            setMeasuredDimension(sizePx, sizePx)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val size = minOf(width, height) * 0.82f
            val half = size / 2f
            val left = width / 2f - half
            val top = height / 2f - half
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = maxOf(1.6f, size * 0.09f)
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            when (kind) {
                CustomGlyph.DEVICE -> {
                    val rect =
                        RectF(
                            left + size * 0.28f,
                            top + size * 0.06f,
                            left + size * 0.72f,
                            top + size * 0.94f,
                        )
                    canvas.drawRoundRect(rect, size * 0.12f, size * 0.12f, paint)
                    canvas.drawLine(
                        left + size * 0.46f,
                        top + size * 0.82f,
                        left + size * 0.54f,
                        top + size * 0.82f,
                        paint,
                    )
                }

                CustomGlyph.DESKTOP -> {
                    val cell = size * 0.4f
                    val radius = size * 0.08f
                    listOf(0.02f to 0.02f, 0.58f to 0.02f, 0.02f to 0.58f, 0.58f to 0.58f)
                        .forEach { (dx, dy) ->
                            val rect =
                                RectF(
                                    left + size * dx,
                                    top + size * dy,
                                    left + size * dx + cell,
                                    top + size * dy + cell,
                                )
                            canvas.drawRoundRect(rect, radius, radius, paint)
                        }
                }
                CustomGlyph.ICON -> {
                    // 画框内放置太阳与山形折线，表达图片与图标替换。
                    val rect =
                        RectF(
                            left + size * 0.08f,
                            top + size * 0.08f,
                            left + size * 0.92f,
                            top + size * 0.92f,
                        )
                    canvas.drawRoundRect(rect, size * 0.14f, size * 0.14f, paint)
                    canvas.drawCircle(
                        left + size * 0.36f,
                        top + size * 0.34f,
                        size * 0.09f,
                        paint,
                    )
                    val path =
                        android.graphics.Path().apply {
                            moveTo(left + size * 0.18f, top + size * 0.80f)
                            lineTo(left + size * 0.42f, top + size * 0.52f)
                            lineTo(left + size * 0.58f, top + size * 0.70f)
                            lineTo(left + size * 0.70f, top + size * 0.58f)
                            lineTo(left + size * 0.86f, top + size * 0.80f)
                        }
                    canvas.drawPath(path, paint)
                }
            }
        }
    }
}
