// 配色令牌沿用常量式全大写命名，跨模块以 InkColors.PAPER 一类形式引用；
// 重命名为小驼峰会改动公共符号与全部调用点，超出格式整理范围，故按文件抑制该规则。
@file:Suppress("ktlint:standard:property-naming")

package com.shuiyu.octoberrain.core.design

import android.graphics.Color

/**
 * 墨水屏配色令牌，对应 docs/03 的四档灰。
 *
 * 界面只引用这里的属性，避免在绘制代码里出现十六进制字面量。
 * 属性由 [apply] 按主题整体写入，默认值对应墨主题的四档灰；
 * 修改配色后调用方负责重绘界面，本对象不持有任何视图引用。
 */
object InkColors {
    /** 纸白，用于背景与反白文字。 */
    @Volatile
    var PAPER: Int = Color.WHITE
        private set

    /** 正文黑，用于主标题与主图标。 */
    @Volatile
    var INK: Int = Color.BLACK
        private set

    /** 次级黑，用于副标题与说明文字。 */
    @Volatile
    var INK_2: Int = Color.rgb(0x3C, 0x3C, 0x3C)
        private set

    /** 中间灰，仅用于装饰线与禁用态，不承载小字。 */
    @Volatile
    var INK_3: Int = Color.rgb(0xA8, 0xA8, 0xA8)
        private set

    /** 浅灰，用于选中底色与分隔线。 */
    @Volatile
    var INK_4: Int = Color.rgb(0xD8, 0xD8, 0xD8)
        private set

    /** 反白块底色，用于强调按钮与选中项。 */
    @Volatile
    var INVERT_PAPER: Int = Color.BLACK
        private set

    /** 反白块文字色。 */
    @Volatile
    var INVERT_INK: Int = Color.WHITE
        private set

    /** 强调线色，用于选中描边与关键分隔。 */
    @Volatile
    var ACCENT_LINE: Int = Color.BLACK
        private set

    /**
     * 应用一组主题配色。
     *
     * 参数 palette 为配色令牌，需保证非空；写入后界面在下一次重绘时生效。
     */
    fun apply(palette: ThemePalette) {
        PAPER = palette.paper
        INK = palette.ink
        INK_2 = palette.inkSoft
        INK_3 = palette.inkFaint
        INK_4 = palette.inkGhost
        INVERT_PAPER = palette.invertPaper
        INVERT_INK = palette.invertInk
        ACCENT_LINE = palette.accentLine
    }

    /** 恢复默认墨主题配色，用于令牌解析失败时的兜底。 */
    fun reset() {
        apply(ThemePalette.DEFAULT)
    }
}
