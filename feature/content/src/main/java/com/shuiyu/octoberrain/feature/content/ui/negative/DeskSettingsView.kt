package com.shuiyu.octoberrain.feature.content.ui.negative

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.WereadDeskConfig
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkWidgets

/**
 * 墨台设置态。
 *
 * 设置态在同一页面内替换列表态显示，分三组：模块开关与排序、阅读资产过滤、阅读指纹选书与
 * 字号。修改即时写入配置并同步到墨单、阅读资产、阅读指纹三个内核分区；写入后调用
 * [onChanged] 通知宿主刷新列表，不发生页面跳转。
 *
 * @param context 页面上下文。
 * @param onClose 关闭设置态回到列表态的回调。
 * @param onChanged 配置写入后的刷新回调。
 * @param onPickFingerprint 打开阅读指纹选书对话框的回调。
 */
@Suppress("TooManyFunctions")
internal class DeskSettingsView(
    context: Context,
    private val onClose: () -> Unit,
    private val onChanged: () -> Unit,
    private val onPickFingerprint: () -> Unit,
) : LinearLayout(context) {
    private val tokens = DesignRuntime.tokens

    private val body: LinearLayout =
        LinearLayout(context).apply {
            orientation = VERTICAL
            val padding = DeskSectionStyle.paddingXPx(resources.displayMetrics.widthPixels)
            setPadding(padding, 0, padding, tokens.gapLarge)
        }

    init {
        orientation = VERTICAL
        addView(
            DeskSectionStyle.buildHeader(context, TITLE, ACTION_CLOSE, showBar = false, onAction = onClose),
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        addView(
            body,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    /** 依据当前配置重建设置项。 */
    fun render() {
        body.removeAllViews()
        renderModuleGroup()
        body.addView(DeskSectionStyle.buildDivider(context))
        renderAssetGroup()
        body.addView(DeskSectionStyle.buildDivider(context))
        renderFingerprintGroup()
    }

    /** 渲染模块开关与排序分组。 */
    private fun renderModuleGroup() {
        val config = AppServices.config.state.value
        body.addView(InkWidgets.heading(context, GROUP_MODULES))
        val modules = config.desktop.negativePage.modules
        body.addView(
            InkWidgets.caption(context, HINT_MODULES).apply { setTextColor(InkColors.INK_3) },
        )
        modules.forEachIndexed { index, id ->
            body.addView(buildModuleRow(id, index, modules.size))
        }
    }

    /**
     * 构造单个模块的开关与排序行。
     *
     * @param moduleId 模块标识。
     * @param index 模块在展示顺序中的下标。
     * @param count 模块总数。
     * @return 模块行。
     */
    private fun buildModuleRow(
        moduleId: String,
        index: Int,
        count: Int,
    ): LinearLayout {
        val row =
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = DeskSectionStyle.rowHeightPx(context)
            }
        val enabled = isModuleEnabled(moduleId)
        val switchView =
            InkWidgets.caption(context, if (enabled) STATE_ON else STATE_OFF).apply {
                setTextColor(if (enabled) InkColors.INK else InkColors.INK_3)
                isClickable = true
                setOnClickListener { toggleModule(moduleId) }
            }
        row.addView(
            InkWidgets.body(context, labelOf(moduleId)).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            },
            LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(switchView, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(
            buildMoveButton(ACTION_UP, index > 0) { moveModule(moduleId, -1) },
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        row.addView(
            buildMoveButton(ACTION_DOWN, index < count - 1) { moveModule(moduleId, 1) },
            LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        return row
    }

    /**
     * 构造上移或下移按钮。
     *
     * @param label 按钮文案。
     * @param enabled 是否可用。
     * @param onMove 点击回调。
     * @return 按钮视图。
     */
    private fun buildMoveButton(
        label: String,
        enabled: Boolean,
        onMove: () -> Unit,
    ): TextView =
        InkWidgets.caption(context, label).apply {
            setTextColor(if (enabled) InkColors.INK else InkColors.INK_4)
            isEnabled = enabled
            isClickable = enabled
            // 三个右侧控件在窄屏上会挤出行宽，水平间距取半个间距单位。
            setPadding(tokens.gap / 2, 0, 0, 0)
            if (enabled) setOnClickListener { onMove() }
        }

    /** 渲染阅读资产过滤分组。 */
    private fun renderAssetGroup() {
        val desk = AppServices.config.state.value.integrations.wereadDesk
        body.addView(InkWidgets.heading(context, GROUP_ASSET))
        body.addView(buildToggleRow(ASSET_SHELF_ONLY, desk.assetShelfOnly) { setAssetShelfOnly(it) })
        body.addView(buildToggleRow(ASSET_FINISHED_ONLY, desk.assetFinishedOnly) { setAssetFinishedOnly(it) })
        body.addView(
            InkWidgets.caption(context, ACTION_RESTORE_HIDDEN).apply {
                isClickable = true
                setOnClickListener { restoreHidden() }
            },
        )
        body.addView(
            InkWidgets.caption(context, hiddenSummary(desk.hiddenIds.size)).apply { setTextColor(InkColors.INK_3) },
        )
    }

    /**
     * 构造开关行。
     *
     * @param label 开关名称。
     * @param checked 当前状态。
     * @param onToggle 切换回调。
     * @return 开关行。
     */
    private fun buildToggleRow(
        label: String,
        checked: Boolean,
        onToggle: (Boolean) -> Unit,
    ): LinearLayout {
        val row =
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = DeskSectionStyle.rowHeightPx(context)
                isClickable = true
                setOnClickListener { onToggle(!checked) }
            }
        row.addView(
            InkWidgets.body(context, label),
            LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(
            InkWidgets.caption(context, if (checked) STATE_ON else STATE_OFF).apply {
                setTextColor(if (checked) InkColors.INK else InkColors.INK_3)
            },
        )
        return row
    }

    /** 渲染阅读指纹分组。 */
    private fun renderFingerprintGroup() {
        val desk = AppServices.config.state.value.integrations.wereadDesk
        body.addView(InkWidgets.heading(context, GROUP_FINGERPRINT))
        body.addView(
            InkWidgets.caption(context, ACTION_PICK_BOOK).apply {
                isClickable = true
                setOnClickListener { onPickFingerprint() }
            },
        )
        val row =
            LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = DeskSectionStyle.rowHeightPx(context)
            }
        row.addView(
            InkWidgets.body(context, LABEL_FP_FONT),
            LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(
            InkWidgets.caption(context, ACTION_FONT_MINUS).apply {
                isClickable = true
                setOnClickListener { stepFpFont(-1) }
            },
        )
        row.addView(
            InkWidgets.body(context, desk.effectiveFpFontPx.toString()).apply {
                gravity = Gravity.CENTER
                setPadding(tokens.gap, 0, tokens.gap, 0)
            },
        )
        row.addView(
            InkWidgets.caption(context, ACTION_FONT_PLUS).apply {
                isClickable = true
                setOnClickListener { stepFpFont(1) }
            },
        )
        body.addView(row)
    }

    /**
     * 判断模块的开关状态。
     *
     * 内核三项（墨单、阅读资产、阅读指纹）的开关由开关掩码决定，顺序列表始终保留三项；
     * 书架与最近图片属于宿主分区，开关即顺序列表是否包含该标识。
     *
     * @param moduleId 模块标识。
     * @return 开启时为 true。
     */
    private fun isModuleEnabled(moduleId: String): Boolean {
        val config = AppServices.config.state.value
        if (moduleId !in KERNEL_MODULE_IDS) {
            return config.desktop.negativePage.modules
                .contains(moduleId)
        }
        return config.integrations.wereadDesk.effectiveOnMask and bitOf(moduleId) != 0
    }

    /**
     * 取模块对应的开关掩码位。
     *
     * @param moduleId 内核模块标识。
     * @return 掩码位，未知模块返回零。
     */
    private fun bitOf(moduleId: String): Int =
        when (moduleId) {
            MODULE_BILL -> WereadDeskConfig.BIT_BILL
            MODULE_RANK -> WereadDeskConfig.BIT_RANK
            MODULE_FP -> WereadDeskConfig.BIT_FP
            else -> 0
        }

    /**
     * 切换模块开关。
     *
     * 内核三项只切换开关掩码，顺序列表保持五项；其余模块直接增删顺序列表。
     *
     * @param moduleId 模块标识。
     */
    private fun toggleModule(moduleId: String) {
        val config = AppServices.config.state.value
        val modules = config.desktop.negativePage.modules
        if (moduleId in KERNEL_MODULE_IDS) {
            val mask = config.integrations.wereadDesk.effectiveOnMask xor bitOf(moduleId)
            AppServices.config.edit { current ->
                current.copy(
                    integrations =
                        current.integrations.copy(
                            wereadDesk = current.integrations.wereadDesk.copy(onMask = mask),
                        ),
                )
            }
            onChanged()
            return
        }
        val next = if (modules.contains(moduleId)) modules - moduleId else modules + moduleId
        writeModules(next)
    }

    /**
     * 调整模块在列表中的位置。
     *
     * @param moduleId 模块标识。
     * @param delta 位移，负值上移，正值下移。
     */
    private fun moveModule(
        moduleId: String,
        delta: Int,
    ) {
        val current = AppServices.config.state.value.desktop.negativePage.modules
        val index = current.indexOf(moduleId)
        if (index < 0) return
        val target = index + delta
        if (target !in current.indices) return
        val next = current.toMutableList()
        next.removeAt(index)
        next.add(target, moduleId)
        writeModules(next)
    }

    /**
     * 写入五项顺序并同步内核顺序与开关掩码。
     *
     * @param modules 新的模块顺序。
     */
    private fun writeModules(modules: List<String>) {
        AppServices.config.edit { config -> applyModules(config, modules) }
        onChanged()
    }

    /**
     * 把模块顺序写入配置，同时更新内核三项顺序与开关掩码。
     *
     * @param config 当前配置。
     * @param modules 新的模块顺序。
     * @return 更新后的配置。
     */
    private fun applyModules(
        config: LauncherConfig,
        modules: List<String>,
    ): LauncherConfig {
        val kernelOrder = modules.filter { it in KERNEL_MODULE_IDS }
        val desk = config.integrations.wereadDesk
        var mask = desk.effectiveOnMask
        // 顺序列表中缺失的内核模块清除对应掩码位，列表内的模块保持原开关状态。
        KERNEL_MODULE_IDS.filter { it !in modules }.forEach { mask = mask and bitOf(it).inv() }
        val desktop = config.desktop.copy(negativePage = config.desktop.negativePage.copy(modules = modules))
        val integrations =
            config.integrations.copy(
                wereadDesk = desk.copy(order = kernelOrder, onMask = mask),
            )
        return config.copy(desktop = desktop, integrations = integrations)
    }

    /**
     * 写入「只统计书架」开关。
     *
     * @param enabled 是否开启。
     */
    private fun setAssetShelfOnly(enabled: Boolean) {
        AppServices.config.edit { config ->
            config.copy(
                integrations =
                    config.integrations.copy(
                        wereadDesk = config.integrations.wereadDesk.copy(assetShelfOnly = enabled),
                    ),
            )
        }
        onChanged()
    }

    /**
     * 写入「只统计读完」开关。
     *
     * @param enabled 是否开启。
     */
    private fun setAssetFinishedOnly(enabled: Boolean) {
        AppServices.config.edit { config ->
            config.copy(
                integrations =
                    config.integrations.copy(
                        wereadDesk = config.integrations.wereadDesk.copy(assetFinishedOnly = enabled),
                    ),
            )
        }
        onChanged()
    }

    /** 恢复已移出书目。 */
    private fun restoreHidden() {
        AppServices.config.edit { config ->
            config.copy(
                integrations =
                    config.integrations.copy(
                        wereadDesk = config.integrations.wereadDesk.copy(hiddenIds = emptyList()),
                    ),
            )
        }
        onChanged()
    }

    /**
     * 调整阅读指纹字号。
     *
     * @param delta 位移，负值减小，正值增大。
     */
    private fun stepFpFont(delta: Int) {
        val desk = AppServices.config.state.value.integrations.wereadDesk
        val options = WereadDeskConfig.FP_FONT_OPTIONS
        val index = options.indexOf(desk.effectiveFpFontPx).coerceAtLeast(0)
        val next = options[(index + delta).coerceIn(0, options.size - 1)]
        AppServices.config.edit { config ->
            config.copy(
                integrations =
                    config.integrations.copy(
                        wereadDesk = config.integrations.wereadDesk.copy(fpFontPx = next),
                    ),
            )
        }
        onChanged()
    }

    /**
     * 把模块标识转成展示名称。
     *
     * @param moduleId 模块标识。
     * @return 展示名称。
     */
    private fun labelOf(moduleId: String): String =
        when (moduleId) {
            MODULE_BILL -> LABEL_BILL
            MODULE_RANK -> LABEL_RANK
            MODULE_FP -> LABEL_FP
            MODULE_BOOKSHELF -> LABEL_BOOKSHELF
            MODULE_RECENT -> LABEL_RECENT
            else -> moduleId
        }

    /**
     * 生成已移出书目的摘要文案。
     *
     * @param count 已移出书目数量。
     * @return 摘要文案。
     */
    private fun hiddenSummary(count: Int): String = "已移出 " + count + " 本"

    private companion object {
        /** 设置态标题。 */
        const val TITLE: String = "墨台设置"

        /** 关闭设置态的动作文案。 */
        const val ACTION_CLOSE: String = "完成"

        /** 模块分组标题。 */
        const val GROUP_MODULES: String = "模块与顺序"

        /** 模块分组说明。 */
        const val HINT_MODULES: String = "开关决定显示哪些分区，顺序即自上而下的排列"

        /** 资产分组标题。 */
        const val GROUP_ASSET: String = "阅读资产过滤"

        /** 指纹分组标题。 */
        const val GROUP_FINGERPRINT: String = "阅读指纹"

        /** 开关开启文案。 */
        const val STATE_ON: String = "开"

        /** 开关关闭文案。 */
        const val STATE_OFF: String = "关"

        /** 上移动作文案。 */
        const val ACTION_UP: String = "↑"

        /** 下移动作文案。 */
        const val ACTION_DOWN: String = "↓"

        /** 开关名称：只统计书架。 */
        const val ASSET_SHELF_ONLY: String = "只统计书架"

        /** 开关名称：只统计读完。 */
        const val ASSET_FINISHED_ONLY: String = "只统计读完"

        /** 恢复已移出书目的动作文案。 */
        const val ACTION_RESTORE_HIDDEN: String = "恢复已移出书目"

        /** 阅读指纹选书动作文案。 */
        const val ACTION_PICK_BOOK: String = "选择指纹书目"

        /** 指纹字号标签。 */
        const val LABEL_FP_FONT: String = "字号"

        /** 字号减小动作文案。 */
        const val ACTION_FONT_MINUS: String = "－"

        /** 字号增大动作文案。 */
        const val ACTION_FONT_PLUS: String = "＋"

        /** 模块标识：墨单。 */
        const val MODULE_BILL: String = "bill"

        /** 模块标识：阅读资产。 */
        const val MODULE_RANK: String = "rank"

        /** 模块标识：阅读指纹。 */
        const val MODULE_FP: String = "fp"

        /** 模块标识：书架。 */
        const val MODULE_BOOKSHELF: String = "bookshelf"

        /** 模块标识：最近图片。 */
        const val MODULE_RECENT: String = "negative_files"

        /** 内核模块标识集合。 */
        val KERNEL_MODULE_IDS: List<String> = listOf(MODULE_BILL, MODULE_RANK, MODULE_FP)

        /** 展示名称：墨单。 */
        const val LABEL_BILL: String = "墨单"

        /** 展示名称：阅读资产。 */
        const val LABEL_RANK: String = "阅读资产"

        /** 展示名称：阅读指纹。 */
        const val LABEL_FP: String = "阅读指纹"

        /** 展示名称：书架。 */
        const val LABEL_BOOKSHELF: String = "书架"

        /** 展示名称：最近图片。 */
        const val LABEL_RECENT: String = "最近图片"
    }
}
