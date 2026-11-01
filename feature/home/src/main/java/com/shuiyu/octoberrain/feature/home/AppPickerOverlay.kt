package com.shuiyu.octoberrain.feature.home

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.shuiyu.octoberrain.core.common.DesktopFolder
import com.shuiyu.octoberrain.core.common.LauncherEntry
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkWidgets
import kotlin.math.ceil

/** 文本行高与字号的比例，用于按标签行数估算固定行高。 */
private const val LINE_HEIGHT_RATIO = 1.4f

/** 候选行图标边长，单位为 dp。 */
private const val ICON_SIZE_DP = 40f

/** 控件不可用时的透明度。 */
private const val INACTIVE_ALPHA = 0.45f

/** 高度预算很低时的对话框内边距，单位为 dp。 */
private const val TIGHT_PADDING_DP = 8f

/** 适配等级：完整布局。 */
private const val FIT_NORMAL = 0

/** 适配等级：摘要并入页码行，候选行压缩为两行标签。 */
private const val FIT_COMPACT = 1

/** 适配等级：再隐藏标题并收窄内边距，把底部按钮留在可见区域内。 */
private const val FIT_TIGHT = 2

/**
 * 依据可用高度与固定控件高度选择适配等级。
 *
 * 固定控件指标题、名称、搜索、页码行与底部按钮；候选区只占剩余空间，
 * 因此只要剩余空间不足一行就把摘要与页码合并、再不够则隐藏标题收窄内边距。
 */
private fun fitModeFor(
    availableHeight: Int,
    fixedHeight: Int,
    summaryHeight: Int,
    headingHeight: Int,
    minRowHeight: Int,
): Int =
    when {
        availableHeight - fixedHeight - summaryHeight - headingHeight >= minRowHeight -> FIT_NORMAL
        availableHeight - fixedHeight - headingHeight >= minRowHeight -> FIT_COMPACT
        else -> FIT_TIGHT
    }

