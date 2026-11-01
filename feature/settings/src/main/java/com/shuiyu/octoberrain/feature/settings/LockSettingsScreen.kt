package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.LockConfig
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkPage
import kotlinx.coroutines.launch

/**
 * 锁屏与安全设置页面。
 *
 * 行控件由 [SettingsCatalog.lockGroups] 的声明生成；
 * 启用开关与 PIN 设置流程保留在本页，因为需要在写入前检查是否已有 PIN。
 */
class LockSettingsScreen : SettingsBaseScreen("锁屏密码") {
    /** 主线程消息队列，用于把后台摘要计算结果交回界面。 */
    private val mainHandler = Handler(Looper.getMainLooper())

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
        SettingsUi.section(page, "手动锁定").action("立即锁定") {
            val context = contextOrNull() ?: return@action
            if (!SettingsUi.settings().lock.enabled || !hasPin()) {
                SettingsUi.toast(context, "请先设置密码并启用锁屏")
            } else {
                AppServices.lock.lock()
            }
        }
        SettingsSpecRenderer.renderPage(
            page,
            listOf(SettingsGroupSpec("project.lockWallpaper", "十雨锁屏壁纸")),
            config,
            settingEffectSink(),
            actions =
                mapOf(
                    "appearance.lockWallpaper.pick" to { pickLockWallpaper() },
                    "appearance.lockWallpaper.clear" to { clearLockWallpaper() },
                ),
        )
    }

    /** 选择锁屏壁纸：调用系统文件选择器，选中后写入锁屏壁纸配置。 */
    private fun pickLockWallpaper() {
        val context = contextOrNull() ?: return
        SettingsUi.pickFile(
            context,
            "选择锁屏壁纸",
            arrayOf("image/*"),
            setOf("jpg", "jpeg", "png", "webp", "bmp"),
            forImport = true,
        ) { uri ->
            applyLockWallpaper(uri)
        }
    }

    /** 异步应用锁屏壁纸，完成后刷新摘要并请求整屏重绘。 */
    private fun applyLockWallpaper(uri: Uri) {
        val context = contextOrNull() ?: return
        SettingsUi.toast(context, "正在设置锁屏壁纸…")
        scope.launch {
            val applied =
                runCatching {
                    AppServices.appearance.setLockWallpaper(uri)
                }.getOrDefault(false)
            if (applied) {
                SettingsUi.toast(context, "锁屏壁纸已更新")
                AppServices.refresh.requestFullRefresh()
                rebuildLater()
            } else {
                SettingsUi.toast(context, "锁屏壁纸读取失败，请换一张图片")
            }
        }
    }

    /** 清除锁屏壁纸，锁屏界面恢复纸色背景；未设置时直接提示。 */
    private fun clearLockWallpaper() {
        val context = contextOrNull() ?: return
        val appearance = SettingsUi.settings().appearance
        if (appearance.lockWallpaperPath.isBlank()) {
            SettingsUi.toast(context, "当前没有设置锁屏壁纸")
            return
        }
        InkDialogs.confirm(
            context,
            "清除锁屏壁纸",
            "锁屏密码界面将恢复为纸色背景。",
            confirmLabel = "清除",
            onConfirm = {
                runCatching { AppServices.appearance.clearLockWallpaper() }
                AppServices.refresh.requestFullRefresh()
                rebuildLater()
            },
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

    /** 打开 PIN 设置流程，两次输入一致后写入。 */
    private fun setPin() {
        val context = contextOrNull() ?: return
        promptPin(context, "设置四位 PIN") { first ->
            promptPin(context, "再次输入以确认") { second ->
                if (first != second) {
                    SettingsUi.toast(context, "两次输入不一致，请重新设置")
                } else {
                    savePin(first, context)
                }
            }
        }
    }

    /**
     * 在后台线程计算 PIN 摘要并写入，完成后回到主线程提示并刷新页面。
     *
     * 摘要使用 PBKDF2 迭代，放在主线程执行会阻塞输入派发并可能触发无响应。
     *
     * @param pin 四位数字 PIN
     * @param context 用于提示的上下文
     */
    private fun savePin(
        pin: String,
        context: Context,
    ) {
        Thread(
            {
                val saved =
                    runCatching {
                        val previous = SettingsUi.settings().lock.pinHash
                        AppServices.lock.setPin(pin)
                        val lock = SettingsUi.settings().lock
                        lock.enabled && !lock.pinHash.isNullOrBlank() && lock.pinHash != previous
                    }.getOrDefault(false)
                mainHandler.post {
                    if (saved) {
                        updateLock { it.copy(enabled = true) }
                        SettingsUi.toast(context, "PIN 已设置")
                        rebuildLater()
                    } else {
                        SettingsUi.toast(context, "PIN 保存失败，请重试")
                    }
                }
            },
            "lock-pin-set",
        ).start()
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
                val saved =
                    runCatching {
                        AppServices.lock.clearPin()
                        !AppServices.lock.hasPin() && !SettingsUi.settings().lock.enabled
                    }.getOrDefault(false)
                SettingsUi.toast(context, if (saved) "密码已清除，锁屏已关闭" else "清除失败，请重试")
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
            if (text.length != PIN_LENGTH || text.any { it !in '0'..'9' }) {
                SettingsUi.toast(context, "PIN 必须为四位数字")
            } else {
                onDone(text)
            }
        }
    }

    private companion object {
        /** PIN 固定长度。 */
        const val PIN_LENGTH = 4
    }
}

/** 写入锁屏配置分区。 */
private fun updateLock(transform: (LockConfig) -> LockConfig) {
    SettingsUi.edit { config -> config.copy(lock = transform(config.lock)) }
}

/** 是否已设置 PIN，读取失败按未设置处理。 */
private fun hasPin(): Boolean = runCatching { AppServices.lock.hasPin() }.getOrDefault(false)
