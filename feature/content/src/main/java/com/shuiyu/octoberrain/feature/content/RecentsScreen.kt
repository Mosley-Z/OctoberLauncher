package com.shuiyu.octoberrain.feature.content

import android.content.Context
import android.graphics.Bitmap
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.TimeUtils
import com.shuiyu.octoberrain.core.common.UsageEntry
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.feature.content.ui.ContentScreen
import com.shuiyu.octoberrain.feature.content.ui.ContentUi

private val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
private val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * 最近任务与使用统计页面。
 *
 * 页面分两个页签：最近任务按时间降序展示最近 24 小时进入前台的应用，
 * 使用统计按今天、本周、本月三个区间展示应用前台时长。列表项支持点击启动，
 * 键盘以方向键移动选中项、左右切换页签与区间、确认键启动选中应用。
 * 未授予使用情况访问权限时仅展示说明与授权入口。
 */
class RecentsScreen : ContentScreen("最近任务") {
    private companion object {
        private const val TAG = "最近任务页"

        /** 两个页签共用的最大条目数。 */
        private const val MAX_ITEMS = 20
    }

    /** 顶部页签。 */
    private enum class Tab(
        val label: String,
    ) {
        RECENTS("最近任务"),
        USAGE("使用统计"),
    }

    private val tabs = listOf(Tab.RECENTS, Tab.USAGE)

    private val repository by lazy { RecentsRepository(context) }

    private var tab = Tab.RECENTS
    private var span = UsageSpan.TODAY

    /** 当前选中行，使用统计页中 0 表示区间选择行。 */
    private var cursor = 0

    private var canRead = false
    private var tasks: List<RecentTask> = emptyList()
    private var summary: UsageSummary? = null

    /** 已加载的应用图标，null 表示加载失败且不再重试。 */
    private val icons = HashMap<String, Bitmap?>()

    /** 参与选中高亮的行视图，下标与 [cursor] 对齐。 */
    private val rowViews = ArrayList<LinearLayout>()

