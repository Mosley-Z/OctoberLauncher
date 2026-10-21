package com.shuiyu.octoberrain.feature.widgets

/**
 * 整体卡片的五种形态标识。
 *
 * 枚举序号写入桌面配置 `desktop.negativePage.forms`，读取时按 [of] 还原形态；
 * 形态池由 `desktop.wereadBoardPoolMask` 的低五位决定，[pool] 与 [nextIn] 提供
 * 轮换所需的顺序。本书形态采用杂志版式自绘，其余形态交给内核整卡渲染。
 */
internal enum class WereadBoardForm(
    /** 持久化标识，与配置中保存的序号对应。 */
    val id: String,
    /** 旧版选择器展示名。 */
    val label: String,
) {
    /** 本周统计形态。 */
    WEEK("week", "本周"),

    /** 本月统计形态。 */
    MONTH("month", "本月"),

    /** 本书详情形态。 */
    BOOK("book", "本书"),

    /** 本记形态。 */
    NOTE("note", "本记"),

    /** 待办形态。 */
    TODO("todo", "待办"),
    ;

    /**
     * 取下一个形态，末尾回到首个。
     *
     * @return 下一个形态
     */
    fun next(): WereadBoardForm = entries[(ordinal + 1) % entries.size]

    /**
     * 在给定启用池内取下一个形态。
     *
     * 池中没有当前形态时从池首重新开始，保证循环始终落在启用项上。
     *
     * @param pool 启用形态列表
     * @return 下一个启用的形态
     */
    fun nextIn(pool: List<WereadBoardForm>): WereadBoardForm =
        when {
            pool.isEmpty() -> next()
            pool.indexOf(this) < 0 -> pool.first()
            else -> pool[(pool.indexOf(this) + 1) % pool.size]
        }

    companion object {
        /**
         * 按持久化序号还原形态，越界与缺失回落到本周。
         *
         * @param ordinal 持久化序号
         * @return 对应形态
         */
        fun of(ordinal: Int?): WereadBoardForm = entries.getOrElse(ordinal ?: 0) { WEEK }

        /**
         * 按启用位掩码取出启用形态列表，空池与越界位回落到全部形态。
         *
         * @param mask 低五位对应的启用掩码
         * @return 启用形态列表
         */
        fun pool(mask: Int): List<WereadBoardForm> =
            entries.filter { (mask and (1 shl it.ordinal)) != 0 }.ifEmpty { entries.toList() }
    }
}
