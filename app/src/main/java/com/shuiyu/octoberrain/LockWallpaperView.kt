package com.shuiyu.octoberrain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.design.InkColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 锁屏壁纸层。
 *
 * 位于锁屏容器的第 0 层，铺满整块屏幕并覆盖状态栏区域；PIN 面板位于本层之上。
 * 无壁纸或解码失败时绘制纸色，保证锁屏始终有可读的纯色背景。
 *
 * 无壁纸时以纸色铺底并绘制细网点，与微读墨记锁屏的空白状态一致；有壁纸时按
 * 视图尺寸铺满，仅在输入面板矩形内叠加半透明白纱，使面板范围内的键盘与文字保持
 * 可读，同时保留面板外沿的壁纸纹理。
 *
 * 路径或量化档位变化后在 IO 线程重新解码，完成后回到主线程重绘。
 */
class LockWallpaperView(
    context: Context,
) : View(context) {
    private var bitmap: Bitmap? = null

    /** 当前已加载的壁纸路径，用于跳过重复解码。 */
    private var loadedPath: String? = null

    /** 当前已加载的量化档位，档位变化时重新解码。 */
    private var loadedQuantize: Int = -1

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    private var scope: CoroutineScope? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val observing = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = observing
        observing.launch {
            AppServices.config.state.collect { config ->
                load(
                    path = config.appearance.lockWallpaperPath,
                    quantize = config.appearance.lockWallpaperQuantize,
                )
                invalidate()
            }
        }
    }

    override fun onDetachedFromWindow() {
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }

    /**
     * 按路径与量化档位加载锁屏壁纸。
     *
     * 参数 path 为配置中的锁屏壁纸路径，空串表示未设置；quantize 为量化档位。
     * 路径与档位均未变化时不重复解码；解码完成时仅当两者仍一致才回填，
     * 避免快速切换导致的错图。
     */
    private fun load(
        path: String,
        quantize: Int,
    ) {
        if (path == loadedPath && quantize == loadedQuantize) return
        loadedPath = path
        loadedQuantize = quantize
        if (path.isBlank()) {
            bitmap = null
            invalidate()
        } else {
            request(path, quantize)
        }
    }

    /** 在 IO 线程解码指定路径与档位的锁屏壁纸，完成时校验状态未变后回填。 */
    private fun request(
        path: String,
        quantize: Int,
    ) {
        val observing = scope ?: return
        observing.launch {
            val targetWidth = if (width > 0) width else resources.displayMetrics.widthPixels
            val targetHeight = if (height > 0) height else resources.displayMetrics.heightPixels
            val loaded =
                withContext(Dispatchers.IO) {
                    runCatching {
                        AppServices.appearance.loadLockWallpaper(
                            targetWidth.coerceAtLeast(1),
                            targetHeight.coerceAtLeast(1),
                        )
                    }.getOrNull()
                }
            if (loadedPath == path && loadedQuantize == quantize) {
                bitmap = loaded
                invalidate()
            }
        }
    }

    /**
     * 绘制锁屏背景与面板白纱。
     *
     * 无壁纸时以纸色填满并叠加网点；有壁纸时位图按视图边界绘制，面板矩形按
     * 宽高比例内缩并使用 0x80 白纱，面板外的壁纸保持原样。
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val target = Rect(0, 0, width, height)
        val current = bitmap
        if (current == null) {
            canvas.drawColor(InkColors.PAPER)
            drawDotGrid(canvas)
            return
        }
        paint.alpha = 255
        canvas.drawBitmap(current, null, target, paint)
        paint.color = Color.argb(PANEL_VEIL_ALPHA, 0xFF, 0xFF, 0xFF)
        canvas.drawRect(
            width * PANEL_MARGIN_RATIO,
            height * PANEL_MARGIN_RATIO,
            width - width * PANEL_MARGIN_RATIO,
            height - height * PANEL_MARGIN_RATIO,
            paint,
        )
        paint.color = Color.BLACK
        paint.alpha = 255
    }

    /**
     * 绘制锁屏空白状态的细网点底纹。
     *
     * 网点按屏幕高度的固定比例等距排列，半径固定为 1.2 像素，颜色取自墨记锁屏
     * 网点色；网点仅用于纯色背景，壁纸背景不叠加，避免破坏图片观感。
     *
     * @param canvas 目标画布
     */
    private fun drawDotGrid(canvas: Canvas) {
        val step = height * DOT_STEP_RATIO
        if (step <= 0f) return
        paint.style = Paint.Style.FILL
        paint.color = DOT_COLOR
        var y = step
        while (y < height) {
            var x = step
            while (x < width) {
                canvas.drawCircle(x, y, DOT_RADIUS, paint)
                x += step
            }
            y += step
        }
        paint.color = Color.BLACK
    }

    private companion object {
        /** 面板白纱透明度，与墨记 0x80FFFFFF 一致。 */
        const val PANEL_VEIL_ALPHA = 0x80

        /** 面板四周留白与屏幕宽高的比例，对应墨记 42/480 与 70/800。 */
        const val PANEL_MARGIN_RATIO = 0.0875f

        /** 网点间距与屏幕高度的比例，与墨记一致。 */
        const val DOT_STEP_RATIO = 0.03f

        /** 网点半径，像素。 */
        const val DOT_RADIUS = 1.2f

        /** 网点颜色，取自墨记锁屏空白状态底纹。 */
        val DOT_COLOR: Int = Color.rgb(0xEC, 0xEC, 0xEC)
    }
}
