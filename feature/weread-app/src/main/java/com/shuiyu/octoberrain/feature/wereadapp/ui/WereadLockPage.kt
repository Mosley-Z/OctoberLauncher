package com.shuiyu.octoberrain.feature.wereadapp.ui

import android.content.Context
import android.net.Uri
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.LockConfig
import com.shuiyu.octoberrain.core.data.AppearanceRepositoryImpl
import com.shuiyu.octoberrain.core.data.LockWallpaperResult
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * 锁屏密码页。
 *
 * 直接使用启动器唯一的锁屏服务与壁纸仓库，与十雨桌面「锁屏与安全」共享同一份配置。
 * 开关与状态行直接读取 AppServices.config.state 的 lock 节，配置变化时自动重绘。
 * 不读写旧 lock_* 偏好，避免覆盖用户已经设置的宿主密码。
 *
 * @param context 创建视图的上下文
 */
@Suppress("TooManyFunctions")
internal class WereadLockPage(
    context: Context,
) : LockPageView(context) {
    private var scope: CoroutineScope? = null

    /** 最近一次渲染使用的锁屏配置，用于识别外部改动并触发重绘。 */
    private var observedLock: LockConfig? = null

    /** 本次操作是否刚更新过背景，仅用于状态行标记，不写入配置。 */
    private var backgroundUpdated = false

    /** 本次操作最近一次背景失败原因，为空表示没有失败记录。 */
    private var backgroundError: String? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val observing = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = observing
        observedLock = AppServices.config.state.value.lock
        refresh()
        observing.launch {
            AppServices.config.state.collect { config ->
                if (config.lock != observedLock) {
                    observedLock = config.lock
                    refresh()
                }
            }
        }
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
        content.addView(
            lockBody(
                context,
                "本页设置的是本机四位 PIN，与十雨桌面「设置-桌面偏好-锁屏与安全」共用同一份配置。" +
                    "启用后，按自动锁定设置在熄屏或启动时显示四位 PIN 解锁界面。",
            ),
        )
        content.addView(lockSpacer(context, 0.5f))
        content.addView(lockCaption(context, "忘记密码时，在亮屏的锁屏界面长按删除键十秒即可清除 PIN，无需清除应用数据。"))
    }

    /** 启用锁屏复选行。 */
    private fun addEnableRow() {
        val enabled = AppServices.config.state.value.lock.enabled
        content.addView(lockSpacer(context))
        content.addView(
            lockCheckRow(context, "启用锁屏（默认关）", enabled) { checked ->
                setLockEnabled(checked)
            },
        )
        val autoLock = AppServices.config.state.value.lock.autoLock
        content.addView(
            lockCheckRow(context, "熄屏后自动锁定", autoLock) { checked ->
                val saved =
                    runCatching {
                        AppServices.config.edit { current ->
                            current.copy(lock = current.lock.copy(autoLock = checked))
                        }
                        AppServices.config.state.value.lock.autoLock == checked
                    }.getOrDefault(false)
                if (!saved) lockToast(context, "自动锁定设置保存失败")
                refresh()
            },
        )
        if (!autoLock) {
            content.addView(lockCaption(context, "自动锁定已关闭：熄屏与重新启动不会自动上锁，可点击下方按钮手动锁定。"))
        }
        content.addView(InkWidgets.button(context, "立即锁定") { lockNow() })
    }

    /** 密码分组：输入、保存与状态行。 */
    private fun addPinSection() {
        val hasPin = hasStoredPin()
        content.addView(lockSpacer(context, 2f))
        content.addView(lockSectionTitle(context, "锁屏密码"))
        val (row, field) =
            lockFieldRow(
                context,
                "四位数字密码",
                "",
                "输入四位数字",
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            )
        content.addView(row)
        content.addView(lockSpacer(context, 0.5f))
        content.addView(
            lockButtonRow(
                context,
                InkWidgets.button(context, "保存密码") { savePin(field.text.toString()) },
                InkWidgets.button(context, "清除密码") { clearPin() },
            ),
        )
        content.addView(lockSpacer(context, 0.5f))
        content.addView(InkWidgets.labelRow(context, "状态", if (hasPin) "已设置密码" else "未设置密码"))
    }

    /** 背景分组：选图、恢复默认与状态行。 */
    private fun addBackgroundSection() {
        val current = AppServices.config.state.value.appearance.lockWallpaperPath
        content.addView(lockSpacer(context, 2f))
        content.addView(lockSectionTitle(context, "锁屏背景"))
        content.addView(
            lockButtonRow(
                context,
                InkWidgets.button(context, "选择图片") { pickBackground() },
                InkWidgets.button(context, "恢复默认") {
                    restoreBackground()
                },
            ),
        )
        content.addView(lockSpacer(context, 0.5f))
        content.addView(
            InkWidgets.labelRow(context, "背景状态", backgroundStatusText(current)),
        )
        content.addView(lockCaption(context, backgroundCaption()))
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
        content.addView(lockSpacer(context, 2f))
        content.addView(
            lockCaption(
                context,
                "此锁只保护十雨桌面内的页面，不替代系统设备锁。" +
                    "自动锁定沿用十雨桌面「锁屏与安全」的设置，无需悬浮窗权限。",
            ),
        )
    }

    /**
     * 配置中是否已保存密码摘要。
     *
     * 与锁屏服务使用同一份配置节，状态行与开关校验都以摘要是否存在为准，
     * 避免旧偏好或默认值干扰判断。
     */
    private fun hasStoredPin(): Boolean {
        val pinHash = AppServices.config.state.value.lock.pinHash
        return !pinHash.isNullOrBlank()
    }

    /** 开启前必须已有宿主 PIN，关闭时保留密码和自动锁定设置。 */
    private fun setLockEnabled(enabled: Boolean) {
        if (enabled && !hasStoredPin()) {
            lockToast(context, "请先设置四位数字密码")
        } else {
            val saved =
                runCatching {
                    AppServices.config.edit { current -> current.copy(lock = current.lock.copy(enabled = enabled)) }
                    AppServices.config.state.value.lock.enabled == enabled
                }.getOrDefault(false)
            if (!saved) lockToast(context, "锁屏设置保存失败")
        }
        refresh()
    }

    /** 不改变自动锁定选项，直接让宿主展示唯一 PIN 覆盖层。 */
    private fun lockNow() {
        val lock = AppServices.config.state.value.lock
        if (!lock.enabled || !AppServices.lock.hasPin()) {
            lockToast(context, "请先设置密码并启用锁屏")
        } else {
            AppServices.lock.lock()
        }
    }

    /** 两次输入一致后交给宿主生成和保存密码摘要。 */
    private fun savePin(pin: String) {
        if (!PIN_PATTERN.matches(pin)) {
            lockToast(context, "请输入四位数字密码")
            return
        }
        InkDialogs.input(context, "再次输入以确认", hint = "四位数字", numeric = true, password = true) { second ->
            if (second != pin) {
                lockToast(context, "两次输入不一致，请重新设置")
            } else {
                saveConfirmedPin(pin)
            }
        }
    }

    /** 使用唯一锁屏服务，摘要计算在后台执行，完成后核对配置确实更新。 */
    private fun saveConfirmedPin(pin: String) {
        val observing = scope ?: return
        lockToast(context, "正在保存密码…")
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
            lockToast(context, if (saved) "密码已保存并启用锁屏" else "密码保存失败，请重试")
            refresh()
        }
    }

    /** 确认后清除宿主密码并关闭锁屏。 */
    private fun clearPin() {
        if (!AppServices.lock.hasPin()) {
            lockToast(context, "尚未设置密码")
            return
        }
        InkDialogs.confirm(context, "清除密码", "清除后十雨桌面将不再要求 PIN 解锁。", confirmLabel = "清除", onConfirm = {
            val saved =
                runCatching {
                    AppServices.lock.clearPin()
                    !AppServices.lock.hasPin() && !AppServices.config.state.value.lock.enabled
                }.getOrDefault(false)
            lockToast(context, if (saved) "密码已清除，锁屏已关闭" else "清除失败，请重试")
            refresh()
        })
    }

    /** 拉起系统文件选择器换取锁屏背景，仅允许图片。 */
    private fun pickBackground() {
        if (!AppServices.hasPicker()) {
            lockToast(context, "文件选择器不可用")
            return
        }
        val opened =
            runCatching {
                AppServices.picker.pick(arrayOf("image/*")) { uri ->
                    if (uri != null) applyBackground(uri)
                }
            }.getOrDefault(false)
        if (!opened) lockToast(context, "文件选择器不可用")
    }

    /** 使用壁纸仓库复制所选图片，成功与失败都写入状态行，并保留提示框。 */
    private fun applyBackground(uri: Uri) {
        val observing = scope ?: return
        observing.launch {
            val result = saveBackground(uri) ?: return@launch
            if (result == LockWallpaperResult.APPLIED) {
                backgroundUpdated = true
                backgroundError = null
                lockToast(context, "锁屏背景已更新")
                AppServices.refresh.requestFullRefresh()
            } else {
                backgroundUpdated = false
                backgroundError = failureReason(result)
                lockToast(context, "锁屏背景设置失败：${failureReason(result)}")
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
        lockToast(context, if (saved) "已恢复默认背景" else "背景恢复失败，请重试")
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

/**
 * 整页容器：纵向滚动，内容容器边距与其他设置页保持一致。
 *
 * 子类在 [render] 中组织内容；[refresh] 会清空容器后重新执行 [render]。
 */
internal abstract class LockPageView(
    context: Context,
) : FrameLayout(context) {
    /** 内容容器。 */
    protected val content =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                DesignRuntime.tokens.pageGutter,
                DesignRuntime.tokens.gap,
                DesignRuntime.tokens.pageGutter,
                DesignRuntime.tokens.pageBottom,
            )
        }

    init {
        addView(
            ScrollView(context).apply {
                isFillViewport = true
                addView(content)
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    /** 清空并重建内容。 */
    fun refresh() {
        content.removeAllViews()
        render()
    }

    /** 组织本页内容。 */
    protected abstract fun render()
}

/** 分段标题文本，上下留少量间距。 */
private fun lockSectionTitle(
    context: Context,
    text: CharSequence,
): TextView =
    InkWidgets.heading(context, text).apply {
        setPadding(0, DesignRuntime.tokens.gapLarge, 0, DesignRuntime.tokens.gap)
    }

/** 说明文本。 */
private fun lockCaption(
    context: Context,
    text: CharSequence,
): TextView = InkWidgets.caption(context, text)

/** 正文文本。 */
private fun lockBody(
    context: Context,
    text: CharSequence,
): TextView = InkWidgets.body(context, text)

/** 纵向占位。 */
private fun lockSpacer(
    context: Context,
    multiple: Float = 1f,
): View = InkWidgets.spacer(context, (DesignRuntime.tokens.gap * multiple).toInt())

/** 提示轻提示。 */
private fun lockToast(
    context: Context,
    text: CharSequence,
) {
    InkDialogs.toast(context, text.toString())
}

/**
 * 复选行。
 *
 * @param label 复选文案
 * @param checked 初始勾选状态
 * @param onChange 勾选状态变化回调，参数为最新状态
 */
private fun lockCheckRow(
    context: Context,
    label: CharSequence,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
): CheckBox =
    CheckBox(context).apply {
        text = label
        isChecked = checked
        setTextSize(TypedValue.COMPLEX_UNIT_PX, DesignRuntime.tokens.textBody)
        setTextColor(InkColors.INK)
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = DesignRuntime.tokens.listRowHeight
        layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        setOnCheckedChangeListener { _, value -> onChange(value) }
    }

/**
 * 输入行：左侧标签，右侧单行输入框。
 *
 * @param label 标签文案
 * @param initial 初始文本
 * @param hint 输入提示
 * @param inputType 输入类型，取 android.text.InputType 常量
 */
private fun lockFieldRow(
    context: Context,
    label: CharSequence,
    initial: CharSequence,
    hint: CharSequence,
    inputType: Int,
): Pair<LinearLayout, EditText> {
    val tokens = DesignRuntime.tokens
    val field =
        EditText(context).apply {
            setText(initial)
            this.hint = hint.toString()
            this.inputType = inputType
            isSingleLine = true
            setTextSize(TypedValue.COMPLEX_UNIT_PX, tokens.textBody)
            setTextColor(InkColors.INK)
            setHintTextColor(InkColors.INK_3)
            setPadding(tokens.gap, tokens.gap, tokens.gap, tokens.gap)
            background = InkWidgets.background(tokens.radius, InkColors.PAPER, tokens.stroke, InkColors.INK)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
    val row =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(tokens.gap, tokens.gap, tokens.gap, tokens.gap)
            addView(
                InkWidgets.textView(context, label, tokens.textBody, InkColors.INK).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                },
            )
            addView(field)
        }
    return row to field
}

/** 横向按钮行，按钮间留标准间距。 */
private fun lockButtonRow(
    context: Context,
    vararg buttons: TextView,
): LinearLayout =
    LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        buttons.forEachIndexed { index, button ->
            if (index > 0) {
                addView(
                    View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(DesignRuntime.tokens.gap, 1)
                    },
                )
            }
            addView(button)
        }
    }
