package com.shuiyu.octoberrain.feature.settings

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.DesktopLayoutSnapshot
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.LauncherEntry
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkGlyphs
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.design.InkWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File

/**
 * 美化模式页面骨架。
 *
 * 整屏显示：顶部为返回、标题、主题库入口与保存主题，中部左右分栏占据剩余高度，
 * 底部一行提供预览、导入、清空与保存，另有底部状态行替代弹窗提示。
 *
 * 各区域按职责拆到同一包内的四个文件中：左栏排序与图标见 BeautifyIconPanel.kt，
 * 右栏图片目录见 BeautifyImagePanel.kt，主题库见 BeautifyThemePanel.kt，
 * 分页装载与预览见 BeautifyPreviewPanel.kt。分页算法统一由 DesktopPagination 提供。
 */
class BeautifyScreen : Screen {
    /** 页面级协程作用域，随页面销毁取消。 */
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 页面根容器，未创建或已销毁时为 null。 */
    internal var root: LinearLayout? = null

    /** 上一次构建时的配置快照，用于回到前台时判断是否需要重建。 */
    private var lastConfig: LauncherConfig? = null

    /** 当前展示的桌面页下标，从 0 开始。 */
    internal var currentPage = 0

    /** 左栏当前选中的条目键，对应 LauncherEntry.key。 */
    internal var selectedKey: String? = null

    /** 内存中的完整页顺序，允许跨页移动；元素为 LauncherEntry.key。 */
    internal var workingPages: MutableList<MutableList<String>> = mutableListOf()

    /** 内存顺序是否已有未保存的改动。 */
    internal var pagesDirty = false

    /** 图片目录，进入页面时从共享存储读取，默认外部存储的 Pictures。 */
    internal var imageDir: File = ImageDirectoryStore.defaultDirectory

    /** 右栏当前展示已用图标而非图片目录。 */
    internal var showingOverrides = false

    /** 最近一次成功使用的图片路径，用于缩略图黑框标记。 */
    internal var selectedImagePath: String? = null

    /** 主题库对话框引用的页面，用于主题变更后就地重绘。 */
    internal var themePage: InkPage? = null

    /** 中部左右分栏容器，用于仅替换左栏。 */
    internal var cardRow: LinearLayout? = null

    /** 左栏应用列表卡片，切换页或选中项时就地替换。 */
    internal var leftCard: View? = null

    /** 左栏卡片内的行列表容器，页内上移或下移时直接交换子视图，保留滚动位置。 */
    internal var appList: LinearLayout? = null

    /** 条目键到左栏行视图的映射，用于就地重排与图标局部刷新。 */
    internal val appRows = HashMap<String, View>()

    /** 页面底部静态状态行，用于替代弹窗提示选图结果。 */
    internal var statusView: TextView? = null

