package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.design.InkPage

/**
 * 设置项渲染器。
 *
 * 按声明表生成分组与行控件：开关、选项、步进、滑条、信息、动作、按钮与自定义行。
 * 写入统一经 SettingsUi.edit 提交，再按声明的生效方式回调页面。
 * 页面只需提供动作处理表与自定义行构建表，不再手写行控件。
 */
internal object SettingsSpecRenderer {
    /**
     * 渲染一组配置项组成的页面。
     *
     * 参数 page 为承载页；groups 为按显示顺序排列的分组；
     * config 为本次构建使用的配置快照；sink 接收写入后的生效方式；
     * actions 以设置项标识为键提供点击行为；customs 以自定义行键提供构建函数。
     */
    fun renderPage(
        page: InkPage,
        groups: List<SettingsGroupSpec>,
        config: LauncherConfig,
        sink: SettingEffectSink,
        actions: Map<String, () -> Unit> = emptyMap(),
        customs: Map<String, (SettingsSection) -> Unit> = emptyMap(),
    ) {
        groups.forEach { group -> renderGroup(page, group, config, sink, actions, customs) }
    }

    /**
     * 渲染单个分组，返回承载该组的分组对象，供页面追加行外内容。
     *
     * 参数 group 为分组声明；其余参数含义同 [renderPage]。
     * 组内没有可见设置项时返回 null 且不生成空分组。
     */
    fun renderGroup(
        page: InkPage,
        group: SettingsGroupSpec,
        config: LauncherConfig,
        sink: SettingEffectSink,
        actions: Map<String, () -> Unit> = emptyMap(),
        customs: Map<String, (SettingsSection) -> Unit> = emptyMap(),
    ): SettingsSection? {
        val specs = SettingsCatalog.specsOf(group.id).filter { it.visible(config) }
        if (specs.isEmpty()) return null
        val section = SettingsUi.section(page, group.title, group.subtitle)
        val context = page.context
        specs.forEach { spec -> renderRow(context, section, spec, sink, actions, customs) }
        group.notes.forEach { note -> SettingsUi.note(page, note) }
        return section
    }

    /** 按控件类型生成一行，并绑定读写行为。 */
    private fun renderRow(
        context: Context,
        section: SettingsSection,
        spec: SettingSpec,
        sink: SettingEffectSink,
        actions: Map<String, () -> Unit>,
        customs: Map<String, (SettingsSection) -> Unit>,
    ) {
        when (val control = spec.control) {
            is SettingControl.Toggle ->
                section.toggle(
                    label = spec.title,
                    get = { control.read(SettingsUi.settings()) },
                    hint = spec.hint,
                ) { value ->
                    applyValue(spec, sink) { config -> control.write(config, value) }
                }

            is SettingControl.Choice ->
                section.choose(
                    label = spec.title,
                    options = control.options.map { it.label },
                    selected = { control.read(SettingsUi.settings()) },
                    hint = spec.hint,
                ) { index ->
                    applyValue(spec, sink) { config -> control.write(config, index) }
                }

            is SettingControl.Stepper ->
                section.stepper(
                    label = spec.title,
                    values = control.options.map { it.label },
                    selected = { control.read(SettingsUi.settings()) },
                    hint = spec.hint,
                ) { index ->
                    applyValue(spec, sink) { config -> control.write(config, index) }
                }

            is SettingControl.Slider ->
                section.slider(
                    label = spec.title,
                    values = control.values,
                    initial = control.read(SettingsUi.settings()),
                    format = control.format,
                    hint = spec.hint,
                ) { value ->
                    applyValue(spec, sink) { config -> control.write(config, value) }
                }

            is SettingControl.Info -> renderInfoRow(context, section, spec, control)

            is SettingControl.Action -> renderActionRow(context, section, spec, control, actions)

            is SettingControl.Button ->
                section.button(
                    label = spec.title,
                    filled = control.filled,
                ) {
                    val handler = actions[spec.id]
                    if (handler != null) {
                        handler()
                    } else {
                        applyTransform(spec, sink) { config -> spec.write?.invoke(config) ?: config }
                    }
                }

            is SettingControl.Custom -> customs[control.key]?.invoke(section)
        }
    }

    /** 生成只读信息行，上下文取值优先于纯配置取值。 */
    private fun renderInfoRow(
        context: Context,
        section: SettingsSection,
        spec: SettingSpec,
        control: SettingControl.Info,
    ) {
        val current = SettingsUi.settings()
        section.info(
            label = spec.title,
            value = control.readWithContext?.invoke(context, current) ?: control.read(current),
        )
    }

    /** 生成跳转动作行，上下文取值优先于纯配置取值，点击行为由页面提供。 */
    private fun renderActionRow(
        context: Context,
        section: SettingsSection,
        spec: SettingSpec,
        control: SettingControl.Action,
        actions: Map<String, () -> Unit>,
    ) {
        val current = SettingsUi.settings()
        val value = control.valueWithContext?.invoke(context, current) ?: control.value?.invoke(current)
        section.action(label = spec.title, value = value, hint = spec.hint) {
            actions[spec.id]?.invoke()
        }
    }

    /** 按控件写入方式提交一次修改，随后按声明生效。 */
    private fun applyValue(
        spec: SettingSpec,
        sink: SettingEffectSink,
        transform: (LauncherConfig) -> LauncherConfig,
    ) {
        applyTransform(spec, sink, transform)
    }

    /** 提交整体写入，读取写入后的配置执行附加动作，再通知页面生效方式。 */
    private fun applyTransform(
        spec: SettingSpec,
        sink: SettingEffectSink,
        transform: (LauncherConfig) -> LauncherConfig,
    ) {
        SettingsUi.edit(transform)
        val updated = SettingsUi.settings()
        runCatching { spec.afterWrite(updated) }
        sink.applied(spec.effect)
    }
}

/** 设置写入后的生效方式接收者，由页面实现。 */
internal fun interface SettingEffectSink {
    /** 参数 effect 为声明表给出的生效方式。 */
    fun applied(effect: SettingEffect)
}