/** Owns only transient selection; the host commits and dismisses after confirmed persistence. */
internal class AppPickerOverlay(
    context: Context,
) : FrameLayout(context) {
    var candidates: List<FolderCandidate> = emptyList()
    var drawIcon: ((Canvas, RectF, LauncherEntry) -> Unit)? = null
    private val tokens get() = DesignRuntime.tokens
    private var dialog: Dialog? = null
    private val iconViews = mutableListOf<View>()

    fun open(
        targetFolder: DesktopFolder?,
        newFolderName: String?,
        preselected: List<String>,
        existingFolders: List<DesktopFolder>,
        apps: List<LauncherEntry>,
        onCancel: () -> Unit,
        onSubmit: (members: List<String>, name: String?) -> Unit,
    ) {
        val content = PickerContent(targetFolder, newFolderName, preselected, existingFolders, apps, onSubmit)
        addView(content.root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        content.bind()
        val host =
            Dialog(context).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setCanceledOnTouchOutside(true)
                setContentView(this@AppPickerOverlay)
                setOnCancelListener { onCancel() }
            }
        dialog = host
        host.show()
        sizeFolderPickerDialog(host)
        host.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE,
        )
        content.focusInitialField()
    }

    private inner class PickerContent(
        targetFolder: DesktopFolder?,
        newFolderName: String?,
        preselected: List<String>,
        existingFolders: List<DesktopFolder>,
        apps: List<LauncherEntry>,
        onSubmit: (members: List<String>, name: String?) -> Unit,
    ) {
        private val targetId = targetFolder?.id

        // Capture once: filtering never replaces the candidate universe or selected set.
        private val snapshot = candidates.toList()
        private val entries = apps.associateBy { it.key }
        private val folderNames = existingFolders.associate { it.id to it.name }
        private var selected = applyPreselection(preselected)
        val root =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(tokens.dialogPadding, tokens.dialogPadding, tokens.dialogPadding, tokens.dialogPadding)
                background = InkWidgets.background(tokens.radius, InkColors.PAPER, tokens.stroke, InkColors.INK)
            }
        private val heading = InkWidgets.heading(context, targetFolder?.name ?: "新建文件夹")
        val search = input("搜索应用名称或包名")
        val name =
            if (targetId == null) {
                input("文件夹名称").apply {
                    setText(newFolderName ?: "文件夹")
                }
            } else {
                null
            }

        // 009：候选区按固定行高分为若干页；翻页只重建页内行，已选集合与名称草稿不受影响。
        private val pager =
            PickerPager(
                context,
                rowFactory = { candidate -> buildRow(candidate) },
                clearIcons = { iconViews.clear() },
            )
        private val summary = InkWidgets.caption(context, "")
        private val footer = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        private var fitMode = FIT_NORMAL
        private var summaryRowHeight = 0
        private var headingRowHeight = 0
        private var fitScheduled = false
        private val submit =
            InkWidgets.button(context, "", filled = true) {
                val keys = FolderAppSelection.selectedKeys(snapshot, selected, targetId)
                if (keys.isEmpty()) {
                    InkDialogs.toast(context, "没有可添加的应用")
                } else {
                    onSubmit(keys, name?.text?.toString()?.trim())
                }
            }

        init {
            root.addView(heading)
            // 新建模式先给名称输入，再给搜索；添加模式没有名称行，顺序不变。
            name?.let { root.addView(it) }
            root.addView(search)
            root.addView(summary)
            root.addView(pager.pager)
            root.addView(
                pager.area,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
            buildFooter()
        }

        /**
         * 按模式确定初始焦点：新建文件夹聚焦名称并全选默认文本，添加成员聚焦搜索框。
         *
         * 窗口已显示，因此在 post 中申请焦点并显式请求软键盘，确保中文输入法可用。
         */
        fun focusInitialField() {
            val field: EditText = name ?: search
            field.post {
                field.requestFocus()
                if (field === name) field.setSelection(0, field.text.length)
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        private fun applyPreselection(preselected: List<String>): Set<String> {
            var selection = emptySet<String>()
            preselected.forEach { key ->
                if (key !in selection) selection = FolderAppSelection.toggle(selection, key, snapshot, targetId)
            }
            return selection
        }

        private fun buildFooter() {
            footer.addView(InkWidgets.button(context, "取消") { dialog?.cancel() })
            footer.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            footer.addView(submit, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f))
            root.addView(footer)
        }

        fun bind() {
            search.addTextChangedListener(watcher { onSearch() })
            name?.addTextChangedListener(watcher { refreshSummary() })
            refreshSummary()
            pager.refresh(FolderAppSelection.filter(snapshot, search.text.toString()))
            // 首次布局与键盘显隐都会改变候选区高度，据此重新计算每页容量。
            pager.watchLayout()
            // 可用高度不足时逐级收紧布局，保证底部按钮始终留在可见区域内。
            root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> scheduleFit() }
            scheduleFit()
        }

        /** 搜索条件变化后回到第一页，避免停留在结果集中已不存在的页码。 */
        private fun onSearch() {
            pager.refresh(FolderAppSelection.filter(snapshot, search.text.toString()), resetPage = true)
        }

        private fun refreshSummary() {
            val plan = FolderAppSelection.plan(snapshot, selected, targetId)
            val count = plan.added + plan.moved
            summary.text =
                listOfNotNull(
                    if (plan.added > 0) "添加 ${plan.added} 个" else null,
                    if (plan.moved > 0) "移动 ${plan.moved} 个" else null,
                ).joinToString("，").ifEmpty { "请选择应用" }
            submit.text = if (targetId == null) "新建并添加 $count 个" else "添加 $count 个"
            submit.isEnabled = count > 0 &&
                (
                    name == null ||
                        name.text
                            .toString()
                            .trim()
                            .isNotEmpty()
                )
            submit.alpha = if (submit.isEnabled) 1f else INACTIVE_ALPHA
            // 低高度预算下摘要并入页码行，由分页控件统一显示；完整布局时页码行只放页码。
            pager.update(if (fitMode == FIT_NORMAL) "" else summary.text.toString())
        }

        /** 登记一次适配检查：布局回调期间只入队一次，实际判断放到遍历结束之后。 */
        private fun scheduleFit() {
            if (fitScheduled) return
            fitScheduled = true
            root.post {
                fitScheduled = false
                updateFitMode()
            }
        }

        /**
         * 按实测可用高度收紧布局，只升级不降级以免布局回调反复切换。
         *
         * 先合并摘要与页码并压缩候选行，仍不够时隐藏标题、收窄内边距，
         * 目标是名称、搜索、页码与底部按钮同时留在可见区域内。
         */
        private fun updateFitMode() {
            val available = root.height - root.paddingTop - root.paddingBottom
            if (available <= 0) return
            if (summary.visibility == View.VISIBLE) summaryRowHeight = summary.height
            if (heading.visibility == View.VISIBLE) headingRowHeight = heading.height
            val fixedHeight = (name?.height ?: 0) + search.height + pager.pager.height + footer.height
            val next =
                fitModeFor(
                    availableHeight = available,
                    fixedHeight = fixedHeight,
                    summaryHeight = summaryRowHeight,
                    headingHeight = headingRowHeight,
                    minRowHeight = pager.compactRowHeightPx,
                )
            if (next <= fitMode) return
            fitMode = next
            summary.visibility = if (next == FIT_NORMAL) View.VISIBLE else View.GONE
            heading.visibility = if (next == FIT_TIGHT) View.GONE else View.VISIBLE
            val padding = if (next == FIT_TIGHT) tokens.dp(TIGHT_PADDING_DP) else tokens.dialogPadding
            root.setPadding(padding, padding, padding, padding)
            pager.compact = next != FIT_NORMAL
            pager.refresh(FolderAppSelection.filter(snapshot, search.text.toString()), force = true)
            refreshSummary()
            root.requestLayout()
        }

        private fun buildRow(candidate: FolderCandidate): View {
            val existing = targetId != null && candidate.parentFolderId == targetId
            val row =
                LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    // 行高由调用方按容量计算后以布局参数固定，行内只保留上下内边距。
                    setPadding(0, tokens.gap, 0, tokens.gap)
                }
            val icon =
                object : View(context) {
                    override fun onDraw(canvas: Canvas) {
                        entries[candidate.key]?.let {
                            drawIcon?.invoke(canvas, RectF(0f, 0f, width.toFloat(), height.toFloat()), it)
                        }
                    }
                }
            row.addView(icon, LinearLayout.LayoutParams(tokens.dp(ICON_SIZE_DP), tokens.dp(ICON_SIZE_DP)))
            iconViews.add(icon)
            val labels = buildLabels(candidate, existing)
            row.addView(labels, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val check =
                CheckBox(context).apply {
                    isChecked = existing || candidate.key in selected
                    isClickable = false
                    isFocusable = false
                    buttonTintList =
                        android.content.res.ColorStateList
                            .valueOf(InkColors.INK)
                }
            row.addView(check)
            row.isEnabled = !existing && candidate.installed
            row.isFocusable = row.isEnabled
            row.contentDescription =
                listOf(
                    candidate.label,
                    candidate.packageName,
                    if (existing) "已添加" else "",
                ).joinToString("，")
            row.setOnClickListener {
                if (row.isEnabled) {
                    selected = FolderAppSelection.toggle(selected, candidate.key, snapshot, targetId)
                    check.isChecked = candidate.key in selected
                    refreshSummary()
                }
            }
            return row
        }

        private fun buildLabels(
            candidate: FolderCandidate,
            existing: Boolean,
        ): View =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(tokens.gap, 0, tokens.gap, 0)
                // 固定行高下每条标签占一行，超长部分省略，保证页内行数与容量计算一致。

                fun singleLine(view: TextView): TextView =
                    view.apply {
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    }
                addView(singleLine(InkWidgets.body(context, candidate.label)))
                // Package names distinguish equal labels and remain searchable.
                addView(
                    singleLine(
                        InkWidgets.caption(
                            context,
                            PickerCandidateLabels.detail(candidate, existing, folderNames, pager.compact),
                        ),
                    ),
                )
                val status =
                    when {
                        existing -> "已添加"
                        !candidate.installed -> "已卸载"
                        candidate.parentFolderId != null ->
                            "位于：${folderNames[candidate.parentFolderId] ?: candidate.parentFolderName.orEmpty()}"
                        else -> null
                    }
                // 压缩行高时只保留名称与包名两行，状态信息让位给固定标题与按钮。
                if (status != null && !pager.compact) addView(singleLine(InkWidgets.caption(context, status)))
            }
    }

    fun invalidateIcons() {
        iconViews.forEach { it.invalidate() }
    }

    fun cancel(): Boolean {
        val host = dialog?.takeIf { it.isShowing } ?: return false
        host.cancel()
        dialog = null
        return true
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    private fun input(hintText: String): EditText =
        EditText(context).apply {
            hint = hintText
            isSingleLine = true
            setTextColor(InkColors.INK)
            setHintTextColor(InkColors.INK_2)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, tokens.textBody)
            typeface = DesignRuntime.typeface()
            setBackgroundColor(InkColors.PAPER)
        }

    private fun watcher(action: () -> Unit): TextWatcher =
        object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int,
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int,
            ) = action()

            override fun afterTextChanged(s: Editable?) = Unit
        }
}

