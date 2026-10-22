package com.shuiyu.octoberrain.feature.content.ui.negative

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.DeskModulePolicy
import com.shuiyu.octoberrain.core.common.WereadDeskConfig
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.core.wereadsync.WereadDeskEditsStore
import java.io.File

/**
 * 墨台设置态。
 *
 * 设置态在同一页面内替换列表态显示，分四组：模块开关与排序、书架书源与行数、阅读资产过滤、
 * 阅读指纹选书与字号。修改即时写入配置并同步到墨单、阅读资产、阅读指纹三个内核分区；写入后调用
 * [onChanged] 通知宿主刷新列表，不发生页面跳转。
 *
 * 版式上每个分组以标题起段，标题上方留一档大间距，行名与右侧控件同基线，说明文字单独
 * 成行并放宽行距；分组之间只画一条分隔线，行与行之间不再重复画线。
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

    private val edits = WereadDeskEditsStore(File(context.filesDir, "weread"))

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
        addGroupDivider()
        renderShelfGroup()
        addGroupDivider()
        renderAssetGroup()
        addGroupDivider()
        renderFingerprintGroup()
    }

    /**
     * 添加分组标题。
     *
     * 标题上方留一档大间距，与上一分组的分隔线拉开距离；标题与首行之间留常规间距，
     * 使标题独立成段，不与首行文字粘连。
     *
     * @param title 分组标题。
     */
    private fun addGroupTitle(title: String) {
        body.addView(
            InkWidgets.heading(context, title),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = tokens.gapLarge
                bottomMargin = tokens.gap
            },
        )
    }

    /**
     * 添加说明文字。
     *
     * 说明字号比正文小一档，行距放宽到 [HINT_LINE_SPACING] 倍，宽度撑满内容区并自动换行；
     * 与上方行名之间留 [HINT_GAP_DP] 的空距，避免说明紧贴行名。
     *
     * @param text 说明文案。
     */
    private fun addHint(text: String) {
        body.addView(
            InkWidgets.caption(context, text).apply {
                setTextColor(InkColors.INK_3)
                setLineSpacing(0f, HINT_LINE_SPACING)
            },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = tokens.dp(HINT_GAP_DP)
            },
        )
    }

    /** 在分组之间插入分隔线，上下各留一档常规间距，避免压住上一组的末行文字。 */
    private fun addGroupDivider() {
        body.addView(
            DeskSectionStyle.buildDivider(context),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = tokens.gap
                bottomMargin = tokens.gap
            },
        )
    }

    /**
     * 添加设置项行。
     *
     * 行与行之间统一留一档常规间距，使行名、说明与下一行之间形成稳定的纵向节奏。
     *
     * @param row 设置项行。
     */
    private fun addItem(row: LinearLayout) {
        body.addView(
            row,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = tokens.gap
            },
        )
    }

    /**
     * 构造设置行容器。
     *
     * 行高取分区行高与 [ROW_MIN_HEIGHT_DP] 的较大值，行内控件因此有不小于 40dp 的触控高度；
     * 行名与右侧控件垂直居中，落在同一基线上。
     *
     * @return 空行容器。
     */
    private fun newRow(): LinearLayout =
        LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = maxOf(DeskSectionStyle.rowHeightPx(context), tokens.dp(ROW_MIN_HEIGHT_DP))
        }

    /**
     * 向行内加入行名。
     *
     * 行名占满剩余宽度，单行显示并在尾部省略，右侧控件因此靠向行尾，与行名之间留出距离。
     *
     * @param row 目标行。
     * @param label 行名。
     */
    private fun addRowLabel(
        row: LinearLayout,
        label: String,
    ) {
        row.addView(
            InkWidgets.body(context, label).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            },
            LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
    }

    /**
     * 构造触控控件。
     *
     * 文案居中，最小宽高各 [TOUCH_TARGET_DP] 见方，点击区覆盖整块空白，相邻控件之间不再
     * 依赖文字本身的大小，减少误触。
     *
     * @param label 控件文案。
     * @param color 文字颜色。
     * @param onClick 点击回调。
     * @return 触控控件。
     */
    private fun touchControl(
        label: String,
        color: Int,
        onClick: () -> Unit,
    ): TextView =
        InkWidgets.caption(context, label).apply {
            setTextColor(color)
            gravity = Gravity.CENTER
            minWidth = tokens.dp(TOUCH_TARGET_DP)
            minimumWidth = minWidth
            minHeight = tokens.dp(TOUCH_TARGET_DP)
            minimumHeight = minHeight
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    /**
     * 构造加减数值控件。
     *
     * 减号、数值、加号横向排列，数值定宽居中，两个按钮的触控区各 [TOUCH_TARGET_DP] 见方；
     * 控件左侧留半个常规间距，与行名之间保持距离。
     *
     * @param value 当前数值文案。
     * @param onMinus 减号点击回调。
     * @param onPlus 加号点击回调。
     * @return 加减控件容器。
     */
    private fun buildStepper(
        value: String,
        onMinus: () -> Unit,
        onPlus: () -> Unit,
    ): LinearLayout =
        LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(tokens.gap / 2, 0, 0, 0)
            addView(touchControl(ACTION_STEP_MINUS, InkColors.INK, onMinus))
            addView(
                InkWidgets.body(context, value).apply {
                    gravity = Gravity.CENTER
                    minWidth = tokens.dp(VALUE_MIN_WIDTH_DP)
                    minimumWidth = minWidth
                },
            )
            addView(touchControl(ACTION_STEP_PLUS, InkColors.INK, onPlus))
        }

    /**
     * 构造只有行名的动作行。
     *
     * 整行可点击，行高与其它设置行一致，说明文字由调用方另行添加。
     *
     * @param label 动作文案。
     * @param onAction 点击回调。
     * @return 可点击的动作行。
     */
    private fun buildActionRow(
        label: String,
        onAction: () -> Unit,
    ): LinearLayout =
        newRow().apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { onAction() }
            addRowLabel(this, label)
        }

    /** 渲染模块开关与排序分组。 */
    private fun renderModuleGroup() {
        val config = AppServices.config.state.value
        addGroupTitle(GROUP_MODULES)
        addHint(HINT_MODULES)
        val modules = DeskModulePolicy.settingsOrder(config)
        modules.forEachIndexed { index, id ->
            addItem(buildModuleRow(id, index, modules.size))
        }
    }

    /**
     * 构造单个模块的开关与排序行。
     *
     * 行名占满左侧剩余宽度，开关与上下移按钮依次排列在行尾；三个控件的触控区均不小于
     * [TOUCH_TARGET_DP] 见方。
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
        val row = newRow()
        val enabled = DeskModulePolicy.isEnabled(AppServices.config.state.value, moduleId)
        addRowLabel(row, labelOf(moduleId))
        row.addView(
            touchControl(
                label = if (enabled) STATE_ON else STATE_OFF,
                color = if (enabled) InkColors.INK else InkColors.INK_3,
            ) { toggleModule(moduleId) },
        )
        row.addView(buildMoveButton(ACTION_UP, index > 0) { moveModule(moduleId, -1) })
        row.addView(buildMoveButton(ACTION_DOWN, index < count - 1) { moveModule(moduleId, 1) })
        return row
    }

    /**
     * 构造上移或下移按钮。
     *
     * 不可用时文字改用浅色并关闭点击，保留占位使行内控件位置稳定。
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
        touchControl(label, if (enabled) InkColors.INK else InkColors.INK_4, onMove).apply {
            isEnabled = enabled
            isClickable = enabled
        }

    /** 低频书架设置统一收纳；行数与书源只在书架标题行操作。 */
    private fun renderShelfGroup() {
        addGroupTitle("书架设置")
        body.addView(ShelfSettingsPanel(context, onChanged))
    }

    /** 渲染阅读资产过滤分组：两个开关、恢复动作与已移出数量说明。 */
    private fun renderAssetGroup() {
        val desk = AppServices.config.state.value.integrations.wereadDesk
        addGroupTitle(GROUP_ASSET)
        addItem(buildToggleRow(ASSET_SHELF_ONLY, desk.assetShelfOnly) { setAssetShelfOnly(it) })
        addItem(buildToggleRow(ASSET_FINISHED_ONLY, desk.assetFinishedOnly) { setAssetFinishedOnly(it) })
        addItem(buildActionRow(ACTION_RESTORE_HIDDEN) { restoreHidden() })
        addHint(hiddenSummary(edits.hiddenIds(desk.hiddenIds).size))
    }

    /**
     * 构造开关行。
     *
     * 行名与开关同基线，开关触控区不小于 [TOUCH_TARGET_DP] 见方；整行同样可点击，
     * 便于在墨水瓶上点按。
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
        val row = newRow()
        row.isClickable = true
        row.setOnClickListener { onToggle(!checked) }
        addRowLabel(row, label)
        row.addView(
            touchControl(
                label = if (checked) STATE_ON else STATE_OFF,
                color = if (checked) InkColors.INK else InkColors.INK_3,
            ) { onToggle(!checked) },
        )
        return row
    }

    /** 渲染阅读指纹分组：选书动作与字号行。 */
    private fun renderFingerprintGroup() {
        val desk = AppServices.config.state.value.integrations.wereadDesk
        addGroupTitle(GROUP_FINGERPRINT)
        addItem(buildActionRow(ACTION_PICK_BOOK) { onPickFingerprint() })
        val row = newRow()
        addRowLabel(row, LABEL_FP_FONT)
        row.addView(
            buildStepper(
                value = desk.effectiveFpFontPx.toString(),
                onMinus = { stepFpFont(-1) },
                onPlus = { stepFpFont(1) },
            ),
        )
        addItem(row)
    }

    /** 切换模块开关，配置和渲染共用同一规则。 */
    private fun toggleModule(moduleId: String) {
        AppServices.config.edit { config -> DeskModulePolicy.toggle(config, moduleId) }
        onChanged()
        render()
    }

    /** 调整模块位置，保持关闭模块可重新开启。 */
    private fun moveModule(
        moduleId: String,
        delta: Int,
    ) {
        AppServices.config.edit { config -> DeskModulePolicy.move(config, moduleId, delta) }
        onChanged()
        render()
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
        render()
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
        render()
    }

    /** 恢复已移出书目。 */
    private fun restoreHidden() {
        if (!edits.restoreHidden()) {
            AppServices.router.toast("恢复失败，请稍后重试")
            return
        }
        AppServices.config.edit { config ->
            config.copy(
                integrations =
                    config.integrations.copy(
                        wereadDesk = config.integrations.wereadDesk.copy(hiddenIds = emptyList()),
                    ),
            )
        }
        onChanged()
        render()
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
        render()
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

        /** 书架分组标题。 */
        const val GROUP_SHELF: String = "书架分区"

        /** 书源标签。 */
        const val LABEL_SHELF_SOURCE: String = "书源"

        /** 书架行数标签。 */
        const val LABEL_SHELF_ROWS: String = "书架行数"

        /** 微信读书书源的说明。 */
        const val HINT_SHELF_WEREAD: String = "展示微信读书书架，每行两本，最多五行共十本"

        /** 本地书源的说明。 */
        const val HINT_SHELF_LOCAL: String = "展示本地目录中的书籍，每行两本，最多五行共十本，并附最近阅读"

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

        /** 加减控件的减号文案。 */
        const val ACTION_STEP_MINUS: String = "－"

        /** 加减控件的加号文案。 */
        const val ACTION_STEP_PLUS: String = "＋"

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

        /** 说明文字与上一行之间的距离，取 3dp 使两层信息贴合但不粘连。 */
        const val HINT_GAP_DP: Float = 3f

        /** 说明文字的行距倍数，放宽到 1.3 倍便于长句阅读。 */
        const val HINT_LINE_SPACING: Float = 1.3f

        /** 触控控件的最小边长，保证点击区不小于 40dp 见方。 */
        const val TOUCH_TARGET_DP: Float = 40f

        /** 加减控件中数值的最小宽度。 */
        const val VALUE_MIN_WIDTH_DP: Float = 40f

        /** 设置行的最小高度，与触控区要求一致。 */
        const val ROW_MIN_HEIGHT_DP: Float = 40f

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
