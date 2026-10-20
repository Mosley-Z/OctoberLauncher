package com.shuiyu.octoberrain.feature.files.ui.viewer

import com.shuiyu.octoberrain.core.render.ImageSizing

/** 查看器缩放档位，名称用于动作面板与提示文案。 */
enum class ViewerZoom(
    val label: String,
) {
    /** 整图可见，留白居中。 */
    FIT("适配"),

    /** 覆盖内容区，溢出部分裁剪。 */
    FILL("填充"),

    /** 原始像素 1:1 中心显示，超出内容区的部分不显示。 */
    ORIGINAL("原尺寸"),
}

/**
 * 查看器的页码、档位与灰度状态。
 *
 * 纯逻辑对象，不引用 Android 类型，换图与缩放的行为可在本地 JVM 单元测试中直接验证。
 * 位图加载与绘制由 PhotoViewer 承担，本类只回答「当前看哪张、按哪档、需要多大解码尺寸」。
 *
 * 参数 paths 为可查看图片的绝对路径列表，空白项被忽略；startIndex 越界时夹取到合法范围。
 * 参数 grayscale 为 D6 规定的默认灰度显示，量化开关的初始值由它承担。
 */
class ViewerState(
    paths: List<String>,
    startIndex: Int = 0,
    grayscale: Boolean = true,
) {
    private val items: List<String> = paths.filter { it.isNotBlank() }

    /** 当前图片下标。 */
    var index: Int = clampIndex(startIndex, items.size)
        private set

    /** 当前缩放档位。 */
    var zoom: ViewerZoom = ViewerZoom.FIT
        private set

    /** 是否按灰度量化显示，默认取自 [grayscale]。 */
    var grayscale: Boolean = grayscale
        private set

    /** 可查看图片数量。 */
    val count: Int get() = items.size

    /** 是否没有可查看的图片。 */
    val isEmpty: Boolean get() = items.isEmpty()

    /** 当前图片路径，列表为空时返回空串。 */
    val path: String get() = items.getOrNull(index).orEmpty()

    /** 全部图片路径，供分享与外部入口复用。 */
    val paths: List<String> get() = items

    /** 是否存在上一张。 */
    val hasPrevious: Boolean get() = index > 0

    /** 是否存在下一张。 */
    val hasNext: Boolean get() = index < items.lastIndex

    /** 页码文案，形如「3/12」；列表为空时为「0/0」。 */
    val pageLabel: String get() = pageLabel(index, count)

    /** 当前档位对应的动作文案，例如「适应」「原尺寸」。 */
    val zoomLabel: String get() = zoom.label

    /**
     * 切换到下一张。
     *
     * @return 下标是否发生变化；已在末张时返回 false。
     */
    fun next(): Boolean = moveTo(index + 1)

    /**
     * 切换到上一张。
     *
     * @return 下标是否发生变化；已在首张时返回 false。
     */
    fun previous(): Boolean = moveTo(index - 1)

    /**
     * 跳到指定下标。
     *
     * @param value 目标下标，越界时返回 false 且不改变状态。
     * @return 下标是否发生变化。
     */
    fun moveTo(value: Int): Boolean {
        if (value !in items.indices || value == index) return false
        index = value
        return true
    }

    /**
     * 按适配、填充、原尺寸、适配的顺序循环切换档位。
     *
     * @return 切换后的档位。
     */
    fun cycleZoom(): ViewerZoom {
        zoom =
            when (zoom) {
                ViewerZoom.FIT -> ViewerZoom.FILL
                ViewerZoom.FILL -> ViewerZoom.ORIGINAL
                ViewerZoom.ORIGINAL -> ViewerZoom.FIT
            }
        return zoom
    }

    /**
     * 重新显示时恢复为适配档位，页码保持不变。
     */
    fun resetZoom() {
        zoom = ViewerZoom.FIT
    }

    /**
     * 切换灰度量化开关。
     *
     * @return 切换后的开关状态。
     */
    fun toggleGrayscale(): Boolean {
        grayscale = !grayscale
        return grayscale
    }

    /**
     * 计算当前档位需要的解码长边。
     *
     * 适配取内容区短边；填充取长边；原尺寸取长边的两倍并按 [ImageSizing.MAX_EDGE] 截断。
     * 内容区任一边不大于 0 时返回 1，交由解码器按最小尺寸处理。
     *
     * @param contentWidth 内容区宽度，像素。
     * @param contentHeight 内容区高度，像素。
     * @return 解码目标长边，像素。
     */
    fun targetPx(
        contentWidth: Int,
        contentHeight: Int,
    ): Int {
        val longEdge = maxOf(contentWidth, contentHeight)
        val shortEdge = minOf(contentWidth, contentHeight)
        if (longEdge <= 0) return 1
        val base =
            when (zoom) {
                ViewerZoom.FIT -> shortEdge
                ViewerZoom.FILL -> longEdge
                ViewerZoom.ORIGINAL -> longEdge * 2
            }
        return base.coerceIn(1, ImageSizing.MAX_EDGE)
    }

    /**
     * 预取计划：当前图片的前一张与后一张，按下标升序，越界项被剔除。
     *
     * @return 需要预取的图片下标列表。
     */
    fun prefetchIndices(): List<Int> {
        if (items.isEmpty()) return emptyList()
        val candidates = listOf(index - 1, index + 1)
        return candidates.filter { it in items.indices }
    }

    /**
     * 判断解码结果是否仍可显示。
     *
     * 解码线程回传结果时，代际、请求下标与当前状态任一不符即丢弃，避免快速换图时旧图覆盖新图。
     *
     * @param resultGeneration 发起请求时的代际。
     * @param currentGeneration 当前代际。
     * @param requestedIndex 发起请求时的下标。
     * @return 是否接受该结果。
     */
    fun shouldAccept(
        resultGeneration: Int,
        currentGeneration: Int,
        requestedIndex: Int,
    ): Boolean = resultGeneration == currentGeneration && requestedIndex == index

    companion object {
        /**
         * 把下标夹取到列表范围内。
         *
         * @param value 期望下标。
         * @param count 列表长度。
         * @return 合法下标；列表为空时返回 0。
         */
        fun clampIndex(
            value: Int,
            count: Int,
        ): Int {
            if (count <= 0) return 0
            return value.coerceIn(0, count - 1)
        }

        /**
         * 生成页码文案。
         *
         * @param index 当前下标，从 0 开始。
         * @param count 列表长度。
         * @return 形如「3/12」的文案；列表为空时为「0/0」。
         */
        fun pageLabel(
            index: Int,
            count: Int,
        ): String {
            if (count <= 0) return "0/0"
            val safe = clampIndex(index, count) + 1
            return "$safe/$count"
        }
    }
}