    /**
     * 创建页面视图。
     *
     * 在内容页容器外层再套一层按键拦截容器，让方向键与确认键先交给页面处理，
     * 未消费的事件继续按默认路径分发，返回键仍由宿主统一处理。
     */
    override fun createView(context: Context): View {
        val page = super.createView(context)
        val wrapper = KeyFrameLayout(context) { event -> onKeyEvent(event) }
        wrapper.addView(page, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        wrapper.isFocusable = true
        wrapper.isFocusableInTouchMode = true
        wrapper.post { wrapper.requestFocus() }
        return wrapper
    }

    /** 进入页面时读取权限与数据，然后渲染。 */
    override fun onResume() {
        load { container ->
            reloadData()
            draw(container)
        }
    }

    /** 读取权限状态与两个页签的数据，并预加载图标。 */
    private suspend fun reloadData() {
        canRead = repository.canRead()
        tasks = if (canRead) repository.recentTasks(MAX_ITEMS) else emptyList()
        summary = if (canRead) repository.usage(span, MAX_ITEMS) else null
        cursor = cursor.coerceIn(0, lastCursorIndex())
        preloadIcons()
    }

    /** 按当前页签与选中状态重建正文。 */
    private fun draw(container: LinearLayout) {
        rowViews.clear()
        container.addView(tabBar())
        if (!canRead) {
            container.addView(permissionCard())
            return
        }
        when (tab) {
            Tab.RECENTS -> drawRecents(container)
            Tab.USAGE -> drawUsage(container)
        }
        container.addView(keyHint())
        applySelection()
    }

    /** 顶部两个页签，选中项下方加粗下划线。 */
    private fun tabBar(): View {
        val tokens = tokens
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                setPadding(tokens.gap / 2, 0, tokens.gap / 2, 0)
            }
        for (item in tabs) {
            val selected = item == tab
            val cell =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    isClickable = true
                    setOnClickListener { switchTab(item) }
                }
            cell.addView(
                InkWidgets
                    .textView(
                        context,
                        item.label,
                        tokens.textBody,
                        if (selected) InkColors.INK else InkColors.INK_2,
                    ).apply {
                        gravity = Gravity.CENTER
                    },
            )
            // 选中页签用黑色下划线标示，未选中项以纸白占位保持行高一致
            cell.addView(
                View(context).apply {
                    setBackgroundColor(if (selected) InkColors.INK else InkColors.PAPER)
                    layoutParams =
                        LinearLayout.LayoutParams(MATCH_PARENT, tokens.labelUnderline).apply {
                            topMargin = tokens.dp(4f)
                        }
                },
            )
            row.addView(cell)
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            addView(row)
            addView(InkWidgets.divider(context))
        }
    }

    /** 权限缺失时的说明与授权入口。 */
    private fun permissionCard(): View =
        ContentUi.noticeCard(
            context = context,
            message = "未获得使用情况访问权限，无法读取最近任务与应用使用时长。授权后返回本页即可刷新。",
            actionLabel = "前往授权",
            onAction = { requestUsagePermission() },
        )

    /** 最近任务页签：最近 24 小时的应用列表。 */
    private fun drawRecents(container: LinearLayout) {
        if (tasks.isEmpty()) {
            container.addView(
                ContentUi.emptyCard(
                    context = context,
                    title = "没有最近任务",
                    message = "最近 24 小时内没有其他应用进入前台；系统清理使用记录后也会出现此提示。",
                    actionLabel = "刷新",
                    onAction = { refreshAll() },
                ),
            )
            return
        }
        ContentUi.sectionTitle(container, "最近 24 小时")
        val card = ContentUi.card(context)
        tasks.forEachIndexed { index, task ->
            if (index > 0) card.addView(InkWidgets.divider(context))
            val row = recentsRow(index, task)
            card.addView(row)
            rowViews.add(row)
        }
        container.addView(card)
    }

    /** 最近任务中的单行：图标、名称与相对时间。 */
    private fun recentsRow(
        index: Int,
        task: RecentTask,
    ): LinearLayout {
        val tokens = tokens
        val row = baseRow()
        row.addView(appIcon(task.packageName, tokens.listIcon))
        row.addView(
            InkWidgets.body(context, task.label).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(tokens.gap, 0, tokens.gap / 2, 0)
            },
        )
        row.addView(InkWidgets.caption(context, TimeUtils.relative(task.lastUsedAt)))
        row.setOnClickListener {
            cursor = index
            applySelection()
            launchApp(task.packageName)
        }
        return row
    }

    /** 使用统计页签：区间选择、区间总计与应用时长列表。 */
    private fun drawUsage(container: LinearLayout) {
        val data = summary
        val rangeRow = rangeSelector()
        container.addView(rangeRow)
        rowViews.add(rangeRow)
        container.addView(totalRow(data?.totalMinutes ?: 0))
        val entries = data?.entries.orEmpty()
        if (entries.isEmpty()) {
            container.addView(
                ContentUi.emptyCard(
                    context = context,
                    title = "暂无使用记录",
                    message = "${span.label}还没有可统计的前台使用记录。",
                    actionLabel = "刷新",
                    onAction = { refreshAll() },
                ),
            )
            return
        }
        ContentUi.sectionTitle(container, "${span.label}应用时长")
        val card = ContentUi.card(context)
        entries.forEachIndexed { index, entry ->
            if (index > 0) card.addView(InkWidgets.divider(context))
            val row = usageRow(index + 1, entry)
            card.addView(row)
            rowViews.add(row)
        }
        container.addView(card)
    }

    /** 今天、本周、本月三个区间按钮。 */
    private fun rangeSelector(): LinearLayout {
        val tokens = tokens
        val spans = UsageSpan.values()
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                setPadding(tokens.gap / 2, tokens.gap / 2, tokens.gap / 2, tokens.gap / 2)
                isClickable = true
                setOnClickListener {
                    cursor = 0
                    applySelection()
                }
            }
        for (item in spans) {
            val selected = item == span
            row.addView(
                InkWidgets
                    .textView(
                        context,
                        item.label,
                        tokens.textBody,
                        if (selected) InkColors.PAPER else InkColors.INK,
                    ).apply {
                        gravity = Gravity.CENTER
                        layoutParams =
                            LinearLayout.LayoutParams(0, tokens.buttonHeight, 1f).apply {
                                if (item != spans.first()) marginStart = tokens.gap / 2
                            }
                        background =
                            if (selected) {
                                InkWidgets.background(tokens.radiusSmall, InkColors.INK)
                            } else {
                                InkWidgets.background(
                                    tokens.radiusSmall,
                                    InkColors.PAPER,
                                    tokens.stroke,
                                    InkColors.INK_4,
                                )
                            }
                        isClickable = true
                        setOnClickListener {
                            cursor = 0
                            selectSpan(item)
                        }
                    },
            )
        }
        return row
    }

    /** 区间内全部应用的前台时长合计。 */
    private fun totalRow(minutes: Int): View {
        val tokens = tokens
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams =
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                    topMargin = tokens.gap
                }
            setPadding(tokens.gap / 2, 0, tokens.gap / 2, 0)
            addView(
                InkWidgets.caption(context, "总计").apply {
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                },
            )
            addView(InkWidgets.body(context, TimeUtils.formatDuration(minutes)))
        }
    }

    /** 使用统计中的单行：图标、名称与区间时长。 */
    private fun usageRow(
        cursorIndex: Int,
        entry: UsageEntry,
    ): LinearLayout {
        val tokens = tokens
        val row = baseRow()
        row.addView(appIcon(entry.packageName, tokens.listIcon))
        row.addView(
            InkWidgets.body(context, entry.label).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(tokens.gap, 0, tokens.gap / 2, 0)
            },
        )
        row.addView(InkWidgets.caption(context, TimeUtils.formatDuration(entry.minutes)))
        row.setOnClickListener {
            cursor = cursorIndex
            applySelection()
            launchApp(entry.packageName)
        }
        return row
    }

    /** 列表行的公共样式。 */
    private fun baseRow(): LinearLayout {
        val tokens = tokens
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = tokens.listRowHeight
            setPadding(tokens.gap / 2, tokens.gap / 4, tokens.gap / 2, tokens.gap / 4)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            isClickable = true
        }
    }

    /** 应用图标，未加载或加载失败时以灰色圆点占位。 */
    private fun appIcon(
        packageName: String,
        sidePx: Int,
    ): View {
        val tokens = tokens
        val frame =
            FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(sidePx, sidePx)
            }
        val bitmap = icons[packageName]
        if (bitmap != null) {
            frame.addView(
                ImageView(context).apply {
                    setImageBitmap(bitmap)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                },
            )
        } else {
            frame.addView(
                InkWidgets.textView(context, "·", tokens.textCaption, InkColors.INK_3).apply {
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                },
            )
        }
        return frame
    }

    /** 底部按键提示。 */
    private fun keyHint(): View {
        val tokens = tokens
        return InkWidgets.caption(context, "↑↓ 选择　←→ 切换页签与区间　↵ 打开").apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            setPadding(tokens.gap / 2, tokens.gapLarge, tokens.gap / 2, 0)
        }
    }

    /**
     * 更新选中行背景。
     *
     * 区间选择行被选中时以黑色描边标示，其余行为浅灰底色；
     * 背景更新后请求一次局部重绘，墨水屏上只刷新变化区域。
     */
    private fun applySelection(notify: Boolean = true) {
        val tokens = tokens
        rowViews.forEachIndexed { index, view ->
            view.background =
                when {
                    index != cursor -> null
                    tab == Tab.USAGE && index == 0 ->
                        InkWidgets.background(
                            tokens.radiusSmall,
                            InkColors.PAPER,
                            tokens.stroke,
                            InkColors.INK,
                        )
                    else -> InkWidgets.background(tokens.radiusSmall, InkColors.INK_4)
                }
        }
        if (notify) {
            rowViews.getOrNull(cursor)?.let { selected ->
                // 布局完成后再滚动，键盘上下移动时让选中行始终可见
                selected.post { scrollRowIntoView(selected) }
            }
            repaint()
        }
    }

    /** 把选中行滚动到可视区域内，行尚未完成布局时不处理。 */
    private fun scrollRowIntoView(view: View) {
        if (view.height <= 0) return
        val scroll = content.parent as? ScrollView ?: return
        val rowTop = topWithin(view, content)
        val rowBottom = rowTop + view.height
        val viewportTop = scroll.scrollY
        val viewportBottom = viewportTop + scroll.height - scroll.paddingTop - scroll.paddingBottom
        if (viewportBottom <= viewportTop) return
        when {
            rowTop < viewportTop -> scroll.scrollTo(0, rowTop)
            rowBottom > viewportBottom -> scroll.scrollTo(0, rowBottom - (viewportBottom - viewportTop))
        }
    }

    /** 计算视图相对某一祖先的纵向偏移。 */
    private fun topWithin(
        view: View,
        ancestor: View,
    ): Int {
        var top = 0
        var current: View? = view
        while (current != null && current !== ancestor) {
            top += current.top
            current = current.parent as? View
        }
        return top
    }

    /** 当前页签允许的最大选中下标。 */
    private fun lastCursorIndex(): Int =
        when (tab) {
            Tab.RECENTS -> (tasks.size - 1).coerceAtLeast(0)
            Tab.USAGE -> summary?.entries?.size ?: 0
        }

    /** 使用统计页中是否停留在区间选择行。 */
    private fun isRangeFocused(): Boolean = tab == Tab.USAGE && cursor == 0

    /** 处理方向键与确认键，返回是否已消费。 */
    private fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> moveCursor(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> moveCursor(1)
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isRangeFocused()) shiftSpan(-1) else switchTabStep(-1)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isRangeFocused()) shiftSpan(1) else switchTabStep(1)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            -> activateSelected()
            else -> false
        }
    }

    /** 上下移动选中行，越界时停在首尾。 */
    private fun moveCursor(delta: Int): Boolean {
        if (!canRead) return true
        val next = (cursor + delta).coerceIn(0, lastCursorIndex())
        if (next != cursor) {
            cursor = next
            applySelection()
        }
        return true
    }

    /** 左右切换页签。 */
    private fun switchTabStep(delta: Int) {
        val index = tabs.indexOf(tab)
        switchTab(tabs[(index + delta + tabs.size) % tabs.size])
    }

    /** 切换页签并重建正文。 */
    private fun switchTab(next: Tab) {
        if (next == tab) return
        tab = next
        cursor = 0
        runTask {
            preloadIcons()
            rebuild()
        }
    }

    /** 切换区间并读取对应统计数据。 */
    private fun shiftSpan(delta: Int) {
        val spans = UsageSpan.values()
        val index = spans.indexOf(span)
        selectSpan(spans[(index + delta + spans.size) % spans.size])
    }

    /** 切换到指定区间并重建正文。 */
    private fun selectSpan(next: UsageSpan) {
        if (next == span) return
        span = next
        cursor = 0
        runTask {
            summary = repository.usage(span, MAX_ITEMS)
            preloadIcons()
            rebuild()
        }
    }

    /** 确认键：最近任务与统计列表启动选中应用，区间行切换下一个区间。 */
    private fun activateSelected(): Boolean {
        if (!canRead) {
            // 未授权时确认键直接打开使用情况访问设置，只有键盘的设备也能完成授权
            requestUsagePermission()
            return true
        }
        when (tab) {
            Tab.RECENTS -> {
                val task = tasks.getOrNull(cursor) ?: return false
                launchApp(task.packageName)
            }
            Tab.USAGE -> {
                if (cursor == 0) {
                    shiftSpan(1)
                } else {
                    val entry = summary?.entries?.getOrNull(cursor - 1) ?: return false
                    launchApp(entry.packageName)
                }
            }
        }
        return true
    }

    /** 清空正文后按当前状态重新渲染。 */
    private fun rebuild() {
        draw(clearContent())
    }

    /** 预加载当前页签所需的应用图标。 */
    private suspend fun preloadIcons() {
        val packages =
            when (tab) {
                Tab.RECENTS -> tasks.map { it.packageName }
                Tab.USAGE -> summary?.entries?.map { it.packageName } ?: emptyList()
            }
        val side = tokens.listIcon
        for (packageName in packages) {
            if (icons.containsKey(packageName)) continue
            icons[packageName] =
                runCatching {
                    AppServices.catalog.loadIcon(packageName, side)
                }.getOrNull()
        }
    }

    /** 启动应用，失败时提示用户。 */
    private fun launchApp(packageName: String) {
        runCatching { AppServices.router.openApp(packageName) }
            .onFailure {
                InkLog.w(TAG, "启动应用失败：${it.message}")
                toast("无法启动该应用")
            }
    }

    /** 跳转系统使用情况访问设置页。 */
    private fun requestUsagePermission() {
        runCatching { AppServices.permissions.openUsageSettings() }
            .onFailure {
                InkLog.w(TAG, "打开使用情况访问设置失败：${it.message}")
                toast("请到系统设置中授予使用情况访问权限")
            }
    }

    /** 清空缓存并重新读取数据。 */
    private fun refreshAll() {
        repository.invalidate()
        onResume()
    }
}

/**
 * 接收方向键与确认键的页面外层容器。
 *
 * 正文中的行视图只设点击不设焦点，按键事件先在容器处拦截并交给页面处理；
 * 容器未消费的事件继续按默认路径分发，返回键仍由宿主 Activity 处理。
 */
private class KeyFrameLayout(
    context: Context,
    private val onKey: (KeyEvent) -> Boolean,
) : FrameLayout(context) {
    private companion object {
        /** 由页面统一处理的按键，抬起事件一并消费，避免系统提示按键序列不完整。 */
        private val HANDLED_KEYS =
            setOf(
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
            )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP && event.keyCode in HANDLED_KEYS) return true
        if (onKey(event)) return true
        return super.dispatchKeyEvent(event)
    }
}