    override fun createView(context: Context): View {
        val view =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(InkColors.PAPER)
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }
        root = view
        imageDir = ImageDirectoryStore.load(context)
        renderNow()
        lastConfig = AppServices.config.state.value
        return view
    }

    override fun onResume() {
        val config = AppServices.config.state.value
        if (root != null && (config != lastConfig || pageContentChanged(config))) {
            renderNow()
            lastConfig = config
        }
    }

    override fun onDestroy() {
        scope.cancel()
        themePage = null
        root = null
    }

    // ---- 页面骨架 ----

    /** 请求重建整页；已附着时推迟到下一帧，避免在触摸分发中移除视图。 */
    internal fun render() {
        val view = root ?: return
        if (view.isAttachedToWindow) {
            view.post { if (root === view) renderNow() }
        } else {
            renderNow()
        }
    }

    /** 按当前配置立即重建整页，保留当前页与选中条目。 */
    internal fun renderNow() {
        val view = root ?: return
        val context = view.context
        val config = AppServices.config.state.value
        val snapshot = snapshot(config)
        loadPages(snapshot)
        val entries = currentEntries(snapshot)
        val selected = entries.firstOrNull { it.key == selectedKey } ?: entries.firstOrNull()
        selectedKey = selected?.key

        view.removeAllViews()
        val middle = buildMiddle(context, entries, snapshot)
        view.addView(buildToolbar(context))
        view.addView(InkWidgets.divider(context))
        view.addView(
            middle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        val status = buildStatusLine(context)
        view.addView(status)
        view.addView(buildBottomActions(context))
        cardRow = middle as? LinearLayout
        leftCard = cardRow?.getChildAt(0)
        statusView = status
    }

    /** 底部状态行：默认隐藏，选图结果以静态文字呈现，不使用弹窗。 */
    private fun buildStatusLine(context: Context): TextView {
        val tokens = DesignRuntime.tokens
        return InkWidgets.textView(context, "", tokens.textSmall, InkColors.INK_3).apply {
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            setPadding(tokens.pageGutter, 0, tokens.pageGutter, tokens.gap / 4)
            visibility = View.GONE
        }
    }

    /** 显示一条静态状态文字，页面底部占用一行，不打断操作。 */
    internal fun showStatus(message: CharSequence) {
        val view = statusView ?: return
        view.text = message
        view.visibility = View.VISIBLE
    }

    /** 顶部工具栏：返回、标题与主题库入口。 */
    private fun buildToolbar(context: Context): View {
        val tokens = DesignRuntime.tokens
        val bar =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isBaselineAligned = false
                minimumHeight = tokens.topBarHeight
                setPadding(tokens.gap, tokens.gap / 4, tokens.gap, 0)
            }

        val back =
            InkGlyphs.view(context, InkGlyphs.Kind.BACK, tokens.dp(20f)).apply {
                isClickable = true
                isFocusable = true
                setOnClickListener { runCatching { AppServices.router.back() } }
            }
        bar.addView(back, LinearLayout.LayoutParams(tokens.dp(24f), tokens.dp(24f)))

        bar.addView(
            InkWidgets.textView(context, "美化模式", tokens.textHeading, InkColors.INK).apply {
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                setPadding(tokens.gap, 0, 0, 0)
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f,
                    )
            },
        )
        // 右侧两个等高的工具栏按钮：主题库与保存主题，横向占满标题之后的剩余宽度。
        val actions =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isBaselineAligned = false
            }
        actions.addView(toolbarButton(context, "主题库") { showThemeLibrary() })
        actions.addView(
            View(context),
            LinearLayout.LayoutParams(tokens.gap / 2, 1),
        )
        actions.addView(toolbarButton(context, "保存主题") { promptSaveTheme() })
        bar.addView(
            actions,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                tokens.dp(28f),
            ),
        )
        return bar
    }

    /** 工具栏按钮：与主题库同高，文字略小，宽度按文字自适应且留出更多内边距。 */
    private fun toolbarButton(
        context: Context,
        label: CharSequence,
        onClick: () -> Unit,
    ): TextView {
        val tokens = DesignRuntime.tokens
        return InkWidgets.textView(context, label, tokens.textCaption, InkColors.INK).apply {
            gravity = Gravity.CENTER
            minWidth = tokens.dp(64f)
            minimumWidth = tokens.dp(64f)
            setPadding(tokens.gap, 0, tokens.gap, 0)
            background =
                InkWidgets.background(
                    tokens.radiusSmall,
                    InkColors.PAPER,
                    tokens.stroke,
                    InkColors.INK,
                )
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    /** 中部左右分栏：左栏应用、右栏图片来源，均各自滚动。 */
    private fun buildMiddle(
        context: Context,
        entries: List<LauncherEntry>,
        snapshot: DesktopLayoutSnapshot,
    ): View {
        val tokens = DesignRuntime.tokens
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
                setPadding(tokens.gap, 0, tokens.gap, tokens.gap / 2)
            }
        row.addView(
            buildAppColumn(context, entries, snapshot),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.52f),
        )
        row.addView(
            View(context),
            LinearLayout.LayoutParams(tokens.gap / 2, LinearLayout.LayoutParams.MATCH_PARENT),
        )
        row.addView(
            buildImageColumn(context),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.48f),
        )
        return row
    }

    /** 底部操作行：预览、导入、清空与保存顺序，四个等宽按钮均带自绘线稿图标。 */
    private fun buildBottomActions(context: Context): View {
        val tokens = DesignRuntime.tokens
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isBaselineAligned = false
                setPadding(tokens.gap, 0, tokens.gap, tokens.gap / 2)
            }
        row.addView(
            iconTextButton(context, "预览", BottomIcon.SEARCH) { openPreview() },
            weightButtonParams(1f),
        )
        row.addView(gapView(context), weightButtonParams(0f).apply { width = tokens.gap / 2 })
        row.addView(
            iconTextButton(context, "导入", BottomIcon.IMPORT) { importTheme() },
            weightButtonParams(1f),
        )
        row.addView(gapView(context), weightButtonParams(0f).apply { width = tokens.gap / 2 })
        row.addView(
            iconTextButton(context, "清空", BottomIcon.TRASH) { confirmResetDesktop() },
            weightButtonParams(1f),
        )
        row.addView(gapView(context), weightButtonParams(0f).apply { width = tokens.gap / 2 })
        row.addView(
            iconTextButton(context, "保存", BottomIcon.SAVE) { saveOrder() },
            weightButtonParams(1f),
        )
        return row
    }

    /** 底部按钮：16dp 自绘线稿图标在左，文字在右，两者间距 4dp，整体等宽。 */
    private fun iconTextButton(
        context: Context,
        label: CharSequence,
        icon: BottomIcon,
        onClick: () -> Unit,
    ): LinearLayout {
        val tokens = DesignRuntime.tokens
        val button =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                isBaselineAligned = false
                background =
                    InkWidgets.background(
                        tokens.radiusSmall,
                        InkColors.PAPER,
                        tokens.stroke,
                        InkColors.INK,
                    )
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
        button.addView(
            BottomGlyphView(context, icon),
            LinearLayout.LayoutParams(tokens.dp(16f), tokens.dp(16f)).apply {
                rightMargin = tokens.dp(4f)
            },
        )
        button.addView(
            InkWidgets.textView(context, label, tokens.textBody, InkColors.INK).apply {
                isSingleLine = true
            },
        )
        return button
    }

    /** 只含一个确认按钮的短提示，用于保存成功反馈。 */
    internal fun showSavedNotice(context: Context) {
        val tokens = DesignRuntime.tokens
        val dialog = Dialog(context)
        val content =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(tokens.dialogPadding, tokens.dialogPadding, tokens.dialogPadding, tokens.dialogPadding)
                background = InkWidgets.background(tokens.radius, InkColors.PAPER, tokens.stroke, InkColors.INK)
            }
        content.addView(InkWidgets.heading(context, "已保存"))
        content.addView(
            InkWidgets.body(context, "桌面顺序已写入本机配置。"),
            LinearLayout
                .LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = tokens.gap / 2 },
        )
        content.addView(
            InkWidgets.button(context, "好", filled = true) { dialog.dismiss() },
            LinearLayout
                .LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    tokens.buttonHeight,
                ).apply {
                    gravity = Gravity.END
                    topMargin = tokens.gap
                },
        )
        dialog.setContentView(content)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.35f)
        }
        dialog.show()
        // 弹窗宽度直接取设计令牌中的设备宽度，避免与渲染层使用两套尺寸来源。
        val width = (tokens.device.widthPx * 0.72f).toInt().coerceAtMost(tokens.dp(320f))
        dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    // ---- 构件 ----

    /** 小型切换按钮，selected 为真时黑底白字。 */
    internal fun chip(
        context: Context,
        label: CharSequence,
        selected: Boolean,
        onClick: () -> Unit,
    ): TextView {
        val tokens = DesignRuntime.tokens
        return InkWidgets
            .textView(
                context,
                label,
                tokens.textCaption,
                if (selected) InkColors.PAPER else InkColors.INK,
            ).apply {
                gravity = Gravity.CENTER
                background =
                    InkWidgets.background(
                        tokens.radiusSmall,
                        if (selected) InkColors.INK else InkColors.PAPER,
                        tokens.stroke,
                        InkColors.INK,
                    )
                setPadding(tokens.gap / 2, tokens.gap / 4, tokens.gap / 2, tokens.gap / 4)
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
    }

    /** 紧凑按钮：白底描边、宽度自适应，用于列表与主题行内操作。 */
    internal fun compactButton(
        context: Context,
        label: CharSequence,
        onClick: () -> Unit,
    ): TextView {
        val tokens = DesignRuntime.tokens
        return InkWidgets.textView(context, label, tokens.textCaption, InkColors.INK).apply {
            gravity = Gravity.CENTER
            minWidth = 0
            minimumWidth = 0
            setPadding(tokens.gap / 2, 0, tokens.gap / 2, 0)
            background =
                InkWidgets.background(
                    tokens.radiusSmall,
                    InkColors.PAPER,
                    tokens.stroke,
                    InkColors.INK,
                )
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    /** 等权按钮布局参数。 */
    internal fun weightButtonParams(weight: Float): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, DesignRuntime.tokens.dp(44f), weight)

    /** 三等分按钮布局参数。 */
    internal fun thirdButtonParams(weight: Float): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, DesignRuntime.tokens.buttonHeight, weight)

    /** 行内间隙占位视图。 */
    internal fun gapView(context: Context): View = View(context)
}

