package com.shuiyu.octoberrain.feature.wereadapp.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.effectiveWereadBoardPoolMask
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.feature.wereadapp.WereadAppPrefs
import com.shuiyu.octoberrain.feature.wereadapp.WereadAppSettings
import com.shuiyu.octoberrain.feature.wereadapp.ui.qr.KeyQrPanel

/**
 * 页面内设置页，按微读原版分为初始化与自定义两个页签。
 *
 * 两个页签常显在顶部，切换后只重建当前页内容；每页纵向滚动，表单改动写入
 * `kernel_prefs` 对应键，内核与桌面卡片按同一份偏好读取，改动即写即生效。
 *
 * @param context 创建视图的上下文
 */
@Suppress("TooManyFunctions", "LargeClass")
internal class WereadSettingsPage(
    context: Context,
) : LinearLayout(context) {
    /** 设置页对外回调。 */
    internal interface Listener {
        /** 完成初始化页配置后返回上一分段。 */
        fun onSaveAndBack()
    }

    /** 外部回调。 */
    var listener: Listener? = null

    /** 当前选中的页签下标。 */
    private var selectedTab: Int = TAB_INIT

    /** 顶部页签栏，回调在选中项变化时重建内容。 */
    private val tabs =
        SegmentBar(context) { index ->
            selectedTab = index
            render()
        }

    /** 当前页签的内容容器。 */
    private val content =
        LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(
                DesignRuntime.tokens.pageGutter,
                DesignRuntime.tokens.gap,
                DesignRuntime.tokens.pageGutter,
                DesignRuntime.tokens.pageBottom,
            )
        }

    /** 纵向滚动容器，切换页签时回到顶部。 */
    private val scroller: ScrollView =
        ScrollView(context).apply {
            isFillViewport = true
            addView(content)
        }

    /** API Key 输入框，保存与测试时读取。 */
    private var apiKeyInput: EditText? = null

    /** API Key 状态文本，保存或扫码录入后即时更新。 */
    private var keyStatusView: TextView? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(InkColors.PAPER)
        tabs.setItems(TAB_LABELS)
        addView(tabs, LayoutParams(LayoutParams.MATCH_PARENT, DesignRuntime.tokens.topBarHeight))
        addView(InkWidgets.divider(context))
        addView(
            scroller,
            LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f),
        )
        render()
    }

    /** 重新渲染当前页签，读取最新偏好值。 */
    fun refresh() {
        render()
    }

    /** 重建当前页签内容。 */
    private fun render() {
        content.removeAllViews()
        scroller.scrollTo(0, 0)
        apiKeyInput = null
        keyStatusView = null
        val settings = WereadAppPrefs.settings(context)
        when (selectedTab) {
            TAB_CUSTOM -> buildCustomPage(settings)
            else -> buildInitPage()
        }
    }

    /** 初始化页，按密钥、帮助与更新依次排列。 */
    private fun buildInitPage() {
        addInitKeySection()
        addInitUsageSection()
    }

    /** 微信读书 API Key：回填输入框、扫码录入、保存与测试连接。 */
    private fun addInitKeySection() {
        addSectionTitle("微信读书 API Key")
        val input =
            buildEditText(
                hint = "粘贴 API Key（wrk- 开头）",
                initial = currentApiKey(),
                numeric = false,
            ).apply {
                layoutParams = LayoutParams(0, DesignRuntime.tokens.buttonHeight, 1f)
            }
        apiKeyInput = input
        val row = horizontalRow()
        row.addView(input)
        row.addView(gapView())
        val paste =
            InkWidgets
                .button(context, "粘贴") {
                    input.setText(readClipboard())
                }.apply {
                    layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, DesignRuntime.tokens.buttonHeight)
                }
        row.addView(paste)
        content.addView(row)
        content.addView(InkWidgets.spacer(context, DesignRuntime.tokens.gap))
        addFullButton("扫描二维码录入") { openKeyQrPanel() }
        addKeyStatusRow()
        content.addView(InkWidgets.spacer(context, DesignRuntime.tokens.gap))
        addTwoButtons(
            leftLabel = "保存并返回",
            leftFilled = false,
            leftAction = {
                saveApiKey(input.text.toString())
                refreshKeyStatus()
                listener?.onSaveAndBack()
            },
            rightLabel = "测试连接",
            rightAction = { testConnection(input.text.toString()) },
        )
        addCaption("获取 API Key：手机客户端中，进入我-设置-微信读书 Skill 复制 Key。")
    }

    /** 打开局域网扫码录入面板，关闭后同步输入框与状态行。 */
    private fun openKeyQrPanel() {
        KeyQrPanel(context) {
            apiKeyInput?.setText(currentApiKey())
            refreshKeyStatus()
        }.show()
    }

    /** 添加密钥状态行，展示已配置时的前四位与后四位。 */
    private fun addKeyStatusRow() {
        val value = InkWidgets.caption(context, keyStatusText(currentApiKey()))
        keyStatusView = value
        val row =
            horizontalRow().apply {
                minimumHeight = DesignRuntime.tokens.listRowHeight
                setPadding(DesignRuntime.tokens.gap, 0, DesignRuntime.tokens.gap, 0)
                addView(
                    InkWidgets.body(context, "接口密钥状态").apply {
                        layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                    },
                )
                addView(value)
            }
        content.addView(row)
    }

    /** 按当前配置刷新状态行文本。 */
    private fun refreshKeyStatus() {
        keyStatusView?.text = keyStatusText(currentApiKey())
    }

    /** 生成密钥状态文本，空密钥显示未配置。 */
    private fun keyStatusText(key: String): String {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return "未配置"
        return "已配置（${trimmed.take(4)}****${trimmed.takeLast(4)}）"
    }

    /** 怎么用：打开使用说明。 */
    private fun addInitUsageSection() {
        content.addView(InkWidgets.spacer(context, DesignRuntime.tokens.gapLarge))
        addFullButton("怎么用") { showHelp() }
    }

    /** 自定义页，按开关、墨台、刷新绑定、成就、导出图与排名依次排列。 */
    private fun buildCustomPage(settings: WereadAppSettings) {
        addCheckRow("仅一页模式，翻页不再隐藏卡片", settings.singlePage) { checked ->
            update { it.copy(singlePage = checked) }
        }
        addCheckRow("启用墨台", settings.deskEnabled) { checked ->
            update { it.copy(deskEnabled = checked) }
        }
        addCustomBindSection(settings)
        addCustomAchievementSection(settings)
        addCustomMonthStyleSection(settings)
        addCustomNoteSizeSection(settings)
        addCustomSwitchModeSection(settings)
        addCustomPoolSection()
        addCustomExportSection(settings)
        addCustomRankSection(settings)
    }

    /** 刷新绑定：三个勾选项横向排列。 */
    private fun addCustomBindSection(settings: WereadAppSettings) {
        addBody("刷新绑定（点「更新于…」一并刷新勾选项）：桌面卡片右下角的刷新按钮与「更新于…」遵循同一组勾选。")
        val row = horizontalRow()
        addWeightedCheck(row, "本周", settings.bindWeek) { checked -> update { it.copy(bindWeek = checked) } }
        addWeightedCheck(row, "本月", settings.bindMonth) { checked -> update { it.copy(bindMonth = checked) } }
        addWeightedCheck(row, "本书", settings.bindBook) { checked -> update { it.copy(bindBook = checked) } }
        content.addView(row)
    }

    /** 阅读成就提示：开关、周目标与月目标档位及自定义输入。 */
    private fun addCustomAchievementSection(settings: WereadAppSettings) {
        addSectionTitle("阅读成就提示")
        addCheckRow("开启阅读成就提示", settings.achievementEnabled) { checked ->
            update { it.copy(achievementEnabled = checked) }
        }
        addCaption("开启后周/月卡片最底下显示距目标的差距")
        addBody("周目标")
        addRadioGroup(KIND_LABELS, settings.weekKind.coerceIn(0, 3)) { index ->
            update { it.copy(weekKind = index) }
        }
        content.addView(buildCustomGoalField(settings.weekCustom, "自定义周目标（小时，如 1.25）", true))
        addBody("月目标")
        addRadioGroup(KIND_LABELS, settings.monthKind.coerceIn(0, 3)) { index ->
            update { it.copy(monthKind = index) }
        }
        content.addView(buildCustomGoalField(settings.monthCustom, "自定义月目标（小时，如 40）", false))
    }

    /** 本月呈现：微读墨记与桌面本月卡片共享。 */
    private fun addCustomMonthStyleSection(settings: WereadAppSettings) {
        addSectionTitle("本月呈现")
        addRadioGroup(MONTH_STYLE_LABELS, if (settings.monthHeatmap) 1 else 0) { index ->
            update { it.copy(monthHeatmap = index == 1) }
        }
        addCaption("打卡＝读过打对勾；热力图＝按当天时长用深浅格子表示（0/30分钟/1小时/3小时四档）。用于微读墨记与桌面卡片的本月月历。")
    }

    /** 卡片档本记字号：数值显示加滑块，范围 12 到 24 号。 */
    private fun addCustomNoteSizeSection(settings: WereadAppSettings) {
        addSectionTitle("本记字号（卡片）")
        val valueLabel =
            InkWidgets.body(context, noteSizeLabel(settings.noteCardSize)).apply {
                layoutParams =
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT,
                    )
            }
        content.addView(valueLabel)
        val sizeBar =
            SeekBar(context).apply {
                max = NOTE_CARD_SIZE_MAX - NOTE_CARD_SIZE_MIN
                progress = (settings.noteCardSize - NOTE_CARD_SIZE_MIN).coerceIn(0, max)
                progressTintList = ColorStateList.valueOf(InkColors.INK)
                thumbTintList = ColorStateList.valueOf(InkColors.INK)
                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            bar: SeekBar?,
                            value: Int,
                            fromUser: Boolean,
                        ) {
                            valueLabel.text = noteSizeLabel(NOTE_CARD_SIZE_MIN + value)
                        }

                        override fun onStartTrackingTouch(bar: SeekBar?) = Unit

                        override fun onStopTrackingTouch(bar: SeekBar?) {
                            val size = NOTE_CARD_SIZE_MIN + (bar?.progress ?: 0)
                            update { it.copy(noteCardSize = size.coerceIn(NOTE_CARD_SIZE_MIN, NOTE_CARD_SIZE_MAX)) }
                        }
                    },
                )
            }
        content.addView(sizeBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addCaption("只影响桌面卡片上「本记」正文的大小。默认 17 号 = 桌面应用名称的字号；字越大，卡片能显示的行数越少。")
    }

    /** 卡片切换方式：循环与列表两档；本轮只写偏好，交互后续接入。 */
    private fun addCustomSwitchModeSection(settings: WereadAppSettings) {
        addSectionTitle("卡片切换方式")
        addRadioGroup(SWITCH_MODE_LABELS, settings.switchMode.coerceIn(0, 1)) { index ->
            update { it.copy(switchMode = index) }
        }
        addCaption("循环模式：点卡片左上角，依次切换到下一张。列表模式：点卡片左上角弹出列表，直接选一张。")
    }

    /** 桌面显示的卡片：五种形态勾选，至少保留一项；本书即书籍轮播。 */
    private fun addCustomPoolSection() {
        addSectionTitle("桌面显示的卡片")
        val poolMask = currentPoolMask()
        val firstRow = horizontalRow()
        POOL_LABELS.take(3).forEachIndexed { index, label ->
            addWeightedCheck(firstRow, label, poolMask and POOL_BITS[index] != 0) { checked ->
                togglePool(poolMask, index, checked)
            }
        }
        content.addView(firstRow)
        val secondRow = horizontalRow()
        POOL_LABELS.drop(3).forEachIndexed { offset, label ->
            val index = offset + 3
            addWeightedCheck(secondRow, label, poolMask and POOL_BITS[index] != 0) { checked ->
                togglePool(poolMask, index, checked)
            }
        }
        secondRow.addView(
            View(context),
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f),
        )
        content.addView(secondRow)
        addCaption("决定桌面小组件右下角「切换」按钮可轮换的形态，至少保留一项；本书即书籍轮播。只影响桌面卡片；App 内各页面的查看不受影响。")
    }

    /** 读取桌面形态池掩码；配置未就绪时按五项全开处理。 */
    private fun currentPoolMask(): Int {
        if (!AppServices.isReady()) return POOL_MASK_ALL
        return AppServices.config.state.value.desktop.effectiveWereadBoardPoolMask
    }

    /** 切换形态池某一位；取消最后一项时保留本周。 */
    private fun togglePool(
        currentMask: Int,
        index: Int,
        checked: Boolean,
    ) {
        val nextMask = if (checked) currentMask or POOL_BITS[index] else currentMask and POOL_BITS[index].inv()
        if (nextMask == 0) {
            InkDialogs.toast(context, "至少要保留一项，已保留本周")
            applyPoolMask(POOL_BITS[0])
            return
        }
        applyPoolMask(nextMask)
    }

    /** 写入桌面形态池掩码并刷新桌面组件。 */
    private fun applyPoolMask(mask: Int) {
        if (!AppServices.isReady()) return
        AppServices.config.edit { config ->
            config.copy(desktop = config.desktop.copy(wereadBoardPoolMask = mask))
        }
        render()
        AppServices.refresh.requestPartial()
    }

    /** 本记字号文案，形如「17 号 · 20.4px」。 */
    private fun noteSizeLabel(size: Int): String {
        val metrics = resources.displayMetrics
        val unit = maxOf(metrics.widthPixels, metrics.heightPixels) * UNIT_PER_LONG_EDGE
        return String.format(java.util.Locale.US, "%d 号 · %.1fpx", size, size * unit)
    }

    /** 本记导出图：模板说明、纸张底纹、字号与两个导出勾选项。 */
    private fun addCustomExportSection(settings: WereadAppSettings) {
        addSectionTitle("本记导出图")
        content.addView(InkWidgets.heading(context, "本记导出模板"))
        addCaption("只影响「本记」页导出图片的样式，改完即时生效。")
        addBody("纸张底纹")
        addRadioGroup(PAPER_LABELS, settings.exportTemplate.coerceIn(0, 2)) { index ->
            update { it.copy(exportTemplate = index) }
        }
        addBody("字号（本记页与导出图）")
        addRadioGroup(SIZE_LABELS, settings.exportSize.coerceIn(0, 2)) { index ->
            update { it.copy(exportSize = index) }
        }
        addCheckRow("导出图显示署名（书名·作者）", settings.noteSign) { checked ->
            update { it.copy(noteSign = checked) }
        }
        addCheckRow("导出图显示章节与划线日期", settings.noteDate) { checked ->
            update { it.copy(noteDate = checked) }
        }
    }

    /** 书籍排名：只统计书架上的书及说明。 */
    private fun addCustomRankSection(settings: WereadAppSettings) {
        addSectionTitle("书籍排名")
        addCheckRow("只统计书架上的书", settings.rankShelfOnly) { checked ->
            update { it.copy(rankShelfOnly = checked) }
        }
        addCaption("打开后，「本月」页与「足迹·读书排行」只列出你书架上的书；已从书架移除的书不再出现。")
        addCaption("首次打开会自动联网同步一次书架清单。")
    }

    /** 写入设置并刷新表单与桌面卡片。 */
    private fun update(transform: (WereadAppSettings) -> WereadAppSettings) {
        WereadAppPrefs.update(context, transform)
        render()
        AppServices.refresh.requestPartial()
    }

    /** 保存自定义周/月目标，只写偏好不重建页面，避免输入框失焦时丢失输入。 */
    private fun saveCustomGoal(
        raw: String,
        week: Boolean,
    ) {
        val value = raw.trim()
        WereadAppPrefs.update(context) {
            if (week) it.copy(weekCustom = value) else it.copy(monthCustom = value)
        }
        AppServices.refresh.requestPartial()
    }

    /** 创建自定义目标输入框，失焦时保存。 */
    private fun buildCustomGoalField(
        initial: String,
        hint: String,
        week: Boolean,
    ): EditText =
        buildEditText(hint, initial, true).apply {
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) saveCustomGoal(text.toString(), week)
            }
        }

    /** 读取当前配置中的接口密钥，配置未就绪时返回空串。 */
    private fun currentApiKey(): String {
        if (!AppServices.isReady()) return ""
        return AppServices.config.state.value.integrations.wereadApiKey
    }

    /** 保存接口密钥到启动器配置。 */
    private fun saveApiKey(raw: String) {
        if (!AppServices.isReady()) {
            InkDialogs.toast(context, "启动器配置尚未就绪")
            return
        }
        val key = raw.trim()
        AppServices.config.edit {
            it.copy(integrations = it.integrations.copy(wereadApiKey = key))
        }
        InkDialogs.toast(context, if (key.isEmpty()) "已清除接口密钥" else "接口密钥已保存")
    }

    /** 检查密钥与同步服务是否可用。 */
    private fun testConnection(raw: String) {
        val apiKey = raw.trim().ifEmpty { currentApiKey() }
        val message =
            when {
                !AppServices.isReady() -> "启动器配置尚未就绪"
                apiKey.isBlank() -> "尚未配置接口密钥"
                AppServices.wereadSync == null -> "同步服务未装配"
                else -> "密钥已配置，同步服务可用"
            }
        InkDialogs.toast(context, message)
    }

    /** 从剪贴板读取文本。 */
    private fun readClipboard(): String {
        val manager = context.getSystemService(ClipboardManager::class.java)
        return manager
            ?.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            .orEmpty()
    }

    /** 原入口打开十雨完整使用说明。 */
    private fun showHelp() {
        LauncherHelpDialog(context).show()
    }

    /** 添加分组标题；首组不加前导间距。 */
    private fun addSectionTitle(title: String) {
        if (content.childCount > 0) content.addView(InkWidgets.spacer(context, DesignRuntime.tokens.gapLarge))
        content.addView(InkWidgets.heading(context, title))
        content.addView(InkWidgets.divider(context))
    }

    /** 添加整宽按钮。 */
    private fun addFullButton(
        label: String,
        onClick: () -> Unit,
    ) {
        val button =
            InkWidgets.button(context, label, false, onClick).apply {
                layoutParams =
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        DesignRuntime.tokens.buttonHeight,
                    )
            }
        content.addView(button)
    }

    /** 添加两个等宽按钮。 */
    private fun addTwoButtons(
        leftLabel: String,
        leftFilled: Boolean,
        leftAction: () -> Unit,
        rightLabel: String,
        rightAction: () -> Unit,
    ) {
        val row = horizontalRow()
        row.addView(
            InkWidgets.button(context, leftLabel, leftFilled, leftAction).apply {
                layoutParams = LayoutParams(0, DesignRuntime.tokens.buttonHeight, 1f)
            },
        )
        row.addView(gapView())
        row.addView(
            InkWidgets.button(context, rightLabel, false, rightAction).apply {
                layoutParams = LayoutParams(0, DesignRuntime.tokens.buttonHeight, 1f)
            },
        )
        content.addView(row)
    }

    /** 添加带复选框的整行开关。 */
    private fun addCheckRow(
        label: String,
        checked: Boolean,
        onChange: (Boolean) -> Unit,
    ) {
        val row = horizontalRow()
        row.minimumHeight = DesignRuntime.tokens.listRowHeight
        row.addView(checkBox(checked))
        row.addView(
            InkWidgets.textView(context, label, DesignRuntime.tokens.textBody, InkColors.INK).apply {
                layoutParams =
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT,
                    ).apply { leftMargin = DesignRuntime.tokens.gap }
            },
        )
        row.isClickable = true
        row.isFocusable = true
        row.setOnClickListener { onChange(!checked) }
        content.addView(row)
    }

    /** 向横向容器追加一个等宽勾选项。 */
    private fun addWeightedCheck(
        row: LinearLayout,
        label: String,
        checked: Boolean,
        onChange: (Boolean) -> Unit,
    ) {
        val option = horizontalRow()
        option.addView(checkBox(checked))
        option.addView(
            InkWidgets.textView(context, label, DesignRuntime.tokens.textBody, InkColors.INK).apply {
                layoutParams =
                    LayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT,
                    ).apply { leftMargin = DesignRuntime.tokens.gap }
            },
        )
        option.isClickable = true
        option.isFocusable = true
        option.setOnClickListener { onChange(!checked) }
        option.layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        row.addView(option)
    }

    /** 添加单选组，选项等宽横向排列。 */
    private fun addRadioGroup(
        options: List<String>,
        selected: Int,
        onPick: (Int) -> Unit,
    ) {
        val row = horizontalRow()
        options.forEachIndexed { index, label ->
            row.addView(
                radioOption(label, index == selected) { onPick(index) }.apply {
                    layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                },
            )
        }
        content.addView(row)
    }

    /** 创建单个单选项：圆圈加文字。 */
    private fun radioOption(
        label: String,
        selected: Boolean,
        onClick: () -> Unit,
    ): LinearLayout =
        horizontalRow().apply {
            addView(radioCircle(selected))
            addView(
                InkWidgets.textView(context, label, DesignRuntime.tokens.textBody, InkColors.INK).apply {
                    layoutParams =
                        LayoutParams(
                            LayoutParams.WRAP_CONTENT,
                            LayoutParams.WRAP_CONTENT,
                        ).apply { leftMargin = DesignRuntime.tokens.gap }
                },
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    /** 创建复选框视图。 */
    private fun checkBox(checked: Boolean): FrameLayout {
        val tokens = DesignRuntime.tokens
        val size = tokens.dp(18f)
        return FrameLayout(context).apply {
            layoutParams = LayoutParams(size, size)
            if (checked) {
                background = InkWidgets.background(tokens.dp(2f), InkColors.INK)
                addView(
                    InkWidgets.textView(context, "✓", tokens.textBody, InkColors.PAPER).apply {
                        gravity = Gravity.CENTER
                        layoutParams =
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT,
                            )
                    },
                )
            } else {
                background =
                    InkWidgets.background(tokens.dp(2f), InkColors.PAPER, maxOf(1, tokens.stroke), InkColors.INK)
            }
        }
    }

    /** 创建单选框视图。 */
    private fun radioCircle(selected: Boolean): FrameLayout {
        val tokens = DesignRuntime.tokens
        val size = tokens.dp(18f)
        val inner = tokens.dp(8f)
        return FrameLayout(context).apply {
            layoutParams = LayoutParams(size, size)
            background =
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(InkColors.PAPER)
                    setStroke(maxOf(1, tokens.stroke), InkColors.INK)
                }
            if (selected) {
                addView(
                    View(context).apply {
                        background =
                            GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(InkColors.INK)
                            }
                        layoutParams =
                            FrameLayout.LayoutParams(inner, inner).apply {
                                gravity = Gravity.CENTER
                            }
                    },
                )
            }
        }
    }

    /** 创建横向容器。 */
    private fun horizontalRow(): LinearLayout =
        LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

    /** 创建等宽空隙。 */
    private fun gapView(): View =
        View(context).apply {
            layoutParams = LayoutParams(DesignRuntime.tokens.gap, 1)
        }

    /** 创建带描边的单行输入框。 */
    private fun buildEditText(
        hint: String,
        initial: String,
        numeric: Boolean,
    ): EditText {
        val tokens = DesignRuntime.tokens
        return EditText(context).apply {
            this.hint = hint
            setText(initial)
            setSingleLine()
            setTextSize(TypedValue.COMPLEX_UNIT_PX, tokens.textBody)
            setTextColor(InkColors.INK)
            setHintTextColor(InkColors.INK_3)
            background = InkWidgets.background(tokens.radius, InkColors.PAPER, tokens.stroke, InkColors.INK_4)
            setPadding(tokens.gap, 0, tokens.gap, 0)
            if (numeric) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, tokens.buttonHeight)
            setOnEditorActionListener { _, _, _ ->
                saveCustomGoal(text.toString(), hint.contains("周"))
                false
            }
        }
    }

    /** 添加说明段落。 */
    private fun addBody(text: String) {
        content.addView(InkWidgets.spacer(context, DesignRuntime.tokens.gap))
        content.addView(InkWidgets.body(context, text))
    }

    /** 添加小号说明文字。 */
    private fun addCaption(text: String) {
        content.addView(InkWidgets.spacer(context, DesignRuntime.tokens.gap))
        content.addView(InkWidgets.caption(context, text))
    }

    /** 选项、档位与页签常量。 */
    private companion object {
        /** 初始化页签下标。 */
        const val TAB_INIT: Int = 0

        /** 自定义页签下标。 */
        const val TAB_CUSTOM: Int = 1

        /** 两个页签的文案，顺序与下标一致。 */
        val TAB_LABELS: List<String> = listOf("初始化", "自定义")

        /** 成就目标档位文字。 */
        val KIND_LABELS: List<String> = listOf("关", "完美", "狂暴", "自定义")

        /** 导出图纸张底纹文字。 */
        val PAPER_LABELS: List<String> = listOf("素白", "手账", "卡纸")

        /** 导出图与正文的字号档位文字。 */
        val SIZE_LABELS: List<String> = listOf("小字", "中字", "大字")

        /** 本月呈现档位文字。 */
        val MONTH_STYLE_LABELS: List<String> = listOf("打卡", "热力图")

        /** 卡片切换方式文字。 */
        val SWITCH_MODE_LABELS: List<String> = listOf("循环模式（默认）", "列表模式")

        /** 形态池文字，顺序与 [POOL_BITS] 一致。 */
        val POOL_LABELS: List<String> = listOf("本周", "本月", "本书", "本记", "待办")

        /** 形态池位掩码，低五位依次对应本周、本月、本书、本记、待办。 */
        val POOL_BITS: IntArray = intArrayOf(1, 2, 4, 8, 16)

        /** 五种形态全开的掩码，作为配置未就绪时的显示初值。 */
        const val POOL_MASK_ALL: Int = 0x1F

        /** 卡片档本记字号下限。 */
        const val NOTE_CARD_SIZE_MIN: Int = 12

        /** 卡片档本记字号上限。 */
        const val NOTE_CARD_SIZE_MAX: Int = 24

        /** 号值到像素的换算比例，与内核一致。 */
        const val UNIT_PER_LONG_EDGE: Float = 0.0015f
    }
}
