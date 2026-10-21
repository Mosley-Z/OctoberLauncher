package com.shuiyu.octoberrain.feature.wereadapp.ui

import android.content.Context
import android.net.Uri
import android.text.InputType
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.data.AppearanceRepositoryImpl
import com.shuiyu.octoberrain.core.data.LockWallpaperResult
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * 锁屏密码段。
 *
 * 直接使用启动器唯一的锁屏服务与壁纸仓库，与「锁屏与安全」共享状态。
 * 不读写实验室旧 lock_* 偏好，避免覆盖用户已经设置的宿主密码。
 *
 * @param context 创建视图的上下文
 */
@Suppress("TooManyFunctions")
internal class WereadLabLockPage(
    context: Context,
) : LabPageView(context) {
    private var scope: CoroutineScope? = null

    /** 本次操作是否刚更新过背景，仅用于状态行标记，不写入配置。 */
    private var backgroundUpdated = false

    /** 本次操作最近一次背景失败原因，为空表示没有失败记录。 */
    private var backgroundError: String? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        refresh()
    }

    override fun onDetachedFromWindow() {
        scope?.cancel()
        scope = null
        super.onDetachedFromWindow()
    }

    override fun render() {
        addIntro()
        addEnableRow()
        addPinSection()
        addBackgroundSection()
        addScopeTip()
    }

    /** 简介文案。 */
    private fun addIntro() {
        content.addView(labBody(context, "与十雨桌面的「锁屏与安全」共用同一把锁。启用后，按自动锁定设置在熄屏或启动时显示四位 PIN 解锁界面。"))
        content.addView(labSpacer(context, 0.5f))
        content.addView(labCaption(context, "忘记密码时可清除应用数据恢复，桌面与阅读数据会一并清除。"))
    }

    /** 启用锁屏复选行。 */
    private fun addEnableRow() {
        val enabled = AppServices.config.state.value.lock.enabled
        content.addView(labSpacer(context))
        content.addView(
            labCheckRow(context, "启用锁屏（默认关）", enabled) { checked ->
                setLockEnabled(checked)
            },
        )
        val autoLock = AppServices.config.state.value.lock.autoLock
        content.addView(
            labCheckRow(context, "熄屏后自动锁定", autoLock) { checked ->
                val saved =
                    runCatching {
                        AppServices.config.edit { current ->
                            current.copy(lock = current.lock.copy(autoLock = checked))
                        }
                        AppServices.config.state.value.lock.autoLock == checked
                    }.getOrDefault(false)
                if (!saved) labToast(context, "自动锁定设置保存失败")
                refresh()
            },
        )
        if (!autoLock) {
            content.addView(labCaption(context, "自动锁定已关闭：熄屏与重新启动不会自动上锁，可点击下方按钮手动锁定。"))
        }
        content.addView(InkWidgets.button(context, "立即锁定") { lockNow() })
    }

    /** 密码分组：输入、保存与状态行。 */
    private fun addPinSection() {
        val hasPin = AppServices.lock.hasPin()
        content.addView(labSpacer(context, 2f))
        content.addView(labSectionTitle(context, "锁屏密码"))
        val (row, field) =
            labFieldRow(
                context,
                "四位数字密码",
                "",
                "输入四位数字",
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            )
        content.addView(row)
        content.addView(labSpacer(context, 0.5f))
        content.addView(
            labButtonRow(
                context,
                InkWidgets.button(context, "保存密码") { savePin(field.text.toString()) },
                InkWidgets.button(context, "清除密码") { clearPin() },
            ),
        )
        content.addView(labSpacer(context, 0.5f))
        content.addView(InkWidgets.labelRow(context, "状态", if (hasPin) "已设置密码" else "未设置密码"))
    }

    /** 背景分组：选图、恢复默认与状态行。 */
    private fun addBackgroundSection() {
        val current = AppServices.config.state.value.appearance.lockWallpaperPath
        content.addView(labSpacer(context, 2f))
        content.addView(labSectionTitle(context, "锁屏背景"))
        content.addView(
            labButtonRow(
                context,
                InkWidgets.button(context, "选择图片") { pickBackground() },
                InkWidgets.button(context, "恢复默认") {
                    restoreBackground()
                },
            ),
        )
        content.addView(labSpacer(context, 0.5f))
        content.addView(
            InkWidgets.labelRow(context, "背景状态", backgroundStatusText(current)),
        )
        content.addView(labCaption(context, backgroundCaption()))
    }

    /**
     * 背景状态行文案：失败原因优先，其次是带已更新标记的文件名。
     *
     * @param current 配置中的背景文件路径，空串表示默认纸色
     * @return 状态行右侧文案
     */
    private fun backgroundStatusText(current: String): String {
        backgroundError?.let { reason -> return "设置失败：$reason" }
        val name = if (current.isEmpty()) "默认纸色" else File(current).name
        return if (backgroundUpdated) "$name（已更新）" else name
    }

    /** 背景说明行：刚更新时补充生效时机。 */
    private fun backgroundCaption(): String =
        if (backgroundUpdated) {
            "所选图片会复制到应用私有目录，仅在十雨桌面的锁屏界面显示。锁屏界面在下次亮屏时使用新背景。"
        } else {
            "所选图片会复制到应用私有目录，仅在十雨桌面的锁屏界面显示。"
        }

    /** 说明锁定的实际作用范围。 */
    private fun addScopeTip() {
        content.addView(labSpacer(context, 2f))
        content.addView(labCaption(context, "此锁只保护十雨桌面内的页面，不替代系统设备锁。自动锁定沿用「锁屏与安全」设置，无需悬浮窗权限。"))
    }

    /** 开启前必须已有宿主 PIN，关闭时保留密码和自动锁定设置。 */
    private fun setLockEnabled(enabled: Boolean) {
        if (enabled && !AppServices.lock.hasPin()) {
            labToast(context, "请先设置四位数字密码")
        } else {
            val saved =
                runCatching {
                    AppServices.config.edit { current -> current.copy(lock = current.lock.copy(enabled = enabled)) }
                    AppServices.config.state.value.lock.enabled == enabled
                }.getOrDefault(false)
            if (!saved) labToast(context, "锁屏设置保存失败")
        }
        refresh()
    }

    /** 不改变自动锁定选项，直接让宿主展示唯一 PIN 覆盖层。 */
    private fun lockNow() {
        val lock = AppServices.config.state.value.lock
        if (!lock.enabled || !AppServices.lock.hasPin()) {
            labToast(context, "请先设置密码并启用锁屏")
        } else {
            AppServices.lock.lock()
        }
    }

    /** 两次输入一致后交给宿主生成和保存密码摘要。 */
    private fun savePin(pin: String) {
        if (!PIN_PATTERN.matches(pin)) {
            labToast(context, "请输入四位数字密码")
            return
        }
        InkDialogs.input(context, "再次输入以确认", hint = "四位数字", numeric = true, password = true) { second ->
            if (second != pin) {
                labToast(context, "两次输入不一致，请重新设置")
            } else {
                saveConfirmedPin(pin)
            }
        }
    }

    /** 使用唯一锁屏服务，摘要计算在后台执行，完成后核对配置确实更新。 */
    private fun saveConfirmedPin(pin: String) {
        val observing = scope ?: return
        labToast(context, "正在保存密码…")
        observing.launch {
            val saved =
                withContext(Dispatchers.Default) {
                    runCatching {
                        val previous = AppServices.config.state.value.lock.pinHash
                        AppServices.lock.setPin(pin)
                        val lock = AppServices.config.state.value.lock
                        lock.enabled && !lock.pinHash.isNullOrBlank() && lock.pinHash != previous
                    }.getOrDefault(false)
                }
            labToast(context, if (saved) "密码已保存并启用锁屏" else "密码保存失败，请重试")
            refresh()
        }
    }

    /** 确认后清除宿主密码并关闭锁屏。 */
    private fun clearPin() {
        if (!AppServices.lock.hasPin()) {
            labToast(context, "尚未设置密码")
            return
        }
        InkDialogs.confirm(context, "清除密码", "清除后十雨桌面将不再要求 PIN 解锁。", confirmLabel = "清除", onConfirm = {
            val saved =
                runCatching {
                    AppServices.lock.clearPin()
                    !AppServices.lock.hasPin() && !AppServices.config.state.value.lock.enabled
                }.getOrDefault(false)
            labToast(context, if (saved) "密码已清除，锁屏已关闭" else "清除失败，请重试")
            refresh()
        })
    }

    /** 拉起系统文件选择器换取锁屏背景，仅允许图片。 */
    private fun pickBackground() {
        if (!AppServices.hasPicker()) {
            labToast(context, "文件选择器不可用")
            return
        }
        val opened =
            runCatching {
                AppServices.picker.pick(arrayOf("image/*")) { uri ->
                    if (uri != null) applyBackground(uri)
                }
            }.getOrDefault(false)
        if (!opened) labToast(context, "文件选择器不可用")
    }

    /** 使用壁纸仓库复制所选图片，成功与失败都写入状态行，并保留提示框。 */
    private fun applyBackground(uri: Uri) {
        val observing = scope ?: return
        observing.launch {
            val result = saveBackground(uri) ?: return@launch
            if (result == LockWallpaperResult.APPLIED) {
                backgroundUpdated = true
                backgroundError = null
                labToast(context, "锁屏背景已更新")
                AppServices.refresh.requestFullRefresh()
            } else {
                backgroundUpdated = false
                backgroundError = failureReason(result)
                labToast(context, "锁屏背景设置失败：${failureReason(result)}")
            }
            refresh()
        }
    }

    /**
     * 调用外观服务保存锁屏背景。
     *
     * 仓库为 [AppearanceRepositoryImpl] 时取用其结果方法，否则按布尔结果折算；
     * 协程取消返回 null，由调用方静默结束，页面销毁后不再弹出提示。
     */
    private suspend fun saveBackground(uri: Uri): LockWallpaperResult? =
        runCatching {
            val repository = AppServices.appearance
            val concrete = repository as? AppearanceRepositoryImpl
            concrete?.applyLockWallpaper(uri)
                ?: if (repository.setLockWallpaper(uri)) {
                    LockWallpaperResult.APPLIED
                } else {
                    LockWallpaperResult.UNKNOWN
                }
        }.getOrElse { error ->
            if (error is CancellationException) null else LockWallpaperResult.UNKNOWN
        }

    /** 把保存结果翻译为提示文案中的失败原因。 */
    private fun failureReason(result: LockWallpaperResult): String =
        when (result) {
            LockWallpaperResult.READ_FAILED -> "无法读取所选文件"
            LockWallpaperResult.NOT_IMAGE -> "所选文件不是可识别的图片"
            LockWallpaperResult.CONFIG_FAILED -> "配置写入未确认"
            else -> "原因未知"
        }

    /** 恢复宿主默认背景，保留桌面壁纸及锁屏量化设置。 */
    private fun restoreBackground() {
        val saved =
            runCatching {
                AppServices.appearance.clearLockWallpaper()
                AppServices.config.state.value.appearance.lockWallpaperPath
                    .isBlank()
            }.getOrDefault(false)
        labToast(context, if (saved) "已恢复默认背景" else "背景恢复失败，请重试")
        if (saved) {
            backgroundUpdated = false
            backgroundError = null
            AppServices.refresh.requestFullRefresh()
        }
        refresh()
    }

    private companion object {
        /** 宿主四位数字 PIN 的输入规则。 */
        val PIN_PATTERN: Regex = Regex("^[0-9]{4}$")
    }
}
