package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.NegativeModule
import com.shuiyu.octoberrain.core.common.NegativeModuleRegistry
import com.shuiyu.octoberrain.core.common.WereadMenuConfig
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkGlyphs
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.design.InkWidgets

/**
 * 负一屏设置页。
 *
 * 页面由两个分组构成：卡片开关与顺序逐行列出负一屏模块，行内直接切换启用状态并通过
 * 上移下移按钮调整顺序；查看效果分组提供进入负一屏的入口。
 * 所有写入都经过 [SettingsUi.edit]，写入后通知内容层刷新，使负一屏按新顺序重建。
 *
 * 卡片开关、顺序调整与墨单十三项各自成组，每组构建逻辑独立成函数以保持可读性，
 * 页面本身不再拆分，因此对函数数量度量局部豁免。
 */
@Suppress("TooManyFunctions")
class NegativeSettingsScreen : SettingsBaseScreen("负一屏设置") {
    /** 配置回写定时器，连续修改时只保留最后一次待发任务。 */
    private val prefsHandler = Handler(Looper.getMainLooper())

    /** 延迟到连续修改停止后再推送的待发任务。 */
    private val prefsPushRunnable = Runnable { pushWereadPrefs() }

    /**
     * 构建页面内容。
     *
     * 参数 page 为承载内容的页面；config 为当前配置快照。
     */
    override fun build(
        page: InkPage,
        config: LauncherConfig,
    ) {
        SettingsUi.beginRowCount(page)
        buildModules(page, config)
        buildMenu(page, config)
        buildEntry(page)
        SettingsUi.note(
            page,
            "关闭的卡片不加载数据；上移下移调整卡片在负一屏中的上下顺序，调整后负一屏立即重建。",
        )
    }

    /** 卡片开关与顺序分组：逐行生成模块状态与上下移动按钮。 */
    private fun buildModules(
        page: InkPage,
        config: LauncherConfig,
    ) {
        val registered = NegativeModuleRegistry.all()
        val order = NegativeSettingsLogic.moduleOrder(config, registered)
        val section =
            SettingsUi.section(
                page,
                "卡片开关与顺序",
                "关闭后不加载数据，顺序即负一屏自上而下的排列",
            )
        order.forEachIndexed { index, moduleId ->
            section.content(buildModuleRow(page.context, config, registered, order.size, index, moduleId))
        }
    }

    /** 查看效果分组：进入负一屏确认排列结果。 */
    private fun buildEntry(page: InkPage) {
        val section = SettingsUi.section(page, "查看效果", "设置立即作用于桌面负一屏")
        section.action(
            label = "打开负一屏",
            value = "也可在桌面首页右滑进入",
            onClick = { AppServices.router.navigate(Destination.Negative) },
        )
    }

