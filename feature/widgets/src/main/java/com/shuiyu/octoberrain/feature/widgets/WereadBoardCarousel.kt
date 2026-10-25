package com.shuiyu.octoberrain.feature.widgets

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 叠放封面轮播的运行状态。
 *
 * 组件只把候选书目总数、选中位置与手指拖动产生的连续偏移交给本类，由本类给出各层
 * 封面的缩放与横向位置。类内不含位图、不访问数据源、不持有视图，便于单独测试。
 */
internal class WereadBoardCarousel {
    /** 是否首尾相连；普通本书与沉浸组件均启用循环。 */
    var looping: Boolean = false

    /** 当前候选书目总数。 */
    var count: Int = 0
        private set

    /** 当前选中书目在列表中的位置。 */
    var selectedIndex: Int = 0
        private set

    /** 相对选中位置的连续偏移，正值表示内容已向下一本移动。 */
    var drag: Float = 0f
        private set

    /** 连续位置，等于选中位置与拖动偏移之和。 */
    val position: Float
        get() = selectedIndex + drag

    /**
     * 复位书目总数与选中位置。
     *
     * 书目总数变化后选中位置越界时按边界截断，保证列表缩短时仍有有效选中项。
     *
     * @param total 候选书目总数
     * @param index 期望的选中位置
     */
    fun reset(
        total: Int,
        index: Int,
    ) {
        count = total.coerceAtLeast(0)
        selectedIndex = if (count == 0) 0 else index.coerceIn(0, count - 1)
        drag = 0f
    }

    /**
     * 写入连续位置对应的拖动偏移，限制在列表范围之内。
     *
     * @param value 连续位置，允许带小数
     */
    fun setPosition(value: Float) {
        drag = if (count <= 1) 0f else (value - selectedIndex).coerceIn(minDrag(), maxDrag())
    }

    /**
     * 在现有拖动偏移上叠加增量。
     *
     * @param steps 增量，单位为「一本」，取连续值
     */
    fun dragBy(steps: Float) {
        if (count <= 1) return
        drag = (drag + steps).coerceIn(minDrag(), maxDrag())
    }

    /**
     * 清空拖动偏移，回到选中位置。
     */
    fun clearDrag() {
        drag = 0f
    }

    /**
     * 结束拖动并落到目标位置。
     *
     * @param target 目标位置，越界时截断到有效范围
     * @return 选中位置是否发生变化
     */
    fun commit(target: Int): Boolean {
        if (count == 0) {
            drag = 0f
            return false
        }
        val next = if (looping) Math.floorMod(target, count) else target.coerceIn(0, count - 1)
        val changed = next != selectedIndex
        selectedIndex = next
        drag = 0f
        return changed
    }

    /**
     * 按当前连续位置取最近的整数位置，用于松手后判定落点。
     *
     * @return 有效范围内的位置
     */
    fun nearestIndex(): Int =
        when {
            count == 0 -> 0
            looping -> Math.floorMod(position.roundToInt(), count)
            else -> position.roundToInt().coerceIn(0, count - 1)
        }

    /**
     * 给出需要绘制的封面层，按由远到近排序。
     *
     * 中央一层始终参与绘制；两侧各最多两层，列表边界之外不复制书目凑数。
     *
     * @return 封面层列表，最多五层
     */
    fun slots(): List<WereadCoverSlot> {
        if (count == 0 || looping) return if (count == 0) emptyList() else loopingSlots()
        val slots = ArrayList<WereadCoverSlot>(MAX_RING * 2 + 1)
        for (ring in MAX_RING downTo 1) {
            for (side in SIDES) {
                val index = selectedIndex + side * ring
                if (index !in 0 until count) continue
                slots += WereadCoverSlot(index, index - position)
            }
        }
        slots += WereadCoverSlot(selectedIndex, selectedIndex - position)
        return slots
    }

    private fun loopingSlots(): List<WereadCoverSlot> {
        val offsets =
            when (count) {
                1 -> listOf(0)
                2 -> listOf(-1, 0)
                3 -> listOf(-1, 1, 0)
                4 -> listOf(-2, -1, 1, 0)
                else -> listOf(-2, 2, -1, 1, 0)
            }
        return offsets.map { offset ->
            WereadCoverSlot(Math.floorMod(selectedIndex + offset, count), offset.toFloat())
        }
    }

    /** 左边界对应的最小偏移。 */
    private fun minDrag(): Float = if (looping) -1f else -selectedIndex.toFloat()

    /** 右边界对应的最大偏移。 */
    private fun maxDrag(): Float = if (looping) 1f else (count - 1 - selectedIndex).toFloat()

    companion object {
        /** 可见层数上限，中央之外每侧两层。 */
        const val MAX_RING: Int = 2

        /** 中央封面缩放，以中央封面宽度为基准。 */
        const val CENTER_SCALE: Float = 1f

        /** 相邻一层封面缩放。 */
        const val SIDE_SCALE: Float = 0.88f

        /** 相邻两层封面缩放。 */
        const val FAR_SCALE: Float = 0.80f

        /** 相邻一层封面中心距，单位为中央封面宽度。 */
        const val SIDE_STEP: Float = 0.40f

        /** 相邻两层封面中心距，单位为中央封面宽度。 */
        const val FAR_OFFSET: Float = 0.32f

        /** 二层之外的附加中心距，保证拖动过程中远端封面继续移出画面。 */
        const val OFFSCREEN_STEP: Float = 0.55f

        /** 两侧遍历方向，负值在左、正值在右。 */
        private val SIDES: IntArray = intArrayOf(-1, 1)

        /**
         * 按连续位置给出封面缩放。
         *
         * 中央为一倍，相邻一层为 0.88，相邻两层为 0.80，层间线性过渡，
         * 拖动过程中缩放随位置连续变化。
         *
         * @param position 相对中央的连续位置
         * @return 缩放倍数
         */
        fun scaleAt(position: Float): Float {
            val ring = abs(position).coerceAtMost(MAX_RING.toFloat())
            return if (ring <= 1f) {
                CENTER_SCALE + (SIDE_SCALE - CENTER_SCALE) * ring
            } else {
                SIDE_SCALE + (FAR_SCALE - SIDE_SCALE) * (ring - 1f)
            }
        }

        /**
         * 按连续位置给出封面中心距，单位为中央封面宽度。
         *
         * 中央到一层按 0.40 递增，一层到二层回收到 0.32，使二层封面仅露出窄边；
         * 超过二层的封面继续向外移动，保证拖动过程中正常移出画面。
         *
         * @param position 相对中央的连续位置
         * @return 中心距
         */
        fun offsetAt(position: Float): Float {
            val ring = abs(position)
            return when {
                ring <= 1f -> SIDE_STEP * ring
                ring <= MAX_RING -> SIDE_STEP + (FAR_OFFSET - SIDE_STEP) * (ring - 1f)
                else -> FAR_OFFSET + (ring - MAX_RING) * OFFSCREEN_STEP
            }
        }
    }
}

/**
 * 单个封面层的位置信息。
 *
 * @property index 候选书目中的位置
 * @property position 相对中央的连续位置，负值在左、正值在右
 */
internal data class WereadCoverSlot(
    val index: Int,
    val position: Float,
) {
    /** 是否处于中央焦点位置。 */
    val centered: Boolean
        get() = abs(position) < CENTER_TOLERANCE

    companion object {
        /** 判定中央位置的容差，拖动过程中以半本为界。 */
        const val CENTER_TOLERANCE: Float = 0.5f
    }
}
