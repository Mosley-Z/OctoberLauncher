package com.shuiyu.octoberrain.feature.wereadapp

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import com.inkread.weekread.kernel.insight.InsightPage
import com.inkread.weekread.kernel.insight.InsightRenderer
import kotlin.math.abs

/**
 * 洞察页的整页绘制视图。
 *
 * 内容由内核 `InsightPage` 自绘：视图宽度确定后取得整页高度，绘制时按滚动偏移切取窗口。
 * 内核页面的 `render` 只绘制首个视口内的分区，滚动由渲染器的 `draw` 末位参数承载，
 * 因此这里通过页面持有的渲染器直接绘制；读取失败时退回页面自身的绘制方式。
 *
 * 整页滚动无惯性；手指落在读书排行区域时优先滚动排行内部列表。
 * 位移小于 12 像素时吞掉事件，避免轻微抖动被误判为滚动。
 *
 * @param context 创建视图的上下文
 */
@Suppress("TooManyFunctions")
internal class WereadInsightView(
    context: Context,
) : View(context) {
    /** 内核宿主适配，只需目录与字号。 */
    private val host = WereadAppHost(context) { invalidate() }

    /** 洞察页渲染器，刷新时整体重建。 */
    private var page: InsightPage = InsightPage(host, WereadAppPrefs.dataDir(context))

    /** 从页面反射取得的渲染器，用于按偏移绘制整页。 */
    private var renderer: InsightRenderer? = null

    /** 读书排行分区的序号，用于把落在该区域的拖动转给排行内部滚动。 */
    private var rankIndex: Int = NO_INDEX

    /** 是否已完成首次绑定。 */
    private var bound: Boolean = false

    /** 整页内容高度，布局完成后计算。 */
    private var contentHeight: Float = 0f

    /** 当前滚动偏移。 */
    private var offset: Float = 0f

    /** 手指按下与上一次移动的纵坐标。 */
    private var downY: Float = 0f
    private var lastY: Float = 0f

    /** 是否已进入拖动状态。 */
    private var dragging: Boolean = false

    /** 本次拖动是否用于滚动排行内部列表。 */
    private var rankScrolling: Boolean = false

    init {
        setBackgroundColor(0xFFFFFFFF.toInt())
    }

    /** 重新读取数据并重建洞察页。 */
    fun refresh() {
        page.release()
        page = InsightPage(host, WereadAppPrefs.dataDir(context))
        renderer = null
        rankIndex = NO_INDEX
        bound = false
        contentHeight = 0f
        offset = 0f
        invalidate()
    }

    /** 释放内核渲染资源。 */
    fun release() {
        page.release()
    }

    /** 绘制当前滚动窗口。 */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val target = ensureRenderer() ?: return drawFallback(canvas)
        if (!bound) {
            page.bind()
            contentHeight = target.contentHeight(width.toFloat(), height.toFloat(), page.unit())
            rankIndex = findRankIndex(target)
            bound = true
        }
        offset = offset.coerceIn(0f, (contentHeight - height).coerceAtLeast(0f))
        target.draw(canvas, width.toFloat(), height.toFloat(), page.unit(), offset)
    }

    /** 拿不到渲染器时退回页面绘制，只显示首个视口。 */
    private fun drawFallback(canvas: Canvas) {
        if (!bound) {
            page.bind()
            contentHeight = page.height(width)
            bound = true
        }
        offset = offset.coerceIn(0f, (contentHeight - height).coerceAtLeast(0f))
        page.render(canvas, width, height, offset.toInt())
    }

    /** 反射读取页面持有的渲染器，失败时缓存空结果。 */
    private fun ensureRenderer(): InsightRenderer? {
        renderer?.let { return it }
        val found =
            runCatching {
                val field = InsightPage::class.java.getDeclaredField("renderer")
                field.isAccessible = true
                field.get(page) as? InsightRenderer
            }.getOrNull()
        renderer = found
        return found
    }

    /** 查找读书排行分区序号，找不到返回 [NO_INDEX]。 */
    private fun findRankIndex(target: InsightRenderer): Int =
        (0 until target.count()).firstOrNull { target.titleOf(it).contains(RANK_TITLE) } ?: NO_INDEX

    /** 判断页面坐标是否落在读书排行分区内。 */
    private fun insideRank(pageY: Float): Boolean {
        val target = renderer
        if (target == null || rankIndex < 0 || !page.rankScrollable()) return false
        val top = target.sectionTop(rankIndex, width.toFloat(), height.toFloat(), page.unit())
        val bottom =
            if (rankIndex + 1 < target.count()) {
                target.sectionTop(rankIndex + 1, width.toFloat(), height.toFloat(), page.unit())
            } else {
                contentHeight
            }
        return pageY >= top && pageY < bottom
    }

    /** 处理纵向拖动：排行区域内滚排行，其余区域滚动整页。 */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event)
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handleCancel()
            else -> return super.onTouchEvent(event)
        }
        return true
    }

    /** 记录按下位置，并判断本次拖动是否作用于排行内部。 */
    private fun handleDown(event: MotionEvent) {
        downY = event.y
        lastY = event.y
        dragging = false
        rankScrolling = insideRank(offset + event.y)
    }

    /** 按位移滚动排行内部或整页内容。 */
    private fun handleMove(event: MotionEvent) {
        val dy = event.y - lastY
        lastY = event.y
        if (!dragging && abs(event.y - downY) > DRAG_SLOP_PX) dragging = true
        if (!dragging) return
        if (rankScrolling) {
            if (page.scrollRankBy(-dy)) invalidate()
        } else {
            appendScroll(-dy)
        }
    }

    /** 结束拖动状态。 */
    private fun handleCancel() {
        dragging = false
        rankScrolling = false
    }

    /** 满足可访问性要求，洞察页没有点按动作。 */
    override fun performClick(): Boolean = super.performClick()

    /** 追加滚动偏移并限制在内容范围内。 */
    private fun appendScroll(delta: Float) {
        val max = (contentHeight - height).coerceAtLeast(0f)
        val next = (offset + delta).coerceIn(0f, max)
        if (next == offset) return
        offset = next
        invalidate()
    }

    private companion object {
        /** 判定拖动的最小位移，与卡片滚动一致。 */
        const val DRAG_SLOP_PX: Float = 12f

        /** 排行分区标题关键字。 */
        const val RANK_TITLE: String = "排行"

        /** 未找到分区时的序号。 */
        const val NO_INDEX: Int = -1
    }
}
