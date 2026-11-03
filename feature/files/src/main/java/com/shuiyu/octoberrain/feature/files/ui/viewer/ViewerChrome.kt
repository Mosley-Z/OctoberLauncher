package com.shuiyu.octoberrain.feature.files.ui.viewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.shuiyu.octoberrain.core.design.InkColors

/** 查看器可触发动作，命中判定返回该枚举。 */
enum class ViewerAction {
    /** 返回上一页。 */
    BACK,

    /** 打开详情面板。 */
    INFO,

    /** 通过系统分享面板发送当前图片。 */
    SHARE,

    /** 进入编辑页，由编辑页承担裁剪、旋转与滤镜。 */
    EDIT,

    /** 删除当前图片，二次确认后执行。 */
    DELETE,

    /** 设置为系统壁纸，仅 S4 通道支持。 */
    SET_WALLPAPER,
}

/**
 * 查看器的命中判定。
 *
 * 纯逻辑对象，按 [ViewerMetrics] 给出的矩形区域判断动作，不接触 Canvas。
 * 判定顺序为返回、信息、底栏四个动作；顶栏标题区与内容区不返回动作。
 */
object ViewerHitTest {
    /** 底栏动作顺序，与 [ViewerMetrics.bottomActions] 的划分一一对应。 */
    val bottomActions: List<ViewerAction> =
        listOf(
            ViewerAction.SHARE,
            ViewerAction.EDIT,
            ViewerAction.DELETE,
            ViewerAction.SET_WALLPAPER,
        )

    /**
     * 判断坐标命中的动作。
     *
     * @param x 横坐标。
     * @param y 纵坐标。
     * @param metrics 当前几何布局。
     * @return 命中的动作；未命中返回 null。
     */
    fun hit(
        x: Int,
        y: Int,
        metrics: ViewerMetrics,
    ): ViewerAction? {
        val areas = metrics.bottomActions(bottomActions.size)
        return when {
            metrics.backArea.contains(x, y) -> ViewerAction.BACK
            metrics.infoArea.contains(x, y) -> ViewerAction.INFO
            else -> {
                val index = areas.indexOfFirst { it.contains(x, y) }
                if (index >= 0) bottomActions[index] else null
            }
        }
    }

    /**
     * 动作的显示文案。
     *
     * @param action 动作。
     * @return 底栏或顶栏使用的短文案。
     */
    fun labelOf(action: ViewerAction): String =
        when (action) {
            ViewerAction.BACK -> "返回"
            ViewerAction.INFO -> "信息"
            ViewerAction.SHARE -> "分享"
            ViewerAction.EDIT -> "编辑"
            ViewerAction.DELETE -> "删除"
            ViewerAction.SET_WALLPAPER -> "设置为"
        }
}

/**
 * 查看器顶栏与底栏绘制。
 *
 * 顶栏包含返回、文件名、信息三部分；底栏包含分享、编辑、删除、设置为四个等分按钮，
 * 每个按钮以文本符号加文案的同一行形式居中绘制。
 * 绘制只读取 [ViewerMetrics]，不重复计算几何；命中判定由 [ViewerHitTest] 完成。
 *
 * 参数 context 用于取密度换算字号；参数 metrics 为当前布局。
 */
