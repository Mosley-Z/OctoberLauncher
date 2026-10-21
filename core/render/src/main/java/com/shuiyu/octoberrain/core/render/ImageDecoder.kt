package com.shuiyu.octoberrain.core.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * 统一解码入口：只读边界探测、两遍采样、EXIF 方向归一与堆预算判断。
 *
 * 移植自墨盘 `ImageDecode.java`，常量与判定规则见 [ImageSizing]。全部方法同步执行，
 * 调用方必须保证不在绘制线程内调用，读盘与解码会明显阻塞。
 *
 * 解码失败路径较多（路径为空、边界无效、超出像素上限、堆余量不足、系统解码返回空），
 * 统一返回 null 由调用方渲染占位，因此对返回语句数量约束做集中抑制。
 */
@Suppress("ReturnCount")
object ImageDecoder {
    /** 流式解码允许读取的字节上限，避免异常输入一次占满堆。 */
    private const val STREAM_LIMIT_BYTES = 32 * 1024 * 1024

    /** 未知类型的 MIME。 */
    const val MIME_UNKNOWN = "application/octet-stream"

    /**
     * 只读边界探测。
     *
     * 参数 path 为文件绝对路径；返回长度 2 的数组，元素依次为宽与高。
     * 文件不存在、不是图片、像素总数超过 [ImageSizing.LIMIT_PIXELS] 时返回 null。
     */
    fun size(path: String): IntArray? {
        if (path.isEmpty()) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(path, options) }
        return boundsOf(options)
    }

    /**
     * 只读边界探测，不施加像素总量上限。
     *
     * 参数 path 为文件绝对路径；返回长度 2 的数组，元素依次为宽与高。
     * 文件不存在、不是图片或尺寸非正时返回 null。用于保存壁纸等只需判断文件能否识别为图片的场景，
     * 超大图的解码压力由调用方的采样与堆预算判断承担，本方法只读文件头，不分配像素缓冲区。
     */
    fun sizeIgnoringLimit(path: String): IntArray? {
        if (path.isEmpty()) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(path, options) }
        return boundsOf(options, enforceLimit = false)
    }

    /**
     * 两遍采样解码本地文件。
     *
     * 第一遍只读边界，第二遍按 2 的幂采样解码，使结果长边不超过目标的两倍；
     * 随后按 EXIF 方向旋转或镜像。参数 minPx 小于 1 按 1 处理，超过 1600 按 1600 处理。
     *
     * 参数 keepAlpha 为 true 时保留 ARGB_8888 透明通道，用于带透明边缘的图标；
     * 为 false 时使用 RGB_565 降低常驻内存。解码失败返回 null。
     */
    fun decode(
        path: String,
        minPx: Int,
        keepAlpha: Boolean = false,
    ): Bitmap? {
        if (path.isEmpty()) return null
        val bounds = size(path) ?: return null
        val sample = ImageSizing.sampleFor(maxOf(bounds[0], bounds[1]), minPx)
        if (!heapAllows(bounds[0], bounds[1], sample)) return null
        val bitmap = decodeFileWithSample(path, sample, keepAlpha) ?: return null
        return applyOrientation(bitmap, orientationOf(path))
    }

    /**
     * 按既定采样倍率解码本地文件，供已经完成尺寸决策的调用点使用。
     *
     * 参数 sample 小于 1 按 1 处理；keepAlpha 语义与 [decode] 相同。
     * 解码后同样执行 EXIF 方向归一，失败返回 null。
     */
    fun decodeWithSample(
        path: String,
        sample: Int,
        keepAlpha: Boolean = false,
    ): Bitmap? {
        if (path.isEmpty()) return null
        val bitmap =
            decodeFileWithSample(path, sample.coerceAtLeast(1), keepAlpha) ?: return null
        return applyOrientation(bitmap, orientationOf(path))
    }

    /**
     * 流式解码，用于压缩包内的图标资源等只有输入流、没有文件路径的场景。
     *
     * 先读入字节数组再复用两遍采样流程；限于输入流不可回绕，不读取 EXIF 方向，
     * 图标资源通常不带方向标记。参数 minPx 与 keepAlpha 语义同 [decode]。
     * 读取超过 [STREAM_LIMIT_BYTES] 或解码失败返回 null。
     */
    fun decodeStream(
        input: InputStream,
        minPx: Int,
        keepAlpha: Boolean = false,
    ): Bitmap? {
        val bytes = runCatching { input.readBytes() }.getOrNull() ?: return null
        if (bytes.size > STREAM_LIMIT_BYTES) return null
        val bounds =
            BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds) }
        val size = boundsOf(bounds) ?: return null
        val sample = ImageSizing.sampleFor(maxOf(size[0], size[1]), minPx)
        if (!heapAllows(size[0], size[1], sample)) return null
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = if (keepAlpha) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
                inScaled = false
            }
        return runCatching {
            BitmapFactory.decodeStream(ByteArrayInputStream(bytes), null, options)
        }.getOrNull()
    }

    /**
     * 读取本地文件的 EXIF 方向。
     *
     * 读取失败或非 JPEG 时返回 [ExifInterface.ORIENTATION_NORMAL]。
     */
    private fun orientationOf(path: String): Int =
        runCatching {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    /**
     * 按 EXIF 方向旋转或镜像位图。
     *
     * 变换产生新位图时立即回收原图，避免同一张图片占用两份堆内存；
     * 无需变换或变换失败时返回原图。
     */
    private fun applyOrientation(
        src: Bitmap,
        orientation: Int,
    ): Bitmap {
        val transform = ORIENTATIONS[orientation] ?: return src
        return runCatching {
            val matrix =
                Matrix().apply {
                    setRotate(transform.degrees)
                    if (transform.flipHorizontal) postScale(-1f, 1f)
                    if (transform.flipVertical) postScale(1f, -1f)
                }
            val output = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, false)
            if (output !== src) src.recycle()
            output
        }.getOrDefault(src)
    }

    /**
     * 从边界选项取宽高数组，尺寸非正时返回 null。
     *
     * 参数 enforceLimit 为 true 时同时判断像素总量上限，供常规解码入口使用；
     * 为 false 时只要求宽高为正，供保存壁纸等只需判断文件能否识别为图片的调用点使用。
     */
    private fun boundsOf(
        options: BitmapFactory.Options,
        enforceLimit: Boolean = true,
    ): IntArray? {
        val width = options.outWidth
        val height = options.outHeight
        if (!ImageSizing.hasPositiveSize(width, height)) return null
        if (!enforceLimit) return intArrayOf(width, height)
        return if (ImageSizing.withinLimit(width, height)) intArrayOf(width, height) else null
    }

    /** 按采样值与位图配置解码本地文件，失败返回 null。 */
    private fun decodeFileWithSample(
        path: String,
        sample: Int,
        keepAlpha: Boolean,
    ): Bitmap? {
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = if (keepAlpha) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
                inScaled = false
            }
        return runCatching { BitmapFactory.decodeFile(path, options) }.getOrNull()
    }

    /** 判断堆余量是否足够解码目标尺寸。 */
    private fun heapAllows(
        width: Int,
        height: Int,
        sample: Int,
    ): Boolean {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        val available = runtime.maxMemory() - used
        return ImageSizing.fitsHeap(width, height, sample, available)
    }

    /** 单个 EXIF 方向对应的变换：旋转角度与水平、垂直翻转。 */
    private data class OrientationTransform(
        val degrees: Float,
        val flipHorizontal: Boolean,
        val flipVertical: Boolean,
    )

    /** EXIF 方向到变换参数的映射，未列出的方向按保持不变处理。 */
    private val ORIENTATIONS: Map<Int, OrientationTransform> =
        mapOf(
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL to
                OrientationTransform(0f, flipHorizontal = true, flipVertical = false),
            ExifInterface.ORIENTATION_ROTATE_180 to
                OrientationTransform(180f, flipHorizontal = false, flipVertical = false),
            ExifInterface.ORIENTATION_FLIP_VERTICAL to
                OrientationTransform(0f, flipHorizontal = false, flipVertical = true),
            ExifInterface.ORIENTATION_TRANSPOSE to
                OrientationTransform(90f, flipHorizontal = true, flipVertical = false),
            ExifInterface.ORIENTATION_ROTATE_90 to
                OrientationTransform(90f, flipHorizontal = false, flipVertical = false),
            ExifInterface.ORIENTATION_TRANSVERSE to
                OrientationTransform(-90f, flipHorizontal = true, flipVertical = false),
            ExifInterface.ORIENTATION_ROTATE_270 to
                OrientationTransform(-90f, flipHorizontal = false, flipVertical = false),
        )
}
