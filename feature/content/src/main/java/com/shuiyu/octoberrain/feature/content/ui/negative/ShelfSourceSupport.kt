package com.shuiyu.octoberrain.feature.content.ui.negative

import com.shuiyu.octoberrain.core.common.NegativePageConfig

/**
 * 负一屏书架书源的取值、归一与文案。
 *
 * 取值直接引用公共契约中的书源常量，归一规则与配置 effectiveShelfSource 保持一致。
 */
internal object ShelfSourceSupport {
    /** 微信读书书源。 */
    const val WEREAD = NegativePageConfig.SHELF_SOURCE_WEREAD

    /** 本地目录书源。 */
    const val LOCAL = NegativePageConfig.SHELF_SOURCE_LOCAL

    /**
     * 把配置值归一为受支持的取值。
     *
     * 空串、未知值与大小写差异都回退到 [WEREAD]，保证读取侧不会因历史配置或手工修改
     * 而进入无匹配分支。
     *
     * @param raw 配置中保存的原始取值
     */
    fun normalize(raw: String): String = if (raw == LOCAL) LOCAL else WEREAD

    /**
     * 读取负一屏配置中的书源。
     *
     * @param page 负一屏配置
     */
    fun of(page: NegativePageConfig): String = normalize(page.shelfSource)

    /**
     * 行内展示的书源名称。
     *
     * @param source 书源取值，可为归一前的原始值
     */
    fun label(source: String): String = if (normalize(source) == LOCAL) LABEL_LOCAL else LABEL_WEREAD

    /** 书源展示名称：本地。 */
    private const val LABEL_LOCAL = "本地书源"

    /** 书源展示名称：微信读书。 */
    private const val LABEL_WEREAD = "微信读书"
}