    /**
     * 生成一行模块状态。
     *
     * 参数 context 为页面上下文；config 为当前配置；
     * registered 为已注册模块；size 为本次展示的模块总数；
     * index 为该行的位置；moduleId 为模块标识。
     * 行整体可点击切换启用状态，上移与下移按钮在边界位置禁用。
     */
    private fun buildModuleRow(
        context: Context,
        config: LauncherConfig,
        registered: List<NegativeModule>,
        size: Int,
        index: Int,
        moduleId: String,
    ): LinearLayout {
        val tokens = DesignRuntime.tokens
        val enabled = NegativeSettingsLogic.enabled(config, moduleId)
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = tokens.listRowHeight
                setPadding(tokens.gap, tokens.gap / 2, tokens.gap, tokens.gap / 2)
            }
        val labelColumn =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f,
                    )
                addView(
                    InkWidgets.textView(
                        context,
                        NegativeSettingsLogic.label(moduleId, registered),
                        tokens.textBody,
                        InkColors.INK,
                    ),
                )
                addView(
                    InkWidgets
                        .textView(
                            context,
                            NegativeSettingsLogic.hint(moduleId),
                            tokens.textCaption,
                            InkColors.INK_2,
                        ).apply { setPadding(0, tokens.gap / 2, 0, 0) },
                )
            }
        row.addView(labelColumn)
        row.addView(moveButton(context, "上移", index > 0) { moveModule(moduleId, -1) })
        row.addView(moveButton(context, "下移", index < size - 1) { moveModule(moduleId, 1) })
        val stateText =
            InkWidgets
                .textView(context, if (enabled) "开" else "关", tokens.textCaption, InkColors.INK_3)
                .apply { setPadding(tokens.gap, 0, 0, 0) }
        val check = InkGlyphs.view(context, InkGlyphs.Kind.CHECK)
        check.alpha = if (enabled) 1f else 0f
        row.addView(stateText)
        row.addView(check)
        row.isClickable = true
        row.isFocusable = true
        row.setOnClickListener { toggleModule(moduleId) }
        return row
    }

    /** 生成上移或下移按钮，不可用位置以浅色文字呈现且不响应点击。 */
    private fun moveButton(
        context: Context,
        label: String,
        enabled: Boolean,
        onClick: () -> Unit,
    ): TextView {
        val tokens = DesignRuntime.tokens
        return InkWidgets
            .textView(context, label, tokens.textCaption, if (enabled) InkColors.INK else InkColors.INK_4)
            .apply {
                gravity = Gravity.CENTER
                setPadding(tokens.gap, tokens.gap / 2, tokens.gap, tokens.gap / 2)
                isEnabled = enabled
                if (enabled) {
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { onClick() }
                }
            }
    }

    /** 切换模块启用状态，写入后通知内容层并重建本页。 */
    private fun toggleModule(moduleId: String) {
        SettingsUi.edit { config -> NegativeSettingsLogic.toggle(config, moduleId) }
        RefreshSchedulerBridge.notifyContentChanged()
        scheduleWereadPrefsPush()
        rebuildLater()
    }

    /** 调整模块位置，写入后通知内容层并重建本页。 */
    private fun moveModule(
        moduleId: String,
        delta: Int,
    ) {
        SettingsUi.edit { config ->
            NegativeSettingsLogic.move(config, moduleId, delta, NegativeModuleRegistry.all())
        }
        RefreshSchedulerBridge.notifyContentChanged()
        scheduleWereadPrefsPush()
        rebuildLater()
    }

    /**
     * 安排一次延迟的微读墨记配置回写。
     *
     * 开关与步进控件每次操作都会写盘，连续操作时只保留最后一次回写，
     * 避免频繁拉起微读墨记的导出组件。定时器随页面销毁取消。
     */
    private fun scheduleWereadPrefsPush() {
        prefsHandler.removeCallbacks(prefsPushRunnable)
        prefsHandler.postDelayed(prefsPushRunnable, PREFS_PUSH_DELAY_MS)
    }

    /**
     * 把当前配置推送给微读墨记。
     *
     * 未装配回写入口（未安装微读墨记）时静默返回；请求发起失败时同样不提示，
     * 发起成功时由回调给出同步结果并提示。
     */
    private fun pushWereadPrefs() {
        val writer = AppServices.wereadPrefs
        val hostContext = contextOrNull()
        if (writer != null && hostContext != null) {
            val callbackContext = hostContext.applicationContext ?: hostContext
            // 未安装微读墨记或导出组件缺失时回调不会触发，此处不改界面；
            // 请求成功发起时由回调给出同步结果并提示。
            writer.push { message ->
                SettingsUi.toast(callbackContext, message)
            }
        }
    }

    /** 页面销毁时移除待发的回写任务，避免回调落在已释放的页面上。 */
    override fun onDestroy() {
        prefsHandler.removeCallbacks(prefsPushRunnable)
        super.onDestroy()
    }

    /**
     * 墨单外观分组：逐项编辑微读墨记偏好。
     *
     * 参数 page 为承载内容的页面；config 为当前配置快照。
     * 十三项取值与微读墨记 cfg 偏好一一对应，写入后触发内容层局部刷新，
     * 使负一屏墨单卡片与桌面整体卡片的墨单形态同步更新。
     */
    private fun buildMenu(
        page: InkPage,
        config: LauncherConfig,
    ) {
        val menu = config.integrations.wereadMenu
        val section =
            SettingsUi.section(
                page,
                "墨单外观",
                "以下设置同时作用于负一屏墨单卡片与桌面整体卡片的墨单形态",
            )
        buildMenuHeader(page, menu, section)
        buildMenuRank(menu, section)
        buildMenuContent(menu, section)
        buildMenuFooter(menu, section)
    }

    /** 墨单页眉与入选时长。 */
    private fun buildMenuHeader(
        page: InkPage,
        menu: WereadMenuConfig,
        section: SettingsSection,
    ) {
        section.action(
            label = "页眉标题",
            value = menu.effectiveTitle,
        ) {
            InkDialogs.input(page.context, "页眉标题", initial = menu.effectiveTitle) { text ->
                if (text.isBlank()) {
                    SettingsUi.toast(page.context, "标题不能为空")
                } else {
                    writeMenu { it.copy(title = text) }
                    rebuildLater()
                }
            }
        }
        section.action(
            label = "入选最短时长",
            value = "${menu.effectiveMinSeconds} 秒",
        ) {
            InkDialogs.input(
                page.context,
                "入选最短时长",
                hint = "单位秒，非负整数",
                initial = menu.effectiveMinSeconds.toString(),
                numeric = true,
            ) { text ->
                val seconds = text.toIntOrNull()
                if (seconds == null || seconds < 0) {
                    SettingsUi.toast(page.context, "请输入非负整数")
                } else {
                    writeMenu { it.copy(minSeconds = seconds) }
                    rebuildLater()
                }
            }
        }
    }

    /** 墨单榜单口径：单位、条数、下架过滤。 */
    private fun buildMenuRank(
        menu: WereadMenuConfig,
        section: SettingsSection,
    ) {
        section.choose(
            label = "时长单位",
            options = UNIT_LABELS,
            selected = { WereadMenuConfig.UNIT_OPTIONS.indexOf(menu.effectiveUnit).coerceAtLeast(0) },
        ) { index ->
            writeMenu { it.copy(unit = WereadMenuConfig.UNIT_OPTIONS[index]) }
        }
        section.choose(
            label = "榜单条数",
            options = TOP_N_LABELS,
            selected = { WereadMenuConfig.TOP_N_OPTIONS.indexOf(menu.effectiveTopN).coerceAtLeast(0) },
        ) { index ->
            writeMenu { it.copy(topN = WereadMenuConfig.TOP_N_OPTIONS[index]) }
        }
        section.toggle(
            label = "过滤下架书",
            get = { menu.dropOffShelf },
            hint = "榜单中排除已下架书籍",
        ) { enabled ->
            writeMenu { it.copy(dropOffShelf = enabled) }
        }
    }

    /** 墨单条目内容：来源、摘录方式与三项展示开关。 */
    private fun buildMenuContent(
        menu: WereadMenuConfig,
        section: SettingsSection,
    ) {
        section.choose(
            label = "笔记来源",
            options = NOTE_SOURCE_LABELS,
            selected = {
                WereadMenuConfig.NOTE_SOURCE_OPTIONS.indexOf(menu.effectiveNoteSource).coerceAtLeast(0)
            },
        ) { index ->
            writeMenu { it.copy(noteSource = WereadMenuConfig.NOTE_SOURCE_OPTIONS[index]) }
        }
        section.choose(
            label = "摘录模式",
            options = EXCERPT_LABELS,
            selected = {
                WereadMenuConfig.EXCERPT_OPTIONS.indexOf(menu.effectiveExcerptMode).coerceAtLeast(0)
            },
        ) { index ->
            writeMenu { it.copy(excerptMode = WereadMenuConfig.EXCERPT_OPTIONS[index]) }
        }
        section.toggle(
            label = "显示作者",
            get = { menu.showAuthor },
            hint = "条目中展示书籍作者",
        ) { enabled ->
            writeMenu { it.copy(showAuthor = enabled) }
        }
        section.toggle(
            label = "显示时长",
            get = { menu.showDuration },
            hint = "条目中展示阅读时长",
        ) { enabled ->
            writeMenu { it.copy(showDuration = enabled) }
        }
        section.toggle(
            label = "显示进度",
            get = { menu.showProgress },
            hint = "条目中展示阅读进度",
        ) { enabled ->
            writeMenu { it.copy(showProgress = enabled) }
        }
    }

    /** 墨单页脚与整体形态。 */
    private fun buildMenuFooter(
        menu: WereadMenuConfig,
        section: SettingsSection,
    ) {
        section.choose(
            label = "页脚类型",
            options = FOOTER_LABELS,
            selected = { WereadMenuConfig.FOOTER_OPTIONS.indexOf(menu.effectiveFooter).coerceAtLeast(0) },
        ) { index ->
            writeMenu { it.copy(footer = WereadMenuConfig.FOOTER_OPTIONS[index]) }
        }
        section.stepper(
            label = "块显示",
            values = BLOCK_LABELS,
            hint = "位 1 票据头、位 2 合计行、位 4 整单备注",
            selected = { menu.effectiveBlocksMask.coerceIn(0, BLOCK_LABELS.lastIndex) },
        ) { index ->
            writeMenu { it.copy(blocksMask = index) }
        }
        section.stepper(
            label = "视图形态",
            values = VIEW_LABELS,
            selected = { menu.effectiveView.coerceIn(0, VIEW_LABELS.lastIndex) },
        ) { index ->
            writeMenu { it.copy(view = index) }
        }
    }

    /** 写入墨单配置并请求内容层局部刷新。 */
    private fun writeMenu(transform: (WereadMenuConfig) -> WereadMenuConfig) {
        SettingsUi.edit { config ->
            config.copy(
                integrations =
                    config.integrations.copy(wereadMenu = transform(config.integrations.wereadMenu)),
            )
        }
        AppServices.refresh.requestPartial()
        scheduleWereadPrefsPush()
    }

    private companion object {
        /** 配置回写的防抖间隔，单位毫秒。 */
        private const val PREFS_PUSH_DELAY_MS = 1_200L

        /** 时长单位的显示文本，顺序与 [WereadMenuConfig.UNIT_OPTIONS] 一致。 */
        private val UNIT_LABELS = listOf<CharSequence>("小时", "分钟")

        /** 榜单条数的显示文本，顺序与 [WereadMenuConfig.TOP_N_OPTIONS] 一致。 */
        private val TOP_N_LABELS = listOf<CharSequence>("3 条", "5 条", "8 条")

        /** 笔记来源的显示文本，顺序与 [WereadMenuConfig.NOTE_SOURCE_OPTIONS] 一致。 */
        private val NOTE_SOURCE_LABELS = listOf<CharSequence>("自动", "手动", "自动优先")

        /** 摘录模式的显示文本，顺序与 [WereadMenuConfig.EXCERPT_OPTIONS] 一致。 */
        private val EXCERPT_LABELS = listOf<CharSequence>("我的摘录", "热门填充", "仅热门", "不限")

        /** 页脚类型的显示文本，顺序与 [WereadMenuConfig.FOOTER_OPTIONS] 一致。 */
        private val FOOTER_LABELS =
            listOf<CharSequence>("无", "笔记", "条码", "条码与笔记")

        /** 块显示位的候选文本，下标即位值组合。 */
        private val BLOCK_LABELS =
            listOf<CharSequence>(
                "0 · 不显示",
                "1 · 票据头",
                "2 · 合计行",
                "3 · 票据头与合计行",
                "4 · 整单备注",
                "5 · 票据头与整单备注",
                "6 · 合计行与整单备注",
                "7 · 全部显示",
            )

        /** 视图形态的显示文本，下标即取值。 */
        private val VIEW_LABELS = listOf<CharSequence>("摘录菜单", "读书菜单")
    }
}
