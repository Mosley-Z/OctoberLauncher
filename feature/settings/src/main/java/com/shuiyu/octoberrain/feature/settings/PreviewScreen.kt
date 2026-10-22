package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.IconTargets
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.common.SortMode
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkGlyphs
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.core.device.DeviceRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * 美化模式预览页。
 *
 * 主体为真实桌面的等比例缩略图：由 feature/home 的 DesktopPreviewProvider 按设备像素
 * 创建一页桌面视图，本页把它缩放到可用区域并居中，组件、图标、边距与行距因此与桌面一致。
 * 提供方不可用时回退为三列图标网格并提示。底部提供「返回调整」与「应用排序」，应用排序时
 * 按预览数据逐页拼装写回桌面顺序，随后关闭预览与美化模式，回到桌面。
 */
class PreviewScreen : Screen {
    /** 页面级协程作用域，随页面销毁取消。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 页面根容器，未创建或已销毁时为 null。 */
    private var root: LinearLayout? = null

    /** 上一次构建时的配置快照，用于回到前台时判断是否需要重建。 */
    private var lastConfig: LauncherConfig? = null

    /** 上一次构建时读取的美化模式状态版本。 */
    private var lastStateVersion = 0L

    /** 当前展示的页下标。 */
    private var pageIndex = 0

    /** 当前页顺序，元素为 LauncherEntry.key。 */
    private var workingKeys: List<String> = emptyList()

    /** 各页条目键，应用排序与预览顺序拼装时使用。 */
    private var pages: List<List<String>> = emptyList()

    /** 条目键到显示名称的映射（回退网格使用）。 */
    private var labels: Map<String, String> = emptyMap()

