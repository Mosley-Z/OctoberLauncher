package com.shuiyu.octoberrain.feature.reader

/**
 * 阅读页重排调度规则。
 *
 * 视图尺寸变化只在会话就绪且没有进行中的打开、翻页或搜索动作时立即重排；
 * 动作进行中只登记待重排标记，由动作完成回调继续消费。这样动作自身的完成
 * 回调不会再次触发重排，连续尺寸变化也只按最新目标尺寸重开一次。
 */
internal object ReaderRelayout {
    /** 一次尺寸变化对应的处置。 */
    enum class Decision {
        /** 尺寸未变化、页面不可见或会话尚未就绪，无需动作。 */
        NONE,

        /** 有动作进行中，登记待重排标记，等动作结束后消费。 */
        QUEUE,

        /** 可以立即按最新目标尺寸重排。 */
        RELAYOUT,
    }

    /**
     * 计算一次尺寸变化的处置。
     *
     * @param visible 页面是否可见
     * @param sessionReady 是否已有可重排的会话
     * @param busy 是否有进行中的动作
     * @param targetWidth 最新目标宽度
     * @param targetHeight 最新目标高度
     * @param openedWidth 当前会话构造时的宽度
     * @param openedHeight 当前会话构造时的高度
     */
    fun onSizeChanged(
        visible: Boolean,
        sessionReady: Boolean,
        busy: Boolean,
        targetWidth: Int,
        targetHeight: Int,
        openedWidth: Int,
        openedHeight: Int,
    ): Decision {
        // 页面不可见、目标尺寸无效、尺寸与当前会话一致或会话未就绪时无需动作。
        val screenReady = visible && targetWidth > 0 && targetHeight > 0
        val sizeChanged = targetWidth != openedWidth || targetHeight != openedHeight
        return when {
            !screenReady || !sizeChanged || !sessionReady -> Decision.NONE
            busy -> Decision.QUEUE
            else -> Decision.RELAYOUT
        }
    }

    /**
     * 动作结束后的消费判断。
     *
     * @param visible 页面是否可见
     * @param sessionReady 动作结束后会话是否已建立
     * @param queued 动作进行中是否登记过尺寸变化
     */
    fun afterAction(
        visible: Boolean,
        sessionReady: Boolean,
        queued: Boolean,
    ): Boolean = visible && sessionReady && queued
}
