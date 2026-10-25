package com.shuiyu.octoberrain.feature.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.WereadTodo
import com.shuiyu.octoberrain.core.common.editWidgetPage
import com.shuiyu.octoberrain.core.common.effectiveTodoWeekStartDay
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.widget.WidgetHitRegion
import com.shuiyu.octoberrain.core.widget.WidgetTouchAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/**
 * 待办周历组件：七列展示可见七天内有日期的待办，并共享微信读书待办的操作。
 *
 * 三行表头固定在顶部，下方七列内容整体上下滚动。表头第一行是年月与新增入口，
 * 第二行的两侧箭头平移一天，第三行的两侧箭头平移七天。勾选框直接切换完成状态，
 * 点击正文打开编辑对话框，新增与编辑都经 [com.shuiyu.octoberrain.core.common.WereadTodoHost] 读写。
 */
@Suppress("TooManyFunctions")
class TodoWeekWidget(
    context: Context,
) : InkWidgetView(context) {
    private val zone: ZoneId = ZoneId.systemDefault()
    private var todos: List<WereadTodo> = emptyList()
    private var days: List<LocalDate> = emptyList()
    private var groups: List<List<WereadTodo>> = emptyList()
    private var anchor: LocalDate? = null
    private var manualShifted = false
    private var scrollOffset = 0f
    private var maxScroll = 0f
    private var contentHeight = 0f
    private var bodyTop = 0f
    private var bodyBottom = 0f
    private var hits: List<Hit> = emptyList()
    private var dragging = false
    private var lastTouchY = 0f
    private var persistJob: Job? = null

    private val titlePaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val boldLabelPaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val donePaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val boxPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val solidPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /** 一次绘制内可点击的目标。 */
    private class Hit(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val kind: Kind,
        val todoId: Long = 0L,
    ) {
        /** 判定坐标是否落在目标内部。 */
        fun contains(
            x: Float,
            y: Float,
        ): Boolean = x >= left && x < right && y >= top && y < bottom

        /** 转成宿主可识别的命中区域，宽高不足一个像素时返回 null。 */
        fun region(): WidgetHitRegion? {
            val width = (right - left).toInt()
            val height = (bottom - top).toInt()
            if (width <= 0 || height <= 0) return null
            return WidgetHitRegion(left.toInt(), top.toInt(), width, height, kind.name)
        }

        /** 目标类型。 */
        enum class Kind {
            ADD,
            DAY_EARLIER,
            DAY_LATER,
            WEEK_EARLIER,
            WEEK_LATER,
            TOGGLE,
            EDIT,
        }
    }

    /** 单列内的一条待办在内容坐标中的位置与折行结果。 */
    private class Cell(
        val todo: WereadTodo,
        val top: Float,
        val height: Float,
        val lines: List<String>,
    )

    /** 绘制的固定度量。 */
    private class Metrics(
        val width: Float,
        val gridLeft: Float,
        val gridRight: Float,
        val columnWidth: Float,
        val arrowWidth: Float,
        val row1Top: Float,
        val row2Top: Float,
        val row3Top: Float,
        val row1Height: Float,
        val row2Height: Float,
        val row3Height: Float,
        val bodyTop: Float,
        val bodyBottom: Float,
        val titleSize: Float,
        val labelSize: Float,
        val bodySize: Float,
        val gap: Float,
    )

    override suspend fun loadData(force: Boolean): Boolean {
        val host = AppServices.wereadTodos ?: return false
        val latest = host.list()
        withContext(Dispatchers.Main.immediate) {
            todos = latest
            val today = LocalDate.now()
            val startDay = config.desktop.effectiveTodoWeekStartDay
            val current = anchor
            val start =
                when {
                    current == null -> TodoWeekCalendar.startFor(today, startDay)
                    !manualShifted && today !in TodoWeekCalendar.daysOf(current) ->
                        TodoWeekCalendar.startFor(today, startDay)
                    else -> current
                }
            anchor = start
            applyWindow(start)
            clampScroll()
        }
        return true
    }

    override fun onHostVisible(visible: Boolean) {
        val becameVisible = visible && !hostVisible
        super.onHostVisible(visible)
        if (!becameVisible || !manualShifted) return
        manualShifted = false
        anchor = null
        reload(force = true)
    }

    override fun drawWidget(
        canvas: Canvas,
        width: Int,
        height: Int,
    ) {
        if (width <= 0 || height <= 0) return
        hits = emptyList()
        val metrics = measure(width, height)
        if (metrics.gridRight <= metrics.gridLeft) return
        preparePaints(metrics)
        drawHeader(canvas, metrics)
        drawBody(canvas, metrics)
    }

    override fun hitRegions(): List<WidgetHitRegion> = hits.mapNotNull { it.region() }

    override fun onClick(
        x: Float,
        y: Float,
    ): Boolean {
        val hit = hits.firstOrNull { it.contains(x, y) } ?: return false
        return when (hit.kind) {
            Hit.Kind.ADD -> {
                openEditor(null)
                true
            }
            Hit.Kind.DAY_EARLIER -> shiftDays(-1)
            Hit.Kind.DAY_LATER -> shiftDays(1)
            Hit.Kind.WEEK_EARLIER -> shiftWeeks(-1)
            Hit.Kind.WEEK_LATER -> shiftWeeks(1)
            Hit.Kind.TOGGLE -> toggle(hit.todoId)
            Hit.Kind.EDIT -> {
                openEditor(todos.firstOrNull { it.id == hit.todoId })
                true
            }
        }
    }

    override fun onTouch(
        action: WidgetTouchAction,
        x: Float,
        y: Float,
    ): Boolean {
        if (action == WidgetTouchAction.DOWN) {
            dragging = maxScroll > 0f && y >= bodyTop && y <= bodyBottom
            lastTouchY = y
            return dragging
        }
        return dragging && handleDrag(action, y)
    }

    /** 处理拖动过程中的位移与松手，未拖动时由调用方直接返回。 */
    private fun handleDrag(
        action: WidgetTouchAction,
        y: Float,
    ): Boolean {
        when (action) {
            WidgetTouchAction.MOVE -> {
                scrollOffset = (scrollOffset - (y - lastTouchY)).coerceIn(0f, maxScroll)
                lastTouchY = y
                requestDraw()
            }
            WidgetTouchAction.UP, WidgetTouchAction.CANCEL -> dragging = false
            WidgetTouchAction.DOWN -> Unit
        }
        return true
    }

    /** 平移到相邻的一天，并把新的起始星期写回配置。 */
    private fun shiftDays(delta: Long): Boolean {
        val base = anchor ?: return false
        val shifted = base.plusDays(delta)
        anchor = shifted
        manualShifted = true
        scrollOffset = 0f
        applyWindow(shifted)
        requestDraw()
        persistStartDay(TodoWeekCalendar.startDayOf(shifted))
        return true
    }

    /** 平移到相邻的一周，起始星期保持不变。 */
    private fun shiftWeeks(delta: Long): Boolean {
        val base = anchor ?: return false
        val shifted = base.plusWeeks(delta)
        anchor = shifted
        manualShifted = true
        scrollOffset = 0f
        applyWindow(shifted)
        requestDraw()
        return true
    }

    /** 把起始星期写入桌面配置，临时平移出的日期不写入。 */
    private fun persistStartDay(startDay: Int) {
        if (!AppServices.isReady()) return
        val previous = persistJob
        persistJob =
            scope.launch {
                previous?.join()
                withContext(Dispatchers.IO) {
                    AppServices.config.edit { current ->
                        current.copy(
                            desktop =
                                current.desktop.editWidgetPage(
                                    pageIndex,
                                ) { it.copy(todoWeekStartDay = startDay) },
                        )
                    }
                }
            }
    }

    /** 切换一条待办的完成状态并重读清单。 */
    private fun toggle(id: Long): Boolean {
        val host = AppServices.wereadTodos ?: return false
        todos = host.toggle(id)
        anchor?.let(::applyWindow)
        clampScroll()
        requestDraw()
        AppServices.refresh.requestPartial()
        return true
    }

    /** 打开新增或编辑对话框，保存后重读清单。 */
    private fun openEditor(todo: WereadTodo?) {
        TodoWeekEditor(context, todo) { latest ->
            todos = latest
            anchor?.let(::applyWindow)
            clampScroll()
            requestDraw()
            AppServices.refresh.requestPartial()
        }.show()
    }

    /** 按起始日期重算七天与分组。 */
    private fun applyWindow(start: LocalDate) {
        days = TodoWeekCalendar.daysOf(start)
        groups = TodoWeekItems.week(todos, days, zone)
    }

    /** 按当前内容高度收敛滚动位置。 */
    private fun clampScroll() {
        maxScroll = max(0f, contentHeight - max(0f, bodyBottom - bodyTop))
        scrollOffset = scrollOffset.coerceIn(0f, maxScroll)
    }

    /** 计算绘制度量。 */
    private fun measure(
        width: Int,
        height: Int,
    ): Metrics {
        val gap = min(tokens.gap.toFloat(), height * 0.05f)
        val arrowWidth = min(tokens.dpF(32f), width * 0.1f)
        val gridLeft = padX * 0.35f + arrowWidth
        val gridRight = width - padX * 0.35f - arrowWidth
        val columnWidth = max(0f, (gridRight - gridLeft) / TodoWeekCalendar.DAY_COUNT)
        val titleSize = minOf(tokens.textHeading.toFloat(), height * 0.12f, width * 0.075f)
        val labelSize = minOf(tokens.textCaption.toFloat(), height * 0.1f, columnWidth * 0.55f)
        val bodySize = minOf(tokens.textCaption.toFloat(), height * 0.095f, columnWidth * 0.28f)
        val row1Height = titleSize * 1.6f
        val arrowHeight = min(tokens.dpF(36f), height * 0.18f)
        val row2Height = max(labelSize * 1.6f, arrowHeight)
        val row3Height = max(labelSize * 1.9f, arrowHeight)
        val row1Top = gap * 0.5f
        val row2Top = row1Top + row1Height
        val row3Top = row2Top + row2Height
        val top = row3Top + row3Height + gap * 0.3f
        val bottom = height - gap * 0.4f
        return Metrics(
            width = width.toFloat(),
            gridLeft = gridLeft,
            gridRight = gridRight,
            columnWidth = columnWidth,
            arrowWidth = arrowWidth,
            row1Top = row1Top,
            row2Top = row2Top,
            row3Top = row3Top,
            row1Height = row1Height,
            row2Height = row2Height,
            row3Height = row3Height,
            bodyTop = min(top, bottom),
            bodyBottom = bottom,
            titleSize = titleSize,
            labelSize = labelSize,
            bodySize = bodySize,
            gap = gap,
        )
    }

    /** 按度量设置画笔字号与颜色。 */
    private fun preparePaints(metrics: Metrics) {
        titlePaint.textSize = metrics.titleSize
        titlePaint.color = InkColors.INK
        titlePaint.typeface = boldTypeface()
        labelPaint.textSize = metrics.labelSize
        labelPaint.color = InkColors.INK_2
        labelPaint.typeface = DesignRuntime.typeface()
        boldLabelPaint.textSize = metrics.labelSize
        boldLabelPaint.color = InkColors.INK
        boldLabelPaint.typeface = boldTypeface()
        bodyPaint.textSize = metrics.bodySize
        bodyPaint.color = InkColors.INK
        bodyPaint.typeface = DesignRuntime.typeface()
        donePaint.set(bodyPaint)
        donePaint.color = InkColors.INK_3
        donePaint.flags = Paint.STRIKE_THRU_TEXT_FLAG
        boxPaint.strokeWidth = tokens.stroke.toFloat()
        boxPaint.color = InkColors.INK
        solidPaint.color = InkColors.INK
        linePaint.strokeWidth = tokens.stroke.toFloat()
        linePaint.color = InkColors.INK_3
    }

    /** 基于设计字体派生粗体。 */
    private fun boldTypeface(): Typeface = Typeface.create(DesignRuntime.typeface(), Typeface.BOLD)

    /** 绘制三行表头。 */
    private fun drawHeader(
        canvas: Canvas,
        metrics: Metrics,
    ) {
        val start = days.firstOrNull() ?: LocalDate.now()
        val titleTop = metrics.row1Top + (metrics.row1Height - InkDraw.lineHeight(titlePaint)) / 2f
        InkDraw.text(canvas, TodoWeekCalendar.titleOf(start), padX * 0.35f, titleTop, titlePaint)
        drawAddEntry(canvas, metrics, titleTop)
        drawDayRow(canvas, metrics)
        drawDateRow(canvas, metrics)
        val separatorY = metrics.bodyTop - metrics.gap * 0.15f
        canvas.drawLine(metrics.gridLeft, separatorY, metrics.gridRight, separatorY, linePaint)
    }

    /** 绘制第一行右侧的新增入口与四侧箭头。 */
    private fun drawAddEntry(
        canvas: Canvas,
        metrics: Metrics,
        top: Float,
    ) {
        val label = InkDraw.ellipsize("＋ 添加待办", labelPaint, metrics.width * 0.45f)
        val width = labelPaint.measureText(label)
        val right = metrics.width - padX * 0.35f
        canvas.drawText(label, right - width, top - labelPaint.ascent(), labelPaint)
        hits = hits + Hit(right - width, top, right, top + InkDraw.lineHeight(labelPaint), Hit.Kind.ADD)
        addArrow(canvas, metrics, metrics.row2Top, metrics.row2Height, false, Hit.Kind.DAY_LATER, "‹")
        addArrow(canvas, metrics, metrics.row2Top, metrics.row2Height, true, Hit.Kind.DAY_EARLIER, "›")
        addArrow(canvas, metrics, metrics.row3Top, metrics.row3Height, false, Hit.Kind.WEEK_EARLIER, "‹")
        addArrow(canvas, metrics, metrics.row3Top, metrics.row3Height, true, Hit.Kind.WEEK_LATER, "›")
    }

    /** 在指定行两侧绘制箭头并登记命中区域。 */
    private fun addArrow(
        canvas: Canvas,
        metrics: Metrics,
        rowTop: Float,
        rowHeight: Float,
        rightSide: Boolean,
        kind: Hit.Kind,
        label: String,
    ) {
        val centerX = if (rightSide) (metrics.gridRight + metrics.width) / 2f else metrics.gridLeft / 2f
        val arrowPaint =
            Paint(boldLabelPaint).apply {
                textSize = minOf(tokens.dpF(26f), rowHeight * 0.82f, metrics.arrowWidth * 0.85f)
            }
        val textWidth = arrowPaint.measureText(label)
        val baseline = rowTop + rowHeight / 2f - (arrowPaint.ascent() + arrowPaint.descent()) / 2f
        canvas.drawText(label, centerX - textWidth / 2f, baseline, arrowPaint)
        val left = if (rightSide) metrics.gridRight else 0f
        val right = if (rightSide) metrics.width else metrics.gridLeft
        hits = hits + Hit(left, rowTop, right, rowTop + rowHeight, kind)
    }

    /** 绘制第二行的星期，今日加粗。 */
    private fun drawDayRow(
        canvas: Canvas,
        metrics: Metrics,
    ) {
        val today = LocalDate.now()
        val top = metrics.row2Top + (metrics.row2Height - InkDraw.lineHeight(labelPaint)) / 2f
        days.forEachIndexed { index, date ->
            val paint = if (date == today) boldLabelPaint else labelPaint
            val centerX = metrics.gridLeft + metrics.columnWidth * (index + 0.5f)
            InkDraw.textCenter(canvas, TodoWeekCalendar.weekdayLabel(date), centerX, top, paint)
        }
    }

    /** 绘制第三行的日期，今天以实心圆反白。 */
    private fun drawDateRow(
        canvas: Canvas,
        metrics: Metrics,
    ) {
        val today = LocalDate.now()
        val top = metrics.row3Top + (metrics.row3Height - InkDraw.lineHeight(labelPaint)) / 2f
        days.forEachIndexed { index, date ->
            val centerX = metrics.gridLeft + metrics.columnWidth * (index + 0.5f)
            if (date == today) {
                val radius = min(metrics.columnWidth * 0.32f, metrics.row3Height * 0.34f)
                val centerY = metrics.row3Top + metrics.row3Height / 2f
                canvas.drawCircle(centerX, centerY, radius, solidPaint)
                drawTodayNumber(canvas, date.dayOfMonth.toString(), centerX, top, radius)
            } else {
                InkDraw.textCenter(canvas, date.dayOfMonth.toString(), centerX, top, labelPaint)
            }
        }
    }

    /** 在实心圆内绘制反白的日期数字。 */
    private fun drawTodayNumber(
        canvas: Canvas,
        text: String,
        centerX: Float,
        top: Float,
        radius: Float,
    ) {
        val originalSize = boldLabelPaint.textSize
        val size = min(originalSize, radius * 1.15f)
        boldLabelPaint.color = InkColors.PAPER
        boldLabelPaint.textSize = size
        val width = boldLabelPaint.measureText(text)
        canvas.drawText(text, centerX - width / 2f, top - boldLabelPaint.ascent(), boldLabelPaint)
        boldLabelPaint.color = InkColors.INK
        boldLabelPaint.textSize = originalSize
    }

    /** 绘制七列内容并按当前滚动位置裁剪。 */
    private fun drawBody(
        canvas: Canvas,
        metrics: Metrics,
    ) {
        val textWidth = max(0f, metrics.columnWidth - boxSide(metrics) - metrics.gap * 0.4f)
        val columns = groups.map { items -> buildCells(items, textWidth) }
        contentHeight = columns.maxOfOrNull { list -> list.lastOrNull()?.let { it.top + it.height } ?: 0f } ?: 0f
        maxScroll = max(0f, contentHeight - max(0f, metrics.bodyBottom - metrics.bodyTop))
        scrollOffset = scrollOffset.coerceIn(0f, maxScroll)
        bodyTop = metrics.bodyTop
        bodyBottom = metrics.bodyBottom
        if (contentHeight <= 0f) {
            InkDraw.textCenter(
                canvas,
                "这七天没有待办",
                (metrics.gridLeft + metrics.gridRight) / 2f,
                metrics.bodyTop,
                labelPaint,
            )
            return
        }
        canvas.save()
        canvas.clipRect(metrics.gridLeft, metrics.bodyTop, metrics.gridRight, metrics.bodyBottom)
        columns.forEachIndexed { index, cells -> drawColumn(canvas, metrics, index, cells) }
        canvas.restore()
    }

    /** 勾选框边长，随列宽收敛，给正文留出折行空间。 */
    private fun boxSide(metrics: Metrics): Float = min(metrics.bodySize * 0.78f, metrics.columnWidth * 0.2f)

    /** 计算一列内每条待办的位置与折行。 */
    private fun buildCells(
        items: List<WereadTodo>,
        textWidth: Float,
    ): List<Cell> {
        val lineHeight = InkDraw.lineHeight(bodyPaint)
        val padV = lineHeight * 0.3f
        var top = 0f
        return items.map { todo ->
            val lines = InkDraw.wrap(todo.content, bodyPaint, textWidth, MAX_LINES)
            val timeHeight = if (todo.hasTime) lineHeight else 0f
            val height = padV * 2 + timeHeight + lines.size * lineHeight
            Cell(todo, top, height, lines).also { top += height }
        }
    }

    /** 绘制一列内容并登记勾选与编辑命中区域。 */
    private fun drawColumn(
        canvas: Canvas,
        metrics: Metrics,
        index: Int,
        cells: List<Cell>,
    ) {
        val columnLeft = metrics.gridLeft + metrics.columnWidth * index
        val box = boxSide(metrics)
        val textLeft = columnLeft + box + metrics.gap * 0.25f
        val textRight = columnLeft + metrics.columnWidth - metrics.gap * 0.15f
        cells.forEach { cell ->
            val top = metrics.bodyTop - scrollOffset + cell.top
            if (top > metrics.bodyBottom || top + cell.height < metrics.bodyTop) return@forEach
            drawCell(canvas, cell, columnLeft, top, textLeft, textRight, box)
        }
    }

    /** 绘制一条待办，时间行与正文按是否设时间排布。 */
    private fun drawCell(
        canvas: Canvas,
        cell: Cell,
        columnLeft: Float,
        top: Float,
        textLeft: Float,
        textRight: Float,
        box: Float,
    ) {
        val lineHeight = InkDraw.lineHeight(bodyPaint)
        val padV = lineHeight * 0.3f
        var rowTop = top + padV
        if (cell.todo.hasTime) {
            drawCheckBox(canvas, columnLeft, rowTop, box, cell.todo.done)
            val timeLabel = cell.todo.timeLabel()
            val timePaint =
                TextPaint(bodyPaint).apply {
                    val measured = measureText(timeLabel)
                    if (measured > textRight - textLeft && measured > 0f) {
                        textSize *= max(0f, textRight - textLeft) / measured
                    }
                }
            InkDraw.text(canvas, timeLabel, textLeft, rowTop, timePaint)
            addBodyHit(columnLeft, rowTop, columnLeft + box, rowTop + box, Hit.Kind.TOGGLE, cell.todo.id)
            rowTop += lineHeight
        }
        val paint = if (cell.todo.done) donePaint else bodyPaint
        cell.lines.forEachIndexed { lineIndex, line ->
            InkDraw.text(canvas, line, textLeft, rowTop + lineIndex * lineHeight, paint)
        }
        if (!cell.todo.hasTime) {
            drawCheckBox(canvas, columnLeft, top + padV, box, cell.todo.done)
            addBodyHit(columnLeft, top + padV, columnLeft + box, top + padV + box, Hit.Kind.TOGGLE, cell.todo.id)
        }
        addBodyHit(textLeft, rowTop, textRight, rowTop + cell.lines.size * lineHeight, Hit.Kind.EDIT, cell.todo.id)
    }

    /** 登记正文区命中区域，裁剪到可见内容区，避免滚出视口的条目被误点。 */
    private fun addBodyHit(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        kind: Hit.Kind,
        id: Long,
    ) {
        val visibleTop = max(top, bodyTop)
        val visibleBottom = min(bottom, bodyBottom)
        if (visibleBottom <= visibleTop || right <= left) return
        hits = hits + Hit(left, visibleTop, right, visibleBottom, kind, id)
    }

    /** 绘制方形勾选框。 */
    private fun drawCheckBox(
        canvas: Canvas,
        left: Float,
        top: Float,
        side: Float,
        done: Boolean,
    ) {
        val rect = RectF(left, top, left + side, top + side)
        if (done) {
            canvas.drawRect(rect, solidPaint)
            val checkPaint = Paint(boxPaint).apply { color = InkColors.PAPER }
            canvas.drawLine(left + side * .2f, top + side * .5f, left + side * .42f, top + side * .74f, checkPaint)
            canvas.drawLine(left + side * .42f, top + side * .74f, left + side * .82f, top + side * .25f, checkPaint)
        } else {
            canvas.drawRect(rect, boxPaint)
        }
    }

    private companion object {
        /** 单条待办最多占用的正文行数。 */
        const val MAX_LINES = 4
    }
}