    /** 数据是否来自美化模式（决定应用排序后关闭几层页面）。 */
    private var fromBeautify = false

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
        loadState()
        renderNow()
        lastConfig = AppServices.config.state.value
        lastStateVersion = BeautifyPreviewState.version
        return view
    }

    override fun onResume() {
        val config = AppServices.config.state.value
        val version = BeautifyPreviewState.version
        if (root != null && (config != lastConfig || version != lastStateVersion)) {
            loadState()
            renderNow()
            lastConfig = config
            lastStateVersion = version
        }
    }

    override fun onDestroy() {
        scope.cancel()
        root = null
    }

    // ---- 状态读取 ----

    /**
     * 读取美化模式写入的状态；缺失时回退到桌面分页快照的第 1 页。
     *
     * 回退分支的顺序与页结构由 DesktopPagination 统一提供，避免预览页
     * 与美化模式各自拼装分页；标签直接取自快照条目。
     */
    private fun loadState() {
        if (BeautifyPreviewState.isFilled) {
            pageIndex = BeautifyPreviewState.pageIndex
            workingKeys = BeautifyPreviewState.workingKeys
            pages = BeautifyPreviewState.pages
            labels = BeautifyPreviewState.labels
            fromBeautify = true
            if (workingKeys.isEmpty()) {
                workingKeys = pages.getOrNull(pageIndex).orEmpty()
            }
            return
        }
        val snapshot = DesktopPagination.snapshot(AppServices.config.state.value)
        pages = DesktopPagination.keysOf(snapshot)
        pageIndex = 0
        workingKeys = pages.getOrNull(0).orEmpty()
        labels = snapshot.pages.flatten().associate { it.key to it.label }
        fromBeautify = false
    }

    /**
     * 拼装预览使用的完整条目顺序。
     *
     * 当前页使用美化模式内存中的顺序，其余页沿用各自页内顺序，逐页合并后交给预览提供方；
     * 页结构缺失时退化为当前页顺序。合并规则与美化模式共用 DesktopPagination.mergeOrder。
     */
    private fun fullOrder(): List<String> = DesktopPagination.mergeOrder(pages, pageIndex, workingKeys)

    // ---- 页面骨架 ----

    /** 立即按当前状态重建整页。 */
    private fun renderNow() {
        val view = root ?: return
        val context = view.context
        view.removeAllViews()
        view.addView(buildToolbar(context))
        view.addView(InkWidgets.divider(context))
        view.addView(buildSubtitle(context))
        view.addView(
            buildBody(context),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        view.addView(buildBottom(context))
    }

    /** 顶部工具栏：返回与标题。 */
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
            InkWidgets.textView(context, "预览", tokens.textHeading, InkColors.INK).apply {
                isSingleLine = true
                setPadding(tokens.gap, 0, 0, 0)
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f,
                    )
            },
        )
        return bar
    }

    /** 副标题：页号与排序提示。 */
    private fun buildSubtitle(context: Context): View {
        val tokens = DesignRuntime.tokens
        return InkWidgets
            .textView(
                context,
                "第 ${pageIndex + 1} 页 · 图标将按此顺序应用",
                tokens.textCaption,
                InkColors.INK_3,
            ).apply {
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                setPadding(tokens.pageGutter, tokens.gap / 2, tokens.pageGutter, tokens.gap / 2)
            }
    }

    /**
     * 预览主体：真实桌面等比例缩略图，提供方不可用时回退三列网格。
     *
     * 使用 FrameLayout 承载并在四边留出 tokens.gap，容器纸白；缩略图本身带细描边，
     * 模拟设备边框。
     */
    private fun buildBody(context: Context): View {
        val tokens = DesignRuntime.tokens
        val body =
            FrameLayout(context).apply {
                setBackgroundColor(InkColors.PAPER)
                setPadding(tokens.gap, 0, tokens.gap, 0)
            }
        val preview =
            if (AppServices.hasPreviewProvider()) {
                runCatching {
                    AppServices.previewProvider.createPreviewView(
                        context,
                        pageIndex = pageIndex,
                        order = fullOrder(),
                    )
                }.getOrNull()
            } else {
                null
            }
        if (preview != null) {
            body.addView(
                ScaledPreviewHost(context, preview),
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        } else {
            body.addView(buildFallback(context))
        }
        return body
    }

    /** 回退视图：不可用提示 + 三列图标网格。 */
    private fun buildFallback(context: Context): View {
        val tokens = DesignRuntime.tokens
        val column =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    )
            }
        column.addView(
            InkWidgets
                .textView(
                    context,
                    "桌面预览不可用，以下为图标顺序",
                    tokens.textCaption,
                    InkColors.INK_3,
                ).apply {
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, tokens.gap / 2, 0, tokens.gap / 2)
                },
        )
        column.addView(
            buildGrid(context),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        return column
    }

    /**
     * 缩略图宿主：把按设备像素排布的桌面视图缩放到可用区域并居中。
     *
     * 缩放比例按可用宽高与视图实际测量尺寸计算：scale = min(可用宽/预览宽, 可用高/预览高)；
     * 以左上角为缩放基点后按剩余空间平移，保证图案居中。绘制完成后在图案外缘画细描边。
     */
    private class ScaledPreviewHost(
        context: Context,
        private val preview: View,
    ) : FrameLayout(context) {
        /** 缩略图在宿主坐标系中的边界，用于绘制设备边框。 */
        private val borderRect = RectF()

        init {
            setBackgroundColor(InkColors.PAPER)
            clipChildren = true
            // 预览视图只用于展示，不响应点击与焦点。
            preview.isClickable = false
            preview.isFocusable = false
            addView(
                preview,
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT,
                ),
            )
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val previewWidth =
                preview.width.takeIf { it > 0 }
                    ?: preview.measuredWidth.takeIf { it > 0 }
                    ?: measureSpecSize(widthMeasureSpec)
            val previewHeight =
                preview.height.takeIf { it > 0 }
                    ?: preview.measuredHeight.takeIf { it > 0 }
                    ?: measureSpecSize(heightMeasureSpec)
            preview.measure(
                MeasureSpec.makeMeasureSpec(previewWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(previewHeight, MeasureSpec.EXACTLY),
            )
            setMeasuredDimension(
                measureSpecSize(widthMeasureSpec),
                measureSpecSize(heightMeasureSpec),
            )
        }

        override fun onLayout(
            changed: Boolean,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
        ) {
            val availableWidth = width
            val availableHeight = height
            if (availableWidth <= 0 || availableHeight <= 0) return
            val previewWidth = preview.measuredWidth.coerceAtLeast(1)
            val previewHeight = preview.measuredHeight.coerceAtLeast(1)
            preview.layout(0, 0, previewWidth, previewHeight)
            // 等比例缩放：取宽高两个方向比例的较小值，保证整页完整可见。
            val scale =
                min(
                    availableWidth.toFloat() / previewWidth,
                    availableHeight.toFloat() / previewHeight,
                )
            preview.pivotX = 0f
            preview.pivotY = 0f
            preview.scaleX = scale
            preview.scaleY = scale
            // 缩放后按剩余空间平移，使图案在容器内水平与垂直居中。
            preview.translationX = (availableWidth - previewWidth * scale) / 2f
            preview.translationY = (availableHeight - previewHeight * scale) / 2f
            borderRect.set(
                preview.translationX,
                preview.translationY,
                preview.translationX + previewWidth * scale,
                preview.translationY + previewHeight * scale,
            )
        }

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            if (borderRect.width() <= 0f || borderRect.height() <= 0f) return
            val tokens = DesignRuntime.tokens
            canvas.drawRect(
                borderRect,
                DesignRuntime.strokePaint(InkColors.INK, tokens.stroke),
            )
        }

        /** 读取测量规格中的可用尺寸。 */
        private fun measureSpecSize(spec: Int): Int = MeasureSpec.getSize(spec).coerceAtLeast(1)
    }

    /** 回退网格：每行 3 个等宽单元，整体纵向滚动。 */
    private fun buildGrid(context: Context): View {
        val tokens = DesignRuntime.tokens
        val scroll =
            ScrollView(context).apply {
                isFillViewport = false
            }
        val content =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, tokens.gap, 0, tokens.pageBottom)
            }
        val cellWidth = cellWidthPx()
        val appPackages =
            AppServices.catalog.apps.value
                .mapTo(HashSet()) { it.packageName }
        workingKeys.chunked(GRID_COLUMNS).forEach { rowKeys ->
            val row =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    isBaselineAligned = false
                    gravity = Gravity.TOP
                }
            for (column in 0 until GRID_COLUMNS) {
                if (column < rowKeys.size) {
                    row.addView(buildCell(context, rowKeys[column], cellWidth, appPackages))
                } else {
                    row.addView(View(context), cellParams(weight = 1f))
                }
                if (column != GRID_COLUMNS - 1) row.addView(gapView(context), gapParams())
            }
            content.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            content.addView(
                gapView(context),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    tokens.gap,
                ),
            )
        }
        if (workingKeys.isEmpty()) {
            content.addView(
                InkWidgets.textView(context, "当前页没有可预览的应用", tokens.textBody, InkColors.INK_3),
            )
        }
        scroll.addView(content)
        return scroll
    }

    /** 单个应用单元：图标在上、名称在下。 */
    private fun buildCell(
        context: Context,
        key: String,
        cellWidth: Int,
        appPackages: Set<String>,
    ): View {
        val tokens = DesignRuntime.tokens
        val iconSize =
            (cellWidth * 0.52f)
                .toInt()
                .coerceIn(tokens.dp(32f), tokens.dp(56f))
        val column =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
        val entryKey = IconTargets.entryKeyOf(key)
        val knownEntry =
            IconTargets.specialEntryOf(key) != null ||
                (entryKey.startsWith("app:") && entryKey.removePrefix("app:") in appPackages)
        if (knownEntry) {
            val icon =
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
            column.addView(
                icon,
                LinearLayout.LayoutParams(iconSize, iconSize),
            )
            loadIcon(key, icon, iconSize)
        } else {
            // 库外键保持占位图形，避免为未知条目请求不存在的图标。
            column.addView(
                InkGlyphs.view(context, InkGlyphs.Kind.FOLDER, iconSize),
                LinearLayout.LayoutParams(iconSize, iconSize),
            )
        }
        column.addView(
            InkWidgets
                .textView(
                    context,
                    labels[key] ?: key,
                    tokens.textSmall,
                    InkColors.INK,
                ).apply {
                    gravity = Gravity.CENTER
                    isSingleLine = true
                    ellipsize = TextUtils.TruncateAt.END
                    layoutParams =
                        LinearLayout
                            .LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                            ).apply { topMargin = tokens.gap / 3 }
                },
        )
        column.layoutParams = cellParams(weight = 1f)
        return column
    }

    /** 底部操作：返回调整与应用排序，两个按钮等宽。 */
    private fun buildBottom(context: Context): View {
        val tokens = DesignRuntime.tokens
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isBaselineAligned = false
                setPadding(tokens.gap, tokens.gap / 2, tokens.gap, tokens.gap / 2)
            }
        row.addView(button(context, "返回调整") { runCatching { AppServices.router.back() } }, buttonParams())
        row.addView(gapView(context), gapParams())
        row.addView(button(context, "应用排序") { applyOrder() }, buttonParams())
        return row
    }

    // ---- 交互 ----

    /** 按当前顺序写回桌面配置，随后关闭预览并回到桌面。 */
    private fun applyOrder() {
        val context = root?.context ?: return
        val merged = fullOrder()
        if (merged.isEmpty()) {
            SettingsUi.toast(context, "没有可应用的顺序")
            return
        }
        SettingsUi.edit { current ->
            current.copy(
                desktop =
                    current.desktop.copy(
                        order = merged,
                        sortMode = SortMode.MANUAL,
                    ),
            )
        }
        BeautifyPreviewState.clear()
        SettingsUi.toast(context, "已应用排序")
        SettingsUi.fullRefresh()
        val backs = if (fromBeautify) 2 else 1
        repeat(backs) {
            runCatching { AppServices.router.back() }
        }
    }

    /** 异步加载条目图标：优先覆盖图，其次应用图标或内置字形。 */
    private fun loadIcon(
        key: String,
        target: ImageView,
        sizePx: Int,
    ) {
        // 键可能是 app:<包名> 或 special:<名称>，统一归一为目标标识后再按类型取图标。
        val targetId = IconTargets.normalizeStoredKey(key)
        scope.launch {
            val bitmap =
                withContext(Dispatchers.IO) {
                    AppServices.iconOverrides.loadOverride(targetId, sizePx)
                        ?: runCatching {
                            val special = IconTargets.specialEntryOf(targetId)
                            if (special != null) {
                                AppServices.catalog.loadSpecialIcon(special, sizePx)
                            } else {
                                AppServices.catalog.loadIcon(targetId, sizePx)
                            }
                        }.getOrNull()
                }
            if (bitmap != null && target.isAttachedToWindow) {
                target.setImageBitmap(bitmap)
            }
        }
    }

    // ---- 构件 ----

    /** 单元格宽度：按屏宽减去页面内边距与两个间隙后三等分。 */
    private fun cellWidthPx(): Int {
        val tokens = DesignRuntime.tokens
        val width = if (DeviceRuntime.isReady()) DeviceRuntime.profile.widthPx else 1080
        return ((width - tokens.pageGutter * 2 - tokens.gap * 2) / GRID_COLUMNS)
            .coerceAtLeast(tokens.dp(48f))
    }

    /** 等宽单元布局参数。 */
    private fun cellParams(weight: Float): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)

    /** 列间间隙布局参数。 */
    private fun gapParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(DesignRuntime.tokens.gap / 2, 1)

    /** 底部按钮：44dp 高、白底描边、文字居中。 */
    private fun button(
        context: Context,
        label: CharSequence,
        onClick: () -> Unit,
    ): TextView {
        val tokens = DesignRuntime.tokens
        return InkWidgets.textView(context, label, tokens.textBody, InkColors.INK).apply {
            gravity = Gravity.CENTER
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
    private fun buttonParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, DesignRuntime.tokens.dp(44f), 1f)

    /** 行内间隙占位视图。 */
    private fun gapView(context: Context): View = View(context)

    private companion object {
        /** 回退网格列数。 */
        const val GRID_COLUMNS = 3
    }
}
