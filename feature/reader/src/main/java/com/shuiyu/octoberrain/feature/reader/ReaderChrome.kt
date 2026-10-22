package com.shuiyu.octoberrain.feature.reader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.view.MotionEvent
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkWidgets

/** 无翻页动画，正文有独立可测量区域；所有控制按钮常显。 */
@SuppressLint("ViewConstructor") // 仅由ReaderScreen以回调构造，不参与XML布局或预览。
internal class ReaderChrome(
    context: Context,
    back: () -> Unit,
    turn: (Int) -> Unit,
    options: () -> Unit,
) : LinearLayout(context) {
    private val tokens = DesignRuntime.tokens
    val title = InkWidgets.body(context, "文件阅读").apply { id = R.id.reader_title }
    val status = InkWidgets.caption(context, "正在打开…").apply { id = R.id.reader_status }
    val page = ReaderPageView(context, turn)
    val previous = InkWidgets.button(context, "上一页") { turn(-1) }.apply { id = R.id.reader_previous }
    val next = InkWidgets.button(context, "下一页") { turn(1) }.apply { id = R.id.reader_next }
    private val more = InkWidgets.button(context, "阅读设置") { options() }.apply { id = R.id.reader_settings }

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.WHITE)
        val top =
            LinearLayout(context).apply {
                addView(InkWidgets.button(context, "返回", onClick = back))
                title.maxLines = 1
                title.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                addView(title, LayoutParams(0, tokens.buttonHeight, 1f))
            }
        addView(top)
        addView(status, LayoutParams(-1, tokens.dp(32f)))
        addView(page, LayoutParams(-1, 0, 1f))
        val bottom =
            LinearLayout(context).apply {
                listOf(previous, more, next).forEach { button ->
                    // 窄屏 480px 下三按钮等分，单行截断避免换行挤压正文区。
                    button.maxLines = 1
                    button.ellipsize = android.text.TextUtils.TruncateAt.END
                    addView(button, LayoutParams(0, tokens.buttonHeight, 1f))
                }
            }
        addView(bottom)
    }

    fun busy(value: Boolean) {
        previous.isEnabled = !value
        next.isEnabled = !value
        more.isEnabled = !value
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setPadding(paddingLeft, paddingTop, paddingRight, gestureFallback())
        requestApplyInsets()
        post { rootWindowInsets?.let { applyReaderInsets(it) } }
    }

    @Suppress("DEPRECATION") // API26–29 have no WindowInsets.Type; still honor their navigation/gesture insets.
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        applyReaderInsets(rootWindowInsets ?: insets)
        return insets
    }

    @Suppress("DEPRECATION") // API26–29 compatibility.
    private fun applyReaderInsets(insets: WindowInsets) {
        val bottom =
            when {
                Build.VERSION.SDK_INT >= 30 ->
                    insets
                        .getInsetsIgnoringVisibility(
                            WindowInsets.Type.navigationBars() or WindowInsets.Type.systemGestures(),
                        ).bottom
                Build.VERSION.SDK_INT >= 29 -> maxOf(insets.systemWindowInsetBottom, insets.systemGestureInsets.bottom)
                else -> insets.systemWindowInsetBottom
            }
        setPadding(paddingLeft, paddingTop, paddingRight, maxOf(bottom, gestureFallback()))
    }

    @SuppressLint("DiscouragedApi") // S4 launcher reports zero insets; use its actual gesture-mode system dimension.
    private fun gestureFallback(): Int {
        val mode =
            android.provider.Settings.Secure
                .getInt(context.contentResolver, "navigation_mode", 0)
        if (mode != 2) return 0
        val id = resources.getIdentifier("navigation_bar_gesture_height", "dimen", "android")
        return if (id == 0) 0 else resources.getDimensionPixelSize(id)
    }
}

/** 帧只读；替换/退出时移除引用，避免绘制访问已关闭引擎。 */
@SuppressLint("ViewConstructor") // 仅由ReaderChrome以翻页回调构造。
internal class ReaderPageView(
    context: Context,
    private val turn: (Int) -> Unit,
) : FrameLayout(context) {
    var frame: ReaderFrame? = null
        set(value) {
            field = value
            invalidate()
        }

    /** 布局尺寸变化回调，旋转或窗口尺寸改变时供 ReaderScreen 重排。 */
    var sizeListener: ((Int, Int) -> Unit)? = null
    var contentDrawn: (() -> Unit)? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val destination = Rect()
    val inset = DesignRuntime.tokens.dp(16f)
    private var downX = 0f

    init {
        isClickable = true
        contentDescription = "正文，点按翻到下一页"
        setWillNotDraw(false)
        setBackgroundColor(Color.WHITE)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(inset.toFloat(), inset.toFloat())
        canvas.clipRect(0, 0, width - inset * 2, height - inset * 2)
        frame?.text?.draw(canvas)
        destination.set(0, 0, width - inset * 2, height - inset * 2)
        frame?.bitmap?.let { canvas.drawBitmap(it, null, destination, paint) }
        canvas.restore()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val completed = contentDrawn
        contentDrawn = null
        completed?.invoke()
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) sizeListener?.invoke(w, h)
    }

    override fun performClick(): Boolean {
        super.performClick()
        turn(1)
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) downX = event.x
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            val delta = event.x - downX
            when {
                delta < -width / 6 -> turn(1)
                delta > width / 6 -> turn(-1)
                event.x < width / 3 -> turn(-1)
                event.x > width * 2 / 3 -> performClick()
            }
        }
        return true
    }
}
