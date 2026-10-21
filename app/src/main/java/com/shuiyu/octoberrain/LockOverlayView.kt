package com.shuiyu.octoberrain

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import kotlin.math.sin

/**
 * 校验结果三态：通过、密码错误、已作废。
 *
 * 已作废表示校验期间发生了熄屏、重新锁定或改密，本次输入的结果不再具备提交条件，
 * 界面回到等待输入状态且不提示错误。
 */
enum class LockVerifyResult {
    /** 校验通过。 */
    PASSED,

    /** 密码错误。 */
    FAILED,

    /** 校验期间锁状态变化，结果作废。 */
    CANCELLED,
}

/**
 * 锁屏覆盖视图：自绘四位数字键盘，样式与微读墨记锁屏对齐。
 *
 * 面板按屏幕高度等比布局：标题基线位于 0.20 高度，四位圆点位于 0.32 高度，
 * 错误提示位于 0.415 高度，键盘区域占 0.50 至 0.90 高度并居中排列三列四行；
 * 按键为白底黑描边的圆形，删除键位于右下角，输入满四位立即提交。
 *
 * PIN 校验由 [verify] 在后台线程执行并在完成后回调主线程，避免 PBKDF2 迭代阻塞
 * 输入事件派发；校验期间忽略按键，失败后清空输入并播放抖动动画，结果作废时
 * 清空输入直接回到等待输入状态。
 *
 * 应急清除：按住删除键满十秒期间仅在删除键外圈绘制进度圆弧，达到阈值后通过
 * [onEmergencyReset] 请求宿主弹出确认框，确认框与清除逻辑均由宿主实现。
 *
 * 锁屏背景由锁屏壁纸层提供，面板不绘制选图入口；选图入口位于微读墨记设置页的「锁屏密码」页签。
 */
