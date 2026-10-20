package com.shuiyu.octoberrain.feature.wereadapp.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout

/**
 * 贴底半屏选择器基类。
 *
 * 版式与微读墨记 `feature/NotePickView.java`、`feature/BookPickView.java` 一致：上半屏为
 * 可点击关闭的浅灰遮罩，下半屏为白色底板，顶部一行标题与「关闭」，其下为可拖动滚动的
 * 行列表。选中行用左侧黑竖条标记，行与行之间用浅色分隔线隔开。
 *
 * 子类通过覆写比例、行高与标题等属性决定具体形态，并在 [populate] 中填充行数据。
 *
 * @param context 创建视图的上下文
 */
@Suppress("TooManyFunctions")
internal abstract class WereadPickSheet(
    context: Context,
) : FrameLayout(context) {
    /** 选择器中的一行。 */
    internal data class PickRow(
        /** 行标识，空串表示「自动」或「全部」等特殊行。 */
        val id: String,
        /** 主标题。 */
        val title: String,
        /** 右侧说明文字。 */
        val meta: String,
    )

    /** 选定行的回调，参数为行标识。 */
    var onPick: ((String) -> Unit)? = null

    /** 当前展示的行。 */
    protected val rows: MutableList<PickRow> = ArrayList()

    /** 当前选中的行标识，空串表示首行。 */
    protected var selectedId: String = ""

    /** 底板占屏高比例。 */
    protected abstract val sheetRatio: Float

    /** 标题行高度，单位 dp。 */
    protected abstract val titleHeightDp: Float

    /** 行高，单位 dp。 */
    protected abstract val rowHeightDp: Float

    /** 标题文字。 */
    protected abstract val sheetTitle: String

    /** 是否带搜索行。 */
    protected open val hasSearch: Boolean get() = false

    /** 搜索框提示文字。 */
    protected open val searchHint: String get() = ""

    /** 遮罩上的提示文字。 */
    protected open val scrimHint: String get() = "点空白处关闭"

    /** 搜索行高度，单位 dp。 */
    protected open val searchHeightDp: Float get() = 54f

    /** 底板左右内边距，单位 dp。 */
    protected open val paddingXDp: Float get() = 16f

    /** 行内容为空时的占位文字。 */
    protected open val emptyHint: String get() = "没有匹配的书"

    /** 主画笔。 */
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    /** 当前对话框。 */
    private var dialog: Dialog? = null

    /** 列表滚动位移。 */
    private var scrollY = 0f

    /** 按下点纵坐标。 */
    private var downY = 0f

    /** 按下时的滚动位移。 */
    private var downScroll = 0f

    /** 是否处于拖动状态。 */
    private var dragging = false

    /** 底板顶部纵坐标。 */
    private var sheetTop = 0f

    /** 标题行高度，单位像素。 */
    private var titleHeightPx = 0f

    /** 搜索行高度，单位像素。 */
    private var searchHeightPx = 0f

    /** 列表顶部纵坐标。 */
    private var listTop = 0f

    /** 列表可视高度。 */
    private var listHeight = 0f

    /** 最大滚动位移。 */
    private var maxScroll = 0f

    /** 关闭按钮触摸区。 */
    private val closeRect = android.graphics.RectF()

    /** 搜索输入框，首次展示时按需创建。 */
    private var search: EditText? = null

    init {
        setWillNotDraw(false)
        setBackgroundColor(Color.TRANSPARENT)
    }

    /** 创建搜索输入框并挂到当前视图。 */
    private fun ensureSearch() {
        if (!hasSearch || search != null) return
        val created =
            EditText(context).apply {
                setSingleLine(true)
                background = null
                inputType = InputType.TYPE_CLASS_TEXT
                setPadding(0, 0, 0, 0)
                textSize = 16f
                setTextColor(Color.BLACK)
                addTextChangedListener(
                    object : android.text.TextWatcher {
                        override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int,
                        ) = Unit

                        override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int,
                        ) = Unit

                        override fun afterTextChanged(s: android.text.Editable?) {
                            reload(s?.toString().orEmpty().trim())
                        }
                    },
                )
            }
        search = created
        addView(created)
    }

    /** 打开半屏弹层。 */
    fun show(address: String = "") {
        selectedId = address
        ensureSearch()
        reload("")
        val created = Dialog(context, android.R.style.Theme_DeviceDefault_Light_NoActionBar)
        val params =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        created.setContentView(this, params)
        created.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        created.show()
        created.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
        )
        dialog = created
    }

    /** 关闭半屏弹层。 */
    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    /** 重新填充行数据。 */
    private fun reload(keyword: String) {
        rows.clear()
        populate(keyword)
        recomputeScroll()
        requestLayout()
        invalidate()
    }

    /**
     * 由子类填充行数据。
     *
     * @param keyword 搜索框关键字，未开启搜索时为空串
     */
    protected abstract fun populate(keyword: String)

    /** 把 dp 值换算为像素。 */
    protected fun dp(value: Float): Float = value * resources.displayMetrics.density

    /** 把 sp 值换算为像素。 */
    protected fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity

    /** 重新计算滚动上限。 */
    private fun recomputeScroll() {
        val content = rows.size * dp(rowHeightDp)
        maxScroll = (content - listHeight).coerceAtLeast(0f)
        if (scrollY > maxScroll) scrollY = maxScroll
    }

    override fun onMeasure(
        widthSpec: Int,
        heightSpec: Int,
    ) {
        val width = MeasureSpec.getSize(widthSpec)
        val height = MeasureSpec.getSize(heightSpec)
        if (width > 0 && height > 0) {
            val previous = maxScroll
            computeGeometry(width, height)
            recomputeScroll()
            if (maxScroll != previous) scrollY = scrollY.coerceAtMost(maxScroll)
            measureSearch(width)
        }
        setMeasuredDimension(width, height)
    }

    /** 测量搜索输入框，供其按计算出的区域布局。 */
    private fun measureSearch(width: Int) {
        val searchView = search ?: return
        val childWidth = (width - dp(paddingXDp) * 2f).toInt().coerceAtLeast(0)
        val childHeight = searchHeightPx.toInt().coerceAtLeast(0)
        searchView.measure(
            MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY),
        )
    }

    /** 计算各区域位置。 */
    private fun computeGeometry(
        width: Int,
        height: Int,
    ) {
        titleHeightPx = dp(titleHeightDp)
        searchHeightPx = if (hasSearch) dp(searchHeightDp) else 0f
        sheetTop = height * sheetRatio
        listTop = sheetTop + titleHeightPx + searchHeightPx
        listHeight = (height - listTop).coerceAtLeast(0f)
        val padX = dp(paddingXDp)
        val closeTop = sheetTop + dp(2f)
        val closeBottom = sheetTop + titleHeightPx - dp(2f)
        closeRect.set(width - padX - dp(56f), closeTop, width - padX + dp(6f), closeBottom)
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        super.onLayout(changed, left, top, right, bottom)
        val searchView = search ?: return
        val padX = dp(paddingXDp).toInt()
        val searchTop = (sheetTop + titleHeightPx).toInt()
        searchView.layout(padX, searchTop, right - padX, searchTop + searchHeightPx.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return
        drawScrim(canvas, width)
        drawSheet(canvas, width, height)
        drawRows(canvas, width)
    }

    /** 绘制遮罩与关闭提示。 */
    private fun drawScrim(
        canvas: Canvas,
        width: Float,
    ) {
        paint.style = Paint.Style.FILL
        paint.color = SCRIM
        canvas.drawRect(0f, 0f, width, sheetTop, paint)
        paint.color = GRAY
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = sp(14f)
        paint.typeface = Typeface.DEFAULT
        canvas.drawText(scrimHint, width / 2f, sheetTop * 0.5f, paint)
    }

    /** 绘制底板、标题行与搜索行下划线。 */
    private fun drawSheet(
        canvas: Canvas,
        width: Float,
        height: Float,
    ) {
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawRect(0f, sheetTop, width, height, paint)
        paint.color = Color.BLACK
        canvas.drawRect(0f, sheetTop, width, sheetTop + dp(1.5f), paint)

        val titleBase = sheetTop + titleHeightPx * 0.68f
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = sp(17f)
        paint.color = Color.BLACK
        canvas.drawText(sheetTitle, dp(paddingXDp), titleBase, paint)
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = sp(15f)
        paint.color = GRAY
        canvas.drawText(CLOSE_LABEL, width - dp(paddingXDp), titleBase, paint)

        val dividerY = sheetTop + titleHeightPx
        paint.color = HAIR
        canvas.drawRect(0f, dividerY, width, dividerY + 1f, paint)
        if (hasSearch) {
            paint.color = HAIR
            canvas.drawRect(dp(paddingXDp), listTop - 1f, width - dp(paddingXDp), listTop, paint)
            drawSearchHint(canvas)
        }
    }

    /** 搜索框为空时绘制提示文字，与微读自绘搜索行一致。 */
    private fun drawSearchHint(canvas: Canvas) {
        val searchView = search ?: return
        if (searchView.text.isNotEmpty()) return
        val metrics = paint.fontMetrics
        val centerY = sheetTop + titleHeightPx + searchHeightPx / 2f
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = sp(16f)
        paint.color = Color.parseColor("#A8A8A8")
        canvas.drawText(searchHint, dp(paddingXDp), centerY - (metrics.ascent + metrics.descent) / 2f, paint)
    }

    /** 绘制行列表。 */
    private fun drawRows(
        canvas: Canvas,
        width: Float,
    ) {
        if (listHeight <= 0f) return
        if (rows.isEmpty()) {
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = sp(14f)
            paint.color = GRAY
            paint.typeface = Typeface.DEFAULT
            canvas.drawText(emptyHint, width / 2f, listTop + dp(36f), paint)
            return
        }
        val rowHeight = dp(rowHeightDp)
        val padX = dp(paddingXDp)
        canvas.save()
        canvas.clipRect(0f, listTop, width, listTop + listHeight)
        val metrics = paint.fontMetrics
        for (index in rows.indices) {
            val rowTop = listTop + index * rowHeight - scrollY
            if (rowTop + rowHeight < listTop || rowTop > listTop + listHeight) continue
            drawRow(canvas, width, rows[index], rowTop, rowHeight, padX, metrics)
        }
        canvas.restore()
    }

    /** 绘制单行。 */
    private fun drawRow(
        canvas: Canvas,
        width: Float,
        row: PickRow,
        rowTop: Float,
        rowHeight: Float,
        padX: Float,
        metrics: Paint.FontMetrics,
    ) {
        val auto = row.id.isEmpty()
        val selected = if (auto) selectedId.isEmpty() else selectedId == row.id
        val centerY = rowTop + rowHeight / 2f
        val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
        if (selected) {
            paint.style = Paint.Style.FILL
            paint.color = Color.BLACK
            canvas.drawRect(0f, rowTop + dp(6f), dp(4f), rowTop + rowHeight - dp(6f), paint)
        }
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.DEFAULT
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = sp(12.5f)
        paint.color = if (selected) GRAY else LIGHT
        val metaWidth = paint.measureText(row.meta)
        if (metaWidth > 0f) {
            canvas.drawText(row.meta, width - padX, baseline, paint)
        }
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = sp(16f)
        paint.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        paint.color = if (auto) GRAY else Color.BLACK
        val titleMax = (width - padX * 2f) - metaWidth - dp(12f)
        val title = TextUtils.ellipsize(row.title, paint, titleMax, TextUtils.TruncateAt.END).toString()
        canvas.drawText(title, padX + if (selected) dp(8f) else 0f, baseline, paint)
        paint.typeface = Typeface.DEFAULT
        paint.color = HAIR
        canvas.drawRect(padX, rowTop + rowHeight - 1f, width - padX, rowTop + rowHeight, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> down(event.y)
            MotionEvent.ACTION_MOVE -> move(event.y)
            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    dragging = false
                } else {
                    tap(event.x, event.y)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    /** 记录按下的起点。 */
    private fun down(y: Float) {
        downY = y
        downScroll = scrollY
        dragging = false
    }

    /** 处理拖动滚动。 */
    private fun move(y: Float) {
        if (kotlin.math.abs(y - downY) > dp(SLOP_DP)) dragging = true
        if (!dragging) return
        scrollY = (downScroll + (downY - y)).coerceIn(0f, maxScroll)
        invalidate()
    }

    /** 处理点击：遮罩与关闭按钮关闭弹层，行区域选中并关闭。 */
    private fun tap(
        x: Float,
        y: Float,
    ) {
        if (y < sheetTop || closeRect.contains(x, y)) {
            dismiss()
        } else {
            pickRowAt(y)
        }
    }

    /** 命中行时回调选中并关闭弹层。 */
    private fun pickRowAt(y: Float) {
        if (y < listTop || y > listTop + listHeight) return
        val index = ((y - listTop + scrollY) / dp(rowHeightDp)).toInt()
        val row = rows.getOrNull(index) ?: return
        selectedId = row.id
        onPick?.invoke(row.id)
        dismiss()
    }

    /** 释放弹层持有的输入焦点。 */
    override fun onDetachedFromWindow() {
        dialog = null
        super.onDetachedFromWindow()
    }

    private companion object {
        /** 遮罩颜色。 */
        const val SCRIM = 0xFFD8D8D8.toInt()

        /** 次要文字颜色。 */
        const val GRAY = 0xFF3C3C3C.toInt()

        /** 弱化文字颜色。 */
        const val LIGHT = 0xFFA8A8A8.toInt()

        /** 分隔线颜色。 */
        const val HAIR = 0xFFD8D8D8.toInt()

        /** 拖动判定距离，单位 dp。 */
        const val SLOP_DP = 12f

        /** 关闭按钮文字。 */
        const val CLOSE_LABEL = "关闭"
    }
}
