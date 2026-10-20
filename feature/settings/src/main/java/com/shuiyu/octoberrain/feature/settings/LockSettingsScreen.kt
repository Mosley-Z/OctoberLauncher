package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.LockConfig
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkPage

/**
 * 锁屏与安全设置页面。
 *
 * 行控件由 [SettingsCatalog.lockGroups] 的声明生成；
 * 启用开关与 PIN 设置流程保留在本页，因为需要在写入前检查是否已有 PIN。
 */
class LockSettingsScreen : SettingsBaseScreen("锁屏与安全") {
    override fun build(
        page: InkPage,
        config: LauncherConfig,
    ) {
        SettingsSpecRenderer.renderPage(
            page = page,
            groups = SettingsCatalog.lockGroups,
            config = config,
            sink = settingEffectSink(),
            actions = lockActions(),
            customs = lockCustoms(),
        )
    }

    /** 锁屏页的动作表，键为设置项标识。 */
    private fun lockActions(): Map<String, () -> Unit> =
        mapOf(
            "lock.pinEdit" to { setPin() },
            "lock.pinClear" to { clearPin() },
        )

    /** 启用开关需要在开启前检查 PIN，因此保留为自定义行。 */
    private fun lockCustoms(): Map<String, (SettingsSection) -> Unit> =
        mapOf(
            "lock.enabled" to { section ->
                section.toggle(
                    label = "启用锁屏",
                    get = { SettingsUi.settings().lock.enabled },
                    hint = "关闭后启动器不再显示 PIN 解锁界面",
                ) { value ->
                    if (value && !hasPin()) {
                        val context = contextOrNull()
                        if (context != null) SettingsUi.toast(context, "请先设置四位 PIN")
                        setPin()
                    } else {
                        updateLock { it.copy(enabled = value) }
                    }
                }
            },
        )

    /** 是否已设置 PIN，读取失败按未设置处理。 */
    private fun hasPin(): Boolean = runCatching { AppServices.lock.hasPin() }.getOrDefault(false)

    /** 打开 PIN 设置流程，两次输入一致后写入。 */
    private fun setPin() {
        val context = contextOrNull() ?: return
        promptPin(context, "设置四位 PIN") { first ->
            promptPin(context, "再次输入以确认") { second ->
                if (first != second) {
                    SettingsUi.toast(context, "两次输入不一致，请重新设置")
                } else {
                    val saved = runCatching { AppServices.lock.setPin(first) }.isSuccess
                    if (saved) {
                        updateLock { it.copy(enabled = true) }
                        SettingsUi.toast(context, "PIN 已设置")
                        rebuildLater()
                    } else {
                        SettingsUi.toast(context, "PIN 保存失败，请重试")
                    }
                }
            }
        }
    }

    /** 确认后清除 PIN 并关闭锁屏。 */
    private fun clearPin() {
        val context = contextOrNull() ?: return
        if (!hasPin()) {
            SettingsUi.toast(context, "尚未设置 PIN")
            return
        }
        InkDialogs.confirm(
            context = context,
            title = "清除 PIN",
            message = "清除后启动器将不再要求解锁，已保存的摘要会被删除。",
            confirmLabel = "清除",
            onConfirm = {
                runCatching { AppServices.lock.clearPin() }
                updateLock { it.copy(enabled = false) }
                rebuildLater()
            },
        )
    }

    /**
     * 弹出四位数字输入框。
     *
     * 输入长度不足或包含非数字时不回调，只提示要求。
     */
    private fun promptPin(
        context: Context,
        title: String,
        onDone: (String) -> Unit,
    ) {
        InkDialogs.input(
            context = context,
            title = title,
            hint = "四位数字",
            numeric = true,
            password = true,
        ) { text ->
            val digits = text.filter { it.isDigit() }.take(PIN_LENGTH)
            if (digits.length != PIN_LENGTH) {
                SettingsUi.toast(context, "PIN 必须为四位数字")
            } else {
                onDone(digits)
            }
        }
    }

    /** 写入锁屏配置分区。 */
    private fun updateLock(transform: (LockConfig) -> LockConfig) {
        SettingsUi.edit { config -> config.copy(lock = transform(config.lock)) }
    }

    private companion object {
        /** PIN 固定长度。 */
        const val PIN_LENGTH = 4
    }
}