class LockOverlayView(
    context: Context,
    private val verify: (String, (LockVerifyResult) -> Unit) -> Unit,
    private val onUnlocked: () -> Unit,
    private val remainingAttempts: () -> Int = { 0 },
    private val lockoutSeconds: () -> Long = { 0L },
    private val emergencyResetEnabled: () -> Boolean = { true },
    private val onEmergencyReset: () -> Unit = {},
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val digits = StringBuilder()
    private var message: String = ""

    /** 校验进行中标记，期间忽略按键并把提示切换为校验文案。 */
    private var verifying = false

    /** 抖动帧计数：0 表示静止，大于 0 表示正在抖动，抖完清空输入。 */
    private var shake = 0

    private val labels = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "删除")

    private var padLeft = 0f
    private var padTop = 0f
    private var padWidth = 0f
    private var cellWidth = 0f
    private var cellHeight = 0f
    private var keyRadius = 0f

    /** 抖动推进器：每帧递增计数，结束后停止并清空输入。 */
    private val shakeTicker =
        object : Runnable {
            override fun run() {
                if (shake <= 0) return
                shake += 1
                if (shake > SHAKE_FRAMES) {
                    shake = 0
                    digits.setLength(0)
                }
                invalidate()
                if (shake > 0) postDelayed(this, SHAKE_FRAME_MILLIS)
            }
        }

    /** 删除键长按进度，0 表示未按压，达到 1 时触发应急清除。 */
    private var holdProgress = 0f

    /** 删除键外圈进度圆弧的绘制边界，复用同一实例避免逐帧分配。 */
    private val holdArc = RectF()

    /** 删除键长按计时器，按住满十秒请求宿主弹出应急清除确认框。 */
    private val deleteHold =
        DeleteHoldTracker(
            host = this,
            onProgress = { value ->
                holdProgress = value
                invalidate()
            },
            onReached = {
                if (emergencyResetEnabled()) onEmergencyReset()
            },
        )

    /** 触点命中的按键索引，落在面板之外返回 -1；绘制与命中测试共用同一套几何。 */
    private val hitIndex: (Float, Float) -> Int = { x, y ->
        if (padWidth <= 0f || x < padLeft) {
            -1
        } else if (x > padLeft + padWidth || y < padTop) {
            -1
        } else {
            val col = ((x - padLeft) / cellWidth).toInt().coerceIn(0, COLUMNS - 1)
            val row = ((y - padTop) / cellHeight).toInt().coerceIn(0, ROWS - 1)
            row * COLUMNS + col
        }
    }

    init {
        // 背景透明，纸色、网点与壁纸由锁屏壁纸层提供，面板只绘制自身内容。
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return
        layoutPad(width, height)
        drawTitle(canvas, width, height)
        drawDots(canvas, width, height)
        drawMessage(canvas, width, height)
        drawKeypad(canvas)
    }

    /**
     * 处理触摸事件。
     *
     * 按下事件只用于启动删除键的应急清除计时，其余键位保持按下不处理、抬起时一次性分发；
     * 抬起与取消事件终止计时，触发过应急清除的按压在抬起时不再分发按键。
     *
     * @param event 触摸事件
     * @return 始终返回 true，表示事件已消费
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val index = hitIndex(event.x, event.y)
        if (action == MotionEvent.ACTION_DOWN) {
            // 校验中与抖动中维持原有忽略按键的行为，不启动计时。
            if (index == KEY_DEL && !verifying && shake == 0) {
                if (emergencyResetEnabled()) deleteHold.start()
            }
            return true
        }
        // 按住删除键期间的细微移动仍视为同一次按压，避免手指抖动中断计时。
        if (action == MotionEvent.ACTION_MOVE && index == KEY_DEL) return true
        val emergencyPressed = deleteHold.takeReached()
        deleteHold.cancel()
        if (action != MotionEvent.ACTION_UP) return true
        if (emergencyPressed) return true
        if (index >= 0) {
            onKey(index)
        }
        return true
    }

    /**
     * 处理按键：数字追加、删除回退、空白格忽略，输满四位立即提交校验。
     *
     * @param index 按键在一维序列中的位置，0 至 11 对应三列四行
     */
    private fun onKey(index: Int) {
        if (verifying || shake > 0 || index == KEY_BLANK) return
        when (index) {
            KEY_DEL -> {
                if (digits.isNotEmpty()) digits.deleteCharAt(digits.length - 1)
            }
            else -> if (digits.length < PIN_LENGTH) digits.append(labels[index])
        }
        message = ""
        invalidate()
        if (digits.length == PIN_LENGTH) submit()
    }

    /**
     * 提交四位密码校验：校验期间锁定按键，完成后按结果解锁、抖动提示或静默复位。
     *
     * 校验通过后隐藏自身并通过 [onUnlocked] 通知宿主；密码错误时保留四位圆点的
     * 实心状态播放抖动，抖动结束由 [shakeTicker] 清空输入；结果作废时清空输入与
     * 校验提示，不播放抖动，回到等待输入状态。
     */
    private fun submit() {
        if (verifying || digits.length != PIN_LENGTH) return
        verifying = true
        message = "校验中…"
        invalidate()
        verify(digits.toString()) { result ->
            verifying = false
            when (result) {
                LockVerifyResult.PASSED -> {
                    digits.setLength(0)
                    message = ""
                    invalidate()
                    visibility = GONE
                    onUnlocked()
                }
                LockVerifyResult.FAILED -> {
                    val lockout = lockoutSeconds()
                    val remaining = remainingAttempts()
                    message =
                        when {
                            lockout > 0L -> "尝试次数过多，$lockout 秒后重试"
                            remaining in 1..2 -> "密码错误，剩余 $remaining 次"
                            else -> "密码错误，请重试"
                        }
                    startShake()
                }
                LockVerifyResult.CANCELLED -> {
                    digits.setLength(0)
                    message = ""
                    invalidate()
                }
            }
        }
    }

    /** 启动抖动动画：从第一帧开始按固定间隔推进，抖动期间忽略按键。 */
    private fun startShake() {
        shake = 1
        invalidate()
        postDelayed(shakeTicker, SHAKE_FRAME_MILLIS)
    }

    /**
     * 计算面板与键盘几何，绘制与命中测试共用同一套结果。
     *
     * @param width 视图宽度
     * @param height 视图高度
     */
    private fun layoutPad(
        width: Float,
        height: Float,
    ) {
        padWidth = width * PAD_WIDTH_RATIO
        padLeft = (width - padWidth) / 2f
        padTop = height * PAD_TOP_RATIO
        cellWidth = padWidth / COLUMNS
        cellHeight = height * PAD_HEIGHT_RATIO / ROWS
        keyRadius = minOf(cellWidth, cellHeight) * KEY_RADIUS_RATIO
    }

    /**
     * 绘制标题，字号与基线按屏幕高度取比例。
     *
     * @param canvas 目标画布
     * @param width 视图宽度
     * @param height 视图高度
     */
    private fun drawTitle(
        canvas: Canvas,
        width: Float,
        height: Float,
    ) {
        paint.style = Paint.Style.FILL
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = height * TITLE_TEXT_RATIO
        paint.color = InkColors.INK
        paint.typeface = DesignRuntime.typeface()
        canvas.drawText("输入密码解锁", width / 2f, height * TITLE_BASELINE_RATIO, paint)
        paint.isFakeBoldText = false
    }

    /**
     * 绘制四位圆点，已输入项为实心圆，未输入项为白底黑描边圆。
     *
     * 抖动时按正弦位移整体平移圆点，位移幅度随帧号变化。
     *
     * @param canvas 目标画布
     * @param width 视图宽度
     * @param height 视图高度
     */
    private fun drawDots(
        canvas: Canvas,
        width: Float,
        height: Float,
    ) {
        val radius = height * DOT_RADIUS_RATIO
        val step = radius * DOT_STEP_RATIO
        val total = step * (PIN_LENGTH - 1) + radius * 2f
        val startX = (width - total) / 2f + radius
        val centerY = height * DOT_BASELINE_RATIO
        val offsetX = if (shake > 0) sin(shake * Math.PI / 3.0).toFloat() * height * SHAKE_OFFSET_RATIO else 0f
        repeat(PIN_LENGTH) { index ->
            val cx = startX + index * step + offsetX
            if (index < digits.length) {
                paint.style = Paint.Style.FILL
                paint.color = InkColors.INK
                canvas.drawCircle(cx, centerY, radius, paint)
            } else {
                paint.style = Paint.Style.FILL
                paint.color = InkColors.PAPER
                canvas.drawCircle(cx, centerY, radius, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = DOT_STROKE
                paint.color = InkColors.INK
                canvas.drawCircle(cx, centerY, radius, paint)
            }
        }
        paint.style = Paint.Style.FILL
    }

    /**
     * 绘制提示文字，校验中与错误状态共用圆点下方的一行区域。
     *
     * 错误提示使用红色，校验中使用次级墨色，字号按屏幕高度取比例。
     *
     * @param canvas 目标画布
     * @param width 视图宽度
     * @param height 视图高度
     */
    private fun drawMessage(
        canvas: Canvas,
        width: Float,
        height: Float,
    ) {
        val text = message
        if (text.isEmpty()) return
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = height * MESSAGE_TEXT_RATIO
        paint.color = if (verifying) InkColors.INK_2 else ERROR_INK
        canvas.drawText(text, width / 2f, height * MESSAGE_BASELINE_RATIO, paint)
    }

    /**
     * 绘制圆形键盘，数字与删除键共用一套几何。
     *
     * 空白格不绘制；数字字号为半径的 0.95 倍，删除文案为半径的 0.58 倍。
     *
     * @param canvas 目标画布
     */
    private fun drawKeypad(canvas: Canvas) {
        paint.textAlign = Paint.Align.CENTER
        labels.forEachIndexed { index, label ->
            if (index == KEY_BLANK) return@forEachIndexed
            val row = index / COLUMNS
            val col = index % COLUMNS
            val cx = padLeft + cellWidth * col + cellWidth / 2f
            val cy = padTop + cellHeight * row + cellHeight / 2f
            paint.style = Paint.Style.FILL
            paint.color = InkColors.PAPER
            canvas.drawCircle(cx, cy, keyRadius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = KEY_STROKE
            paint.color = InkColors.INK
            canvas.drawCircle(cx, cy, keyRadius, paint)
            if (index == KEY_DEL && holdProgress > 0f) {
                // 进度圆弧自顶端顺时针延伸，圈住删除键外沿，与按键描边同色同风格。
                val arcRadius = keyRadius * HOLD_ARC_RATIO
                holdArc.set(cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius)
                paint.strokeWidth = HOLD_STROKE
                canvas.drawArc(holdArc, -90f, 360f * holdProgress, false, paint)
            }
            paint.style = Paint.Style.FILL
            paint.color = InkColors.INK
            paint.textSize = if (index == KEY_DEL) keyRadius * DELETE_TEXT_RATIO else keyRadius * DIGIT_TEXT_RATIO
            val metrics = paint.fontMetrics
            val baseline = cy - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(label, cx, baseline, paint)
        }
    }

    private companion object {
        /** 输入位数。 */
        const val PIN_LENGTH = 4

        /** 键盘列数。 */
        const val COLUMNS = 3

        /** 键盘行数。 */
        const val ROWS = 4

        /** 空白键位索引，位于第三行第一列。 */
        const val KEY_BLANK = 9

        /** 删除键索引，位于第四行第三列。 */
        const val KEY_DEL = 11

        /** 键盘宽度占视图宽度的比例，与墨记一致。 */
        const val PAD_WIDTH_RATIO = 0.80f

        /** 键盘顶部位置占视图高度的比例，与墨记一致。 */
        const val PAD_TOP_RATIO = 0.50f

        /** 键盘区域高度占视图高度的比例，与墨记一致。 */
        const val PAD_HEIGHT_RATIO = 0.40f

        /** 圆形按键半径与单元格短边的比例，与墨记一致。 */
        const val KEY_RADIUS_RATIO = 0.36f

        /** 标题文字大小占视图高度的比例，与墨记一致。 */
        const val TITLE_TEXT_RATIO = 0.030f

        /** 标题基线位置占视图高度的比例，与墨记一致。 */
        const val TITLE_BASELINE_RATIO = 0.20f

        /** 圆点半径占视图高度的比例，与墨记一致。 */
        const val DOT_RADIUS_RATIO = 0.014f

        /** 圆点间距与半径的比例，与墨记一致。 */
        const val DOT_STEP_RATIO = 4.4f

        /** 圆点圆心位置占视图高度的比例，与墨记一致。 */
        const val DOT_BASELINE_RATIO = 0.32f

        /** 圆点描边宽度。 */
        const val DOT_STROKE = 2.5f

        /** 提示文字大小占视图高度的比例，与墨记一致。 */
        const val MESSAGE_TEXT_RATIO = 0.024f

        /** 提示文字基线位置占视图高度的比例，与墨记一致。 */
        const val MESSAGE_BASELINE_RATIO = 0.415f

        /** 数字文字大小与按键半径的比例，与墨记一致。 */
        const val DIGIT_TEXT_RATIO = 0.95f

        /** 删除文字大小与按键半径的比例，与墨记一致。 */
        const val DELETE_TEXT_RATIO = 0.58f

        /** 按键圆形描边宽度。 */
        const val KEY_STROKE = 2f

        /** 抖动总帧数，与墨记一致。 */
        const val SHAKE_FRAMES = 9

        /** 抖动帧间隔，与墨记一致。 */
        const val SHAKE_FRAME_MILLIS = 40L

        /** 抖动位移峰值与视图高度的比例，与墨记一致。 */
        const val SHAKE_OFFSET_RATIO = 0.018f

        /** 长按进度圆弧半径与按键半径的比例，取略大于按键外沿的值。 */
        const val HOLD_ARC_RATIO = 1.18f

        /** 长按进度圆弧的描边宽度。 */
        const val HOLD_STROKE = 3f

        /** 错误提示颜色，取自墨记锁屏错误文字。 */
        val ERROR_INK: Int = Color.rgb(0xCC, 0x00, 0x00)
    }
}
