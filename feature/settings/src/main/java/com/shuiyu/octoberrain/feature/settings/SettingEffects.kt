package com.shuiyu.octoberrain.feature.settings

/**
 * 设置写入后的生效动作接收者。
 *
 * 把声明表给出的生效方式转换为具体刷新行为，避免页面直接判断枚举；
 * 各动作由页面按当前环境提供，纯逻辑部分可脱离 Android 环境用用例覆盖。
 */
internal interface SettingEffectHooks {
    /** 请求局部刷新。 */
    fun partialRefresh()

    /** 请求整屏刷新。 */
    fun fullRefresh()

    /** 重建设计令牌并整屏刷新。 */
    fun applyAppearance()

    /** 在下一帧重建页面内容。 */
    fun rebuildPage()

    /** 刷新内容数据缓存并通知渲染层。 */
    fun refreshContent()
}

/**
 * 生效方式分发。
 *
 * 每种生效方式对应的动作顺序固定：外观与字号类先重建设计令牌，再视需要重建页面；
 * 行结构变化先局部刷新再重建页面；内容类刷新调用内容缓存刷新。
 */
internal object SettingEffectDispatcher {
    /**
     * 分发一次生效方式。
     *
     * 参数 effect 为设置项声明给出的生效方式；hooks 为页面提供的具体动作。
     * 未声明的生效方式不做任何动作，交由页面动作自行处理。
     */
    fun dispatch(
        effect: SettingEffect,
        hooks: SettingEffectHooks,
    ) {
        when (effect) {
            SettingEffect.NONE -> Unit
            SettingEffect.PARTIAL -> hooks.partialRefresh()
            SettingEffect.FULL -> hooks.fullRefresh()
            SettingEffect.APPEARANCE -> hooks.applyAppearance()
            SettingEffect.APPEARANCE_REBUILD -> {
                hooks.applyAppearance()
                hooks.rebuildPage()
            }

            SettingEffect.REBUILD -> {
                hooks.partialRefresh()
                hooks.rebuildPage()
            }

            SettingEffect.CONTENT_REFRESH -> {
                hooks.refreshContent()
                hooks.partialRefresh()
            }
        }
    }
}