class ViewerChrome(
    context: Context,
    private val metrics: ViewerMetrics,
) {
    private val density: Float = context.resources.displayMetrics.density
    private val scaledDensity: Float = context.resources.displayMetrics.scaledDensity
    private val titlePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkColors.INK
            textSize = 17f * scaledDensity
            isFakeBoldText = true
        }
    private val bodyPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkColors.INK
            textSize = 14f * scaledDensity
        }

    /** 底栏符号画笔，字号比正文略大，与文案共用基线。 */
    private val glyphPaint =
        Paint(bodyPaint).apply {
            textSize = bodyPaint.textSize * 1.05f
        }
    private val smallPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkColors.INK_3
            textSize = 12f * scaledDensity
        }
    private val paperPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkColors.PAPER
            style = Paint.Style.FILL
        }
    private val linePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkColors.INK_4
            strokeWidth = maxOf(1f, 1.2f * density)
            style = Paint.Style.STROKE
        }

    /**
     * 绘制顶栏与底栏。
     *
     * @param canvas 画布。
     * @param title 文件名，超宽时逐字截断并追加省略号。
     * @param pageLabel 页码文案。
     */
    fun draw(
        canvas: Canvas,
        title: String,
        pageLabel: String,
    ) {
        drawTopBar(canvas, title, pageLabel)
        drawBottomBar(canvas)
    }

    private fun drawTopBar(
        canvas: Canvas,
        title: String,
        pageLabel: String,
    ) {
        val bar = metrics.topBar
        canvas.drawRect(bar.left.toFloat(), bar.top.toFloat(), bar.right.toFloat(), bar.bottom.toFloat(), paperPaint)
        canvas.drawLine(
            bar.left.toFloat(),
            bar.bottom.toFloat(),
            bar.right.toFloat(),
            bar.bottom.toFloat(),
            linePaint,
        )
        drawCentered(canvas, "返回", metrics.backArea, bodyPaint)
        val titleArea = metrics.titleArea
        val fitted = fit(title, titleArea.width - metrics.backArea.width / 2)
        drawTextLeft(canvas, fitted, titleArea, titlePaint)
        drawTextRight(canvas, pageLabel, titleArea, smallPaint)
    }

    private fun drawBottomBar(canvas: Canvas) {
        val bar = metrics.bottomBar
        canvas.drawRect(bar.left.toFloat(), bar.top.toFloat(), bar.right.toFloat(), bar.bottom.toFloat(), paperPaint)
        canvas.drawLine(
            bar.left.toFloat(),
            bar.top.toFloat(),
            bar.right.toFloat(),
            bar.top.toFloat(),
            linePaint,
        )
        val areas = metrics.bottomActions(ViewerHitTest.bottomActions.size)
        areas.forEachIndexed { index, area ->
            val action = ViewerHitTest.bottomActions[index]
            drawGlyphLabel(canvas, glyphOf(action), ViewerHitTest.labelOf(action), area, glyphPaint, bodyPaint)
            if (index > 0) {
                canvas.drawLine(
                    area.left.toFloat(),
                    area.top.toFloat() + area.height / 4f,
                    area.left.toFloat(),
                    area.bottom.toFloat() - area.height / 4f,
                    linePaint,
                )
            }
        }
    }

    /**
     * 底栏动作的文本符号。
     *
     * 符号取「↗ ✎ ✕ ⊞」，与文案在同一行绘制。设备字体缺字时退化备选为
     * 「↑ / × ▣」，在设备上确认字形完整后只保留一组常量并删除另一组。
     *
     * @param action 动作。
     * @return 与文案同行的符号；顶栏动作不使用符号，返回空串。
     */
    private fun glyphOf(action: ViewerAction): String =
        when (action) {
            ViewerAction.SHARE -> "↗"
            ViewerAction.EDIT -> "✎"
            ViewerAction.DELETE -> "✕"
            ViewerAction.SET_WALLPAPER -> "⊞"
            ViewerAction.BACK,
            ViewerAction.INFO,
            -> ""
        }

    /**
     * 在矩形区域内绘制「符号 + 文案」同一行并整体居中。
     *
     * 先按两个画笔的字宽与 4dp 间距计算总宽，再以区域中线定位起点，
     * 符号与文案共用正文基线，保证底栏高度与等分宽度不变。
     *
     * @param canvas 画布。
     * @param glyph 文本符号。
     * @param text 动作文案。
     * @param area 该动作的等分区域。
     * @param glyphPaint 符号画笔。
     * @param bodyPaint 文案画笔，同时提供基线计算。
     */
    private fun drawGlyphLabel(
        canvas: Canvas,
        glyph: String,
        text: String,
        area: ViewerArea,
        glyphPaint: Paint,
        bodyPaint: Paint,
    ) {
        val gap = 4f * density
        val glyphWidth = glyphPaint.measureText(glyph)
        val textWidth = bodyPaint.measureText(text)
        val totalWidth = glyphWidth + gap + textWidth
        val baseline = area.top + area.height / 2f - (bodyPaint.descent() + bodyPaint.ascent()) / 2f
        val start = area.left + (area.width - totalWidth) / 2f
        canvas.drawText(glyph, start, baseline, glyphPaint)
        canvas.drawText(text, start + glyphWidth + gap, baseline, bodyPaint)
    }

    private fun drawCentered(
        canvas: Canvas,
        text: String,
        area: ViewerArea,
        paint: Paint,
    ) {
        val baseline = area.top + area.height / 2f - (paint.descent() + paint.ascent()) / 2f
        val width = paint.measureText(text)
        canvas.drawText(text, area.left + (area.width - width) / 2f, baseline, paint)
    }

    private fun drawTextLeft(
        canvas: Canvas,
        text: String,
        area: ViewerArea,
        paint: Paint,
    ) {
        val baseline = area.top + area.height / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, area.left.toFloat(), baseline, paint)
    }

    private fun drawTextRight(
        canvas: Canvas,
        text: String,
        area: ViewerArea,
        paint: Paint,
    ) {
        val width = paint.measureText(text)
        val baseline = area.top + area.height / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, area.right - width, baseline, paint)
    }

    /**
     * 按可用宽度逐字截断文本并追加省略号。
     *
     * @param text 原始文本。
     * @param maxWidth 可用宽度，像素。
     * @return 可显示文本。
     */
    private fun fit(
        text: String,
        maxWidth: Int,
    ): String = ViewerText.ellipsize(text) { titlePaint.measureText(it) <= maxWidth }
}
