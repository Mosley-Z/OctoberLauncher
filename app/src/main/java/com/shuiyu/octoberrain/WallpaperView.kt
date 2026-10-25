package com.shuiyu.octoberrain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.DesktopMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全屏壁纸层。
 *
 * 位于宿主最底层，覆盖包括状态栏在内的整块屏幕；内容区与状态栏保持透明，
 * 由本视图统一绘制壁纸，避免只有桌面内容区出现壁纸、状态栏仍是白条。
 *
 * 壁纸位图按视图尺寸拉伸绘制；配置开启白色压层或变暗时依次叠加，
 * 壁纸路径变化后在 IO 线程重新解码，解码完成回到主线程重绘。
 */
class WallpaperView(
    context: Context,
) : View(context) {
    private var bitmap: Bitmap? = null

    /** 当前已加载的壁纸路径，用于跳过重复解码。 */
    private var loadedPath: String? = null

    /** 当前已加载的量化档位，档位变化时重新解码。 */
    private var loadedQuantize: Int = -1

    private var loadedRotation = -1
    private var loadedWidth = -1
    private var loadedHeight = -1
    private var requestGeneration = 0L

    private var dimRatio = 0f

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    private var scope: CoroutineScope? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val observing = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = observing
        loadedWidth = -1
        observing.launch {
            AppServices.config.state.collect { config ->
                val immersive = config.desktop.mode == DesktopMode.IMMERSIVE
                dimRatio = if (immersive) 0f else config.appearance.wallpaperDim.coerceIn(0f, 1f)
                load(config.appearance.wallpaperPath, config.appearance.wallpaperQuantize)
                invalidate()
            }
        }
    }

    override fun onDetachedFromWindow() {
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        val appearance = AppServices.config.state.value.appearance
        load(appearance.wallpaperPath, appearance.wallpaperQuantize)
    }

    /**
     * 按路径与量化档位加载壁纸。
     *
     * 参数 path 为配置中的壁纸路径；quantize 为量化档位。
     * 两者均未变化时不重复解码。路径为空时清空位图并立即重绘。
     * 解码在 IO 线程执行，完成后仅在路径与档位仍一致时回填，避免快速切换导致的错图。
     */
    private fun load(
        path: String?,
        quantize: Int,
    ) {
        if (scope == null) return
        val targetWidth = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val targetHeight = height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val rotation =
            if (targetWidth >
                targetHeight
            ) {
                AppServices.config.state.value.appearance.wallpaperLandscapeRotation
            } else {
                0
            }
        val sameImage = path == loadedPath && quantize == loadedQuantize && rotation == loadedRotation
        val sameSize = targetWidth == loadedWidth && targetHeight == loadedHeight
        if (sameImage && sameSize) {
            return
        }
        loadedWidth = targetWidth
        loadedHeight = targetHeight
        loadedRotation = rotation
        requestGeneration++
        loadedPath = path
        loadedQuantize = quantize
        if (path.isNullOrEmpty()) {
            bitmap?.recycle()
            bitmap = null
            invalidate()
        } else {
            request(path, quantize)
        }
    }

    /** 在 IO 线程解码指定路径与档位的桌面壁纸，完成时校验状态未变后回填。 */
    private fun request(
        path: String,
        quantize: Int,
    ) {
        val observing = scope ?: return
        val generation = requestGeneration
        val targetWidth = loadedWidth
        val targetHeight = loadedHeight
        observing.launch {
            val loaded =
                withContext(Dispatchers.IO) {
                    runCatching {
                        AppServices.appearance.loadWallpaper(
                            targetWidth.coerceAtLeast(1),
                            targetHeight.coerceAtLeast(1),
                            quantize,
                        )
                    }.getOrNull()
                }
            if (generation == requestGeneration && loadedPath == path && loadedQuantize == quantize) {
                val previous = bitmap
                bitmap = loaded
                if (previous !== loaded) previous?.recycle()
                invalidate()
            } else {
                loaded?.recycle()
            }
        }
    }

    /**
     * 绘制壁纸与叠加层。
     *
     * 位图按视图边界拉伸；白色压层使用 E0 透明度，变暗层按配置比例叠加黑色，
     * 两个叠加层都覆盖状态栏区域。
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = bitmap ?: return
        val target = Rect(0, 0, width, height)
        paint.alpha = 255
        canvas.drawBitmap(current, null, target, paint)
        if (dimRatio > 0f) {
            paint.color = Color.argb((dimRatio * 255f).toInt(), 0, 0, 0)
            canvas.drawRect(target, paint)
        }
        paint.color = Color.BLACK
        paint.alpha = 255
    }
}
