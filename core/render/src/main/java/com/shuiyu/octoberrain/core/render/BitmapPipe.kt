package com.shuiyu.octoberrain.core.render

import android.graphics.Bitmap
import java.io.File

/**
 * 位图管线：统一解码与缩放，供图标、封面、壁纸共用。
 *
 * 解码委托 [ImageDecoder]：只读边界、两遍采样、EXIF 方向归一、长边限幅与堆预算判断
 * 均在统一入口内完成，本对象只保留既有调用签名与释放辅助。
 */
object BitmapPipe {
    /**
     * 从文件解码位图。
     *
     * 参数 file 为图片文件；reqWidth 与 reqHeight 为期望的最大尺寸，
     * 传 0 表示该方向不限制。解码失败返回 null，由调用方渲染占位。
     */
    fun decodeFile(
        file: File,
        reqWidth: Int,
        reqHeight: Int,
    ): Bitmap? {
        if (!file.isFile) return null
        val hasRequest = reqWidth > 0 || reqHeight > 0
        val minPx = if (hasRequest) maxOf(reqWidth, reqHeight) else ImageSizing.MAX_TARGET
        return ImageDecoder.decode(file.absolutePath, minPx)
    }

    /**
     * 计算 2 的幂采样倍率。
     *
     * 参数 rawWidth 与 rawHeight 为原图尺寸；reqWidth 与 reqHeight 为期望尺寸，
     * 两者同时为 0 时返回 1。计算规则见 [ImageSizing.requestSample]，像素总量兜底
     * 与既有实现一致。
     */
    fun sampleSize(
        rawWidth: Int,
        rawHeight: Int,
        reqWidth: Int,
        reqHeight: Int,
    ): Int = ImageSizing.requestSample(rawWidth, rawHeight, reqWidth, reqHeight)

    /** 释放位图，重复调用安全。 */
    fun recycle(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
    }
}