/** 底部操作按钮的线稿图标种类。 */
private enum class BottomIcon { SEARCH, IMPORT, TRASH, SAVE }

/**
 * 底部按钮的 16dp 线稿图标，全部由 Canvas 绘制。
 *
 * @param icon 图标种类，决定轮廓画法。
 */
private class BottomGlyphView(
    context: Context,
    private val icon: BottomIcon,
) : View(context) {
    /** 线条画笔。 */
    private val stroke = DesignRuntime.strokePaint()

    /** 填充画笔，用于软盘滑盖等实心细节。 */
    private val fill = DesignRuntime.fillPaint(InkColors.INK)

    /**
     * 按图标种类绘制轮廓，外接尺寸取视图宽高的较小值。
     *
     * @param canvas 目标画布，尺寸取视图当前宽高。
     */
    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat() * 0.86f
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        val right = left + size
        val bottom = top + size
        when (icon) {
            BottomIcon.SEARCH -> {
                val r = size * 0.30f
                val cx = left + size * 0.42f
                val cy = top + size * 0.42f
                canvas.drawCircle(cx, cy, r, stroke)
                canvas.drawLine(
                    cx + r * 0.7f,
                    cy + r * 0.7f,
                    left + size * 0.95f,
                    top + size * 0.95f,
                    stroke,
                )
            }

            BottomIcon.IMPORT -> {
                // 文件夹外轮廓加顶部标签，内部向下箭头表示导入。
                val bodyTop = top + size * 0.26f
                canvas.drawRect(left, bodyTop, right, bottom, stroke)
                canvas.drawLine(left, bodyTop, left + size * 0.30f, bodyTop, stroke)
                canvas.drawLine(
                    left + size * 0.30f,
                    bodyTop,
                    left + size * 0.38f,
                    top + size * 0.10f,
                    stroke,
                )
                canvas.drawLine(left + size * 0.38f, top + size * 0.10f, right, top + size * 0.10f, stroke)
                val cx = left + size * 0.5f
                canvas.drawLine(cx, top + size * 0.32f, cx, top + size * 0.62f, stroke)
                val path = Path()
                path.moveTo(cx - size * 0.12f, top + size * 0.50f)
                path.lineTo(cx + size * 0.12f, top + size * 0.50f)
                path.lineTo(cx, top + size * 0.66f)
                path.close()
                canvas.drawPath(path, fill)
            }

            BottomIcon.TRASH -> {
                // 桶盖、桶身与两条竖向纹理。
                canvas.drawLine(left, top + size * 0.22f, right, top + size * 0.22f, stroke)
                canvas.drawRect(
                    left + size * 0.16f,
                    top + size * 0.10f,
                    left + size * 0.50f,
                    top + size * 0.22f,
                    stroke,
                )
                canvas.drawLine(
                    left + size * 0.22f,
                    top + size * 0.22f,
                    left + size * 0.30f,
                    bottom,
                    stroke,
                )
                canvas.drawLine(
                    right - size * 0.22f,
                    top + size * 0.22f,
                    right - size * 0.30f,
                    bottom,
                    stroke,
                )
                canvas.drawLine(
                    left + size * 0.30f,
                    bottom,
                    right - size * 0.30f,
                    bottom,
                    stroke,
                )
                canvas.drawLine(
                    left + size * 0.42f,
                    top + size * 0.34f,
                    left + size * 0.44f,
                    bottom - size * 0.10f,
                    stroke,
                )
                canvas.drawLine(
                    right - size * 0.42f,
                    top + size * 0.34f,
                    right - size * 0.44f,
                    bottom - size * 0.10f,
                    stroke,
                )
            }

            BottomIcon.SAVE -> {
                // 软盘外框、滑盖与标签区，比例与旧磁盘按钮一致。
                val radius = size * 0.12f
                canvas.drawRoundRect(left, top, right, bottom, radius, radius, stroke)
                canvas.drawRect(
                    left + size * 0.20f,
                    top + size * 0.12f,
                    left + size * 0.60f,
                    top + size * 0.40f,
                    stroke,
                )
                canvas.drawRect(
                    left + size * 0.20f,
                    top + size * 0.58f,
                    left + size * 0.80f,
                    top + size * 0.88f,
                    stroke,
                )
                canvas.drawRect(
                    left + size * 0.30f,
                    top + size * 0.18f,
                    left + size * 0.48f,
                    top + size * 0.40f,
                    fill,
                )
            }
        }
    }
}
