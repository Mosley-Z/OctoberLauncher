package com.shuiyu.octoberrain.core.device

/**
 * 显示类型，决定绘制与刷新策略。
 *
 * 上游以屏幕分辨率做准入，这里改为按显示特性分支，任意分辨率都能进入桌面。
 */
enum class DisplayClass {
    /** 墨水屏，支持局部刷新的机型，交互反馈走局部重绘。 */
    EinkFast,

    /** 墨水屏，仅有整屏刷新的机型，交互反馈靠整页闪烁。 */
    EinkFull,

    /** 液晶屏，允许动画、阴影与模糊。 */
    Lcd,

    ;

    /** 是否为墨水屏。 */
    val isEink: Boolean get() = this != Lcd
}