/**
 * 添加应用选择器的候选分页容器。
 *
 * 只维护当前页的行容器、页码控件与页容量；候选行由外部工厂创建，
 * 选择集合由调用方维护，翻页与容量变化都不会改变已选项。
 */
private class PickerPager(
    private val context: Context,
    private val rowFactory: (FolderCandidate) -> View,
    private val clearIcons: () -> Unit,
) {
    private val tokens get() = DesignRuntime.tokens
    private val previousButton = InkWidgets.button(context, "▲") { turn(-1) }
    private val nextButton = InkWidgets.button(context, "▼") { turn(1) }
    private val indicator =
        InkWidgets.caption(context, "").apply {
            gravity = Gravity.CENTER
            // 摘要并入本行时可能较长，限制单行并从中间省略，页码始终可见。
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
        }
    private val areaContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val pagerRow = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }

    /** 行间分隔线高度，取自 divider 的实际布局参数，与容量计算保持一致。 */
    private val dividerHeightPx: Int = InkWidgets.divider(context).layoutParams.height
    private var page = 0
    private var pageSize = 0
    private var visible: List<FolderCandidate> = emptyList()
    private var refreshScheduled = false

    /** 低高度预算下把候选行压缩为两行标签，容量与行高随之变化。 */
    var compact: Boolean = false

    /** 与页码同行显示的摘要文本，空串表示摘要独占一行。 */
    private var indicatorSupplement = ""

    /** 压缩后的单行占位，供外层判断剩余空间是否至少容纳一行候选。 */
    val compactRowHeightPx: Int = rowContentHeightPx(compact = true) + dividerHeightPx

    /** 候选区容器，在对话框中按剩余高度铺满。 */
    val area: View get() = areaContainer

    /** 页码控件所在行。 */
    val pager: View get() = pagerRow

    init {
        previousButton.contentDescription = "上一页"
        nextButton.contentDescription = "下一页"
        pagerRow.addView(previousButton)
        pagerRow.addView(
            indicator,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = Gravity.CENTER
            },
        )
        pagerRow.addView(nextButton)
    }

    /**
     * 记录可见候选并刷新当前页。
     *
     * 容量与结果集均未变化时跳过行重建；[resetPage] 为真的搜索变化场景先把页码归零，
     * [force] 为真时（适配等级切换）即使容量相同也重建，保证行高与标签行数同步变化。
     */
    fun refresh(
        candidates: List<FolderCandidate>,
        resetPage: Boolean = false,
        force: Boolean = false,
    ) {
        val previousPage = page
        if (resetPage) page = 0
        val nextSize = PickerPagination.pageSize(availableHeightPx(), rowContentHeightPx(compact) + dividerHeightPx)
        // 页码被复位但结果集未变时仍需重建，否则画面会停留在旧页的行上。
        if (canSkipRefresh(force, nextSize, candidates, previousPage)) {
            update()
            return
        }
        visible = candidates
        pageSize = nextSize
        page = PickerPagination.clampPage(page, visible.size, pageSize)
        render()
        update()
    }

    /**
     * 判断本轮刷新是否可以只更新计数、跳过重建。
     *
     * 参数 force 表示强制重建，nextSize 为按当前可用高度算出的新容量，
     * candidates 为新结果集，previousPage 为刷新前的页索引。
     * 只有容量、结果集与页索引都未变化且未强制重建时返回真。
     */
    private fun canSkipRefresh(
        force: Boolean,
        nextSize: Int,
        candidates: List<FolderCandidate>,
        previousPage: Int,
    ): Boolean = !force && nextSize == pageSize && candidates == visible && page == previousPage

    /**
     * 监听候选区高度与窗口可见区域变化，容量或结果集变化时重新计算页。
     *
     * 回调只登记一次重算，实际重建放到遍历结束之后：布局回调期间增删子 View 会落在
     * 本次测量与布局中间，新行当场得不到尺寸，界面会停在空白容器上。
     */
    fun watchLayout() {
        fun schedule() {
            if (refreshScheduled) return
            refreshScheduled = true
            areaContainer.post {
                refreshScheduled = false
                refresh(visible)
            }
        }
        areaContainer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> schedule() }
        areaContainer.viewTreeObserver.addOnGlobalLayoutListener { schedule() }
    }

    /** 只重建当前页的候选行，旧页图标引用随容器清空一并释放。 */
    private fun render() {
        clearIcons()
        areaContainer.removeAllViews()
        if (visible.isEmpty()) {
            areaContainer.addView(InkWidgets.body(context, "没有匹配的应用"))
            return
        }
        PickerPagination.slice(visible, page, pageSize).forEachIndexed { index, candidate ->
            if (index > 0) areaContainer.addView(InkWidgets.divider(context))
            areaContainer.addView(
                rowFactory(candidate),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowContentHeightPx(compact)),
            )
        }
    }

    /** 刷新页码文案与两端箭头的可用状态；空结果仍显示「第 1/1 页」。 */
    fun update(supplement: String? = null) {
        if (supplement != null) indicatorSupplement = supplement
        val pages = maxOf(1, PickerPagination.pageCount(visible.size, pageSize))
        val pageLabel = "第 ${page + 1}/$pages 页"
        // 空间不足时摘要并入本行，与页码一起显示，避免单独占去一行高度。
        val label = if (indicatorSupplement.isEmpty()) pageLabel else "$indicatorSupplement · $pageLabel"
        // 布局回调会反复触发本函数，文案相等时跳过以免不断请求布局。
        if (indicator.text.toString() != label) indicator.text = label
        val canPrevious = page > 0
        val canNext = page < pages - 1
        previousButton.isEnabled = canPrevious
        nextButton.isEnabled = canNext
        previousButton.alpha = if (canPrevious) 1f else INACTIVE_ALPHA
        nextButton.alpha = if (canNext) 1f else INACTIVE_ALPHA
    }

    /** 翻页：夹紧目标页码后只重建候选区，已选集合与名称草稿保持不变。 */
    private fun turn(delta: Int) {
        val next = PickerPagination.clampPage(page + delta, visible.size, pageSize)
        if (next == page) return
        page = next
        render()
        update()
    }

    /** 候选区当前可用高度：取自身高度与窗口可见底边两者的较小值，键盘弹出时随之减小。 */
    private fun availableHeightPx(): Int {
        val height = areaContainer.height
        if (height <= 0) return 0
        val location = IntArray(2)
        areaContainer.getLocationOnScreen(location)
        val frame = Rect()
        areaContainer.getWindowVisibleDisplayFrame(frame)
        val visibleBottom = minOf(location[1] + height, frame.bottom)
        return maxOf(0, visibleBottom - location[1])
    }

    /** 行内容高度：标签行数与图标取较高者，加上下行内边距；压缩模式只保留两行标签。 */
    private fun rowContentHeightPx(compact: Boolean): Int {
        val bodyLine = ceil(tokens.textBody * LINE_HEIGHT_RATIO).toInt()
        val captionLine = ceil(tokens.textCaption * LINE_HEIGHT_RATIO).toInt()
        val labelBlock = if (compact) bodyLine + captionLine else bodyLine + captionLine * 2
        return maxOf(
            tokens.listRowHeight,
            maxOf(tokens.dp(ICON_SIZE_DP), labelBlock) + tokens.gap * 2,
        )
    }
}
