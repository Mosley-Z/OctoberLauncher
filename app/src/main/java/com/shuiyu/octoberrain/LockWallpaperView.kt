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
 * 壁纸位图按视图尺寸绘制，并叠加固定 alpha 的白色压层提升键盘与文字对比度；
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
     * 绘制锁屏壁纸与白色压层。
     *
     * 位图按视图边界绘制；无壁纸时以纸色填满，白色压层固定使用 0xB8 透明度，
     * 覆盖状态栏区域，为面板上的黑色线条提供稳定底色。
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val target = Rect(0, 0, width, height)
        val current = bitmap
        if (current == null) {
            canvas.drawColor(InkColors.PAPER)
            return
        }
        paint.alpha = 255
        canvas.drawBitmap(current, null, target, paint)
        paint.color = Color.argb(OVERLAY_ALPHA, 0xFF, 0xFF, 0xFF)
        canvas.drawRect(target, paint)
        paint.color = Color.BLACK
        paint.alpha = 255
    }

    private companion object {
        /** 锁屏白色压层透明度，约 72% 白。 */
        const val OVERLAY_ALPHA = 0xB8
    }
}
