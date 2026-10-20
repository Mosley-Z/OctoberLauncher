package com.shuiyu.octoberrain.feature.files.ui.viewer

import android.graphics.Bitmap
import com.shuiyu.octoberrain.core.render.Ditherer
import com.shuiyu.octoberrain.core.render.ImageDecoder

/**
 * 查看器图片解码与灰度处理。
 *
 * 解码尺寸由 [ViewerState.targetPx] 决定，方向归一由 [ImageDecoder] 完成；
 * 灰度与量化沿用 D6 的默认灰度显示与可切换量化，处理结果均为新位图，源位图在内部回收。
 *
 * 全部方法在调用线程执行，界面层应置于 IO 线程调用 [decode]。
 */
object ViewerDecoder {
    /**
     * 一次解码请求。
     *
     * @param minPx 解码目标长边，像素。
     * @param quantized 是否按四档量化，关闭时只做灰度。
     */
    data class Request(
        val minPx: Int,
        val quantized: Boolean,
    )

    /**
     * 由查看器状态与内容区尺寸生成解码请求。
     *
     * @param state 查看器状态。
     * @param contentWidth 内容区宽度，像素。
     * @param contentHeight 内容区高度，像素。
     * @return 解码请求。
     */
    fun requestFor(
        state: ViewerState,
        contentWidth: Int,
        contentHeight: Int,
    ): Request =
        Request(
            minPx = state.targetPx(contentWidth, contentHeight),
            quantized = state.grayscale,
        )

    /**
     * 按请求解码并完成灰度处理。
     *
     * @param path 图片绝对路径。
     * @param request 解码请求。
     * @return 处理后的位图；解码失败时返回 null。
     */
    fun decode(
        path: String,
        request: Request,
    ): Bitmap? {
        val decoded = ImageDecoder.decode(path, request.minPx) ?: return null
        val tone = if (request.quantized) Ditherer.dither(decoded) else Ditherer.grayscale(decoded)
        if (tone !== decoded) decoded.recycle()
        return tone
    }
}
