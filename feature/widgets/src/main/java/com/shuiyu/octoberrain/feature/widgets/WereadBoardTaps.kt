package com.shuiyu.octoberrain.feature.widgets

/**
 * 卡片的点击分发。
 *
 * 底部按钮优先，其次是中央封面的打开入口，最后按封面层与内核命中区域判定。
 * 点击不会改变轮播选中状态，换书统一由落位动画提交，避免索引与画面不一致。
 *
 * @param host 状态与动作的提供方
 */
internal class WereadBoardTaps(
    private val host: WereadBoardGestureHost,
) {
    /**
     * 处理点击。
     *
     * @param x 组件局部横坐标
     * @param y 组件局部纵坐标
     * @return 是否消费
     */
    fun click(
        x: Float,
        y: Float,
    ): Boolean =
        if (host.form == WereadBoardForm.BOOK) {
            bookTap(x, y)
        } else {
            tappedRegion(x, y)
        }

    /**
     * 本书形态点击：底部按钮、打开入口、封面层依次判定。
     *
     * @param x 组件局部横坐标
     * @param y 组件局部纵坐标
     * @return 是否消费
     */
    private fun bookTap(
        x: Float,
        y: Float,
    ): Boolean {
        val current = host.chrome
        val slot = coverSlotAt(x, y)
        return when {
            current?.layoutButton?.contains(x, y) == true -> {
                host.switchForm()
                true
            }

            current?.refreshButton?.contains(x, y) == true -> host.refresh()
            current?.openButton?.contains(x, y) == true -> {
                host.open()
                true
            }

            slot == null -> false
            else -> {
                if (slot.centered) host.open() else host.settle(slot.index)
                true
            }
        }
    }

    /**
     * 判定底部按钮与内核命中区域。
     *
     * @param x 组件局部横坐标
     * @param y 组件局部纵坐标
     * @return 是否消费
     */
    private fun tappedRegion(
        x: Float,
        y: Float,
    ): Boolean {
        val current = host.chrome
        return when {
            current != null && current.layoutButton.contains(x, y) -> {
                host.switchForm()
                true
            }

            current != null && current.refreshButton.contains(x, y) -> host.refresh()
            else -> host.hitRegion(x, y)?.action?.let(host::dispatch) ?: false
        }
    }

    /**
     * 查找命中的封面层，按由近及远的顺序判定。
     *
     * @param x 组件局部横坐标
     * @param y 组件局部纵坐标
     * @return 命中的封面层，未命中时为空
     */
    private fun coverSlotAt(
        x: Float,
        y: Float,
    ): WereadCoverSlot? {
        val layout = host.frame ?: return null
        val slots = host.carousel?.slots().orEmpty()
        return slots.asReversed().firstOrNull { coverBoundsOf(layout, it).contains(x, y) }
    }
}
