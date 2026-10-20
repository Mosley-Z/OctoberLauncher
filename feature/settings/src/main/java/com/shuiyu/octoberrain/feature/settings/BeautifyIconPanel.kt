package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.DesktopLayoutSnapshot
import com.shuiyu.octoberrain.core.common.LauncherEntry
import com.shuiyu.octoberrain.core.common.SortMode
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkWidgets
import kotlinx.coroutines.launch
import java.io.File

// 美化模式左栏：图标与排序。
//
// 左栏按当前页列出应用，选中一行后可上移、下移或移到本页首末位，
// 页首上移与页尾下移会跨页移动；点击右栏缩略图即为此行换图标。
// 行视图被保留在映射中，页内交换直接替换子视图顺序，滚动位置不丢失。

/** 行内「已换」标记的视图 tag，用于避免重复添加。 */
private const val OVERRIDE_MARK_TAG = "override-mark"

/** 左栏容器：页内应用列表、上移下移与应用排序。 */
internal fun BeautifyScreen.buildAppColumn(
    context: Context,
    entries: List<LauncherEntry>,
    snapshot: DesktopLayoutSnapshot,
): View {
    val tokens = DesignRuntime.tokens
    val column =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background =
                InkWidgets.background(
                    tokens.radius,
                    InkColors.PAPER,
                    tokens.stroke,
                    InkColors.INK,
                )
            setPadding(tokens.gap / 2, tokens.gap / 2, tokens.gap / 2, tokens.gap / 2)
        }

    // 页码行并入左栏头部：两端为切页箭头，首末页时对应箭头置灰。
    val header =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isBaselineAligned = false
            setPadding(tokens.gap / 2, 0, tokens.gap / 2, tokens.gap / 2)
        }
    val pageCount = workingPages.size.coerceAtLeast(1)
    header.addView(
        PageArrowView(
            context,
            forward = false,
            enabled = currentPage > 0,
            onClick = { switchPage(currentPage - 1) },
        ),
        LinearLayout.LayoutParams(tokens.dp(24f), tokens.dp(24f)),
    )
    header.addView(
        InkWidgets
            .caption(
                context,
                "第 ${currentPage + 1} 页 · ${entries.size} 个应用 · ${snapshot.columns} 列",
            ).apply {
                gravity = Gravity.CENTER
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                setPadding(tokens.gap / 2, 0, tokens.gap / 2, 0)
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f,
                    )
            },
    )
    header.addView(
        PageArrowView(
            context,
            forward = true,
            enabled = currentPage < pageCount - 1,
            onClick = { switchPage(currentPage + 1) },
        ),
        LinearLayout.LayoutParams(tokens.dp(24f), tokens.dp(24f)),
    )
    column.addView(header)

    val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    // 记录行容器与行视图，页内重排时直接交换子视图，避免整体重建丢失滚动位置。
    appList = list
    appRows.clear()
    if (entries.isEmpty()) {
        list.addView(
            InkWidgets.caption(context, "本页尚未放入应用").apply {
                setPadding(tokens.gap / 2, tokens.gap / 2, 0, 0)
            },
        )
    } else {
        val overrides = overrides()
        entries.forEach { entry ->
            val row = buildAppRow(context, entry, overrides.containsKey(entry.packageName))
            appRows[entry.key] = row
            list.addView(row)
        }
    }
    column.addView(
        ScrollView(context).apply {
            isFillViewport = false
            addView(
                list,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        },
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
    )

    val footer =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            setPadding(0, tokens.gap / 2, 0, 0)
        }
    footer.addView(ArrowButtonView(context, ArrowKind.TO_TOP) { moveToTop() }, thirdButtonParams(1f))
    footer.addView(gapView(context), thirdButtonParams(0f).apply { width = tokens.gap / 3 })
    footer.addView(ArrowButtonView(context, ArrowKind.UP) { moveSelection(-1) }, thirdButtonParams(1f))
    footer.addView(gapView(context), thirdButtonParams(0f).apply { width = tokens.gap / 3 })
    footer.addView(ArrowButtonView(context, ArrowKind.DOWN) { moveSelection(1) }, thirdButtonParams(1f))
    footer.addView(gapView(context), thirdButtonParams(0f).apply { width = tokens.gap / 3 })
    footer.addView(
        ArrowButtonView(context, ArrowKind.TO_BOTTOM) { moveToBottom() },
        thirdButtonParams(1f),
    )
    column.addView(
        footer,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            tokens.buttonHeight,
        ),
    )
    return column
}

/**
 * 上移或下移选中条目。
 *
 * 页内移动直接交换行视图；位于页首时上移跨到上一页末位，
 * 位于页尾时下移跨到下一页首位。目标页已满时改为边界交换，
 * 首页顶端与末页底端保持不动。
 */
internal fun BeautifyScreen.moveSelection(delta: Int) {
    val key =
        selectedKey ?: run {
            showStatus("请先选择左侧应用")
            return
        }
    val page = workingPages.getOrNull(currentPage) ?: return
    val index = page.indexOf(key)
    if (index < 0) return
    val snapshot = snapshot(AppServices.config.state.value)
    if (delta < 0) {
        if (index > 0) {
            swapInPage(index, index - 1)
            return
        }
        val previous = workingPages.getOrNull(currentPage - 1)
        if (previous == null) {
            showStatus("已在本页首位")
            return
        }
        if (previous.size >= pageCapacity(snapshot, currentPage - 1)) {
            // 上一页已满：与上一页末位交换，被挤出的条目落到本页原位置。
            val displaced = previous.removeAt(previous.lastIndex)
            page[index] = displaced
            previous.add(key)
            currentPage -= 1
            pagesDirty = true
            refreshLeftNow()
            showStatus("已与第 ${currentPage + 1} 页末位交换")
            return
        }
        page.removeAt(index)
        previous.add(key)
        currentPage -= 1
        pagesDirty = true
        refreshLeftNow()
        showStatus("已移动到第 ${currentPage + 1} 页末位")
        return
    }
    if (index < page.lastIndex) {
        swapInPage(index, index + 1)
        return
    }
    val next = workingPages.getOrNull(currentPage + 1)
    if (next == null) {
        showStatus("已在本页末位")
        return
    }
    if (next.size >= pageCapacity(snapshot, currentPage + 1)) {
        // 下一页已满：与下一页首位交换，被挤出的条目回到本页原位置。
        val displaced = next.removeAt(0)
        page[index] = displaced
        next.add(0, key)
        currentPage += 1
        pagesDirty = true
        refreshLeftNow()
        showStatus("已与第 ${currentPage + 1} 页首位交换")
        return
    }
    page.removeAt(index)
    next.add(0, key)
    currentPage += 1
    pagesDirty = true
    refreshLeftNow()
    showStatus("已移动到第 ${currentPage + 1} 页首位")
}

/** 页内交换两行，直接交换子视图以保持滚动位置。 */
private fun BeautifyScreen.swapInPage(
    from: Int,
    to: Int,
) {
    val page = workingPages.getOrNull(currentPage) ?: return
    if (from !in page.indices || to !in page.indices) return
    val key = page[from]
    val otherKey = page[to]
    page[from] = otherKey
    page[to] = key
    pagesDirty = true
    val list = appList
    val rowKey = appRows[key]
    val rowOther = appRows[otherKey]
    if (list == null || rowKey == null || rowOther == null) {
        refreshLeft()
        return
    }
    val first = list.indexOfChild(rowKey)
    val second = list.indexOfChild(rowOther)
    if (first < 0 || second < 0) {
        refreshLeft()
        return
    }
    // 两行同时摘除后按目标顺序插回：选中行背景与「已换」标记随行移动。
    val low = minOf(first, second)
    val high = maxOf(first, second)
    list.removeViewAt(high)
    list.removeViewAt(low)
    if (from > to) {
        list.addView(rowKey, low)
        list.addView(rowOther, high)
    } else {
        list.addView(rowOther, low)
        list.addView(rowKey, high)
    }
    SettingsUi.refresh()
}

/** 选中条目移到本页首位，不跨页。 */
private fun BeautifyScreen.moveToTop() = moveToEdge(first = true)

/** 选中条目移到本页末位，不跨页。 */
private fun BeautifyScreen.moveToBottom() = moveToEdge(first = false)

/** 把选中条目移动到本页首端或末端，并同步内存列表与行视图。 */
private fun BeautifyScreen.moveToEdge(first: Boolean) {
    val key =
        selectedKey ?: run {
            showStatus("请先选择左侧应用")
            return
        }
    val page = workingPages.getOrNull(currentPage) ?: return
    val index = page.indexOf(key)
    if (index < 0) return
    val target = if (first) 0 else page.lastIndex
    if (index == target) {
        showStatus(if (first) "已在本页首位" else "已在本页末位")
        return
    }
    page.removeAt(index)
    if (first) page.add(0, key) else page.add(key)
    pagesDirty = true
    val list = appList
    val row = appRows[key]
    if (list == null || row == null) {
        refreshLeft()
        return
    }
    list.removeView(row)
    list.addView(row, if (first) 0 else list.childCount)
    SettingsUi.refresh()
}

/** 点击左栏行只切换选中背景，不重建列表以保持滚动位置。 */
internal fun BeautifyScreen.selectRow(key: String) {
    if (selectedKey == key) return
    selectedKey?.let { previous ->
        appRows[previous]?.setBackgroundColor(0)
    }
    appRows[key]?.setBackgroundColor(InkColors.INK_4)
    selectedKey = key
}

/** 把内存中的完整页顺序写入配置，并弹出短提示确认已保存。 */
internal fun BeautifyScreen.saveOrder() {
    val context = root?.context ?: return
    runCatching {
        loadPages(snapshot(AppServices.config.state.value))
        val merged = workingPages.flatten()
        if (merged.isEmpty()) {
            showStatus("没有可保存的顺序")
            return
        }
        SettingsUi.edit { current ->
            current.copy(
                desktop =
                    current.desktop.copy(
                        order = merged,
                        sortMode = SortMode.MANUAL,
                    ),
            )
        }
        pagesDirty = false
        SettingsUi.refresh()
        showSavedNotice(context)
    }.onFailure {
        showStatus("保存顺序失败，请稍后重试")
    }
}

/** 把图片设置为左栏选中应用的图标。 */
internal fun BeautifyScreen.applyImage(cell: ImageCell) {
    val context = root?.context ?: return
    val entry =
        selectedEntry() ?: run {
            SettingsUi.toast(context, "请先选择左侧应用")
            return
        }
    val packageName =
        entry.packageName ?: run {
            SettingsUi.toast(context, "该条目不是应用，无法更换图标")
            return
        }
    val file = File(cell.path)
    if (!file.exists()) {
        SettingsUi.toast(context, "图片文件已不存在")
        return
    }
    scope.launch {
        val ok =
            runCatching {
                AppServices.iconOverrides.setOverride(packageName, Uri.fromFile(file))
            }.getOrDefault(false)
        if (root == null) return@launch
        if (!ok) {
            showStatus("图标设置失败，请更换图片")
            return@launch
        }
        selectedImagePath = file.path
        selectedKey = entry.key
        // 只刷新当前行的图标与「已换」标记，不重建整页、不请求整屏刷新。
        refreshRowIcon(entry)
        showStatus("已为 ${entry.label} 更换图标")
        SettingsUi.refresh()
    }
}

/**
 * 就地刷新左栏某一行的图标与「已换」标记。
 *
 * 重新读取覆盖图并写回行首的 ImageView；缺失标记时在名称右侧补一个，
 * 避免因整栏重建导致滚动位置丢失。
 */
private fun BeautifyScreen.refreshRowIcon(entry: LauncherEntry) {
    val row = appRows[entry.key] as? LinearLayout ?: return
    val packageName = entry.packageName ?: return
    val tokens = DesignRuntime.tokens
    val size = tokens.dp(32f)
    val icon = row.getChildAt(0) as? ImageView ?: return
    loadAppIcon(packageName, icon, size)
    if (row.findViewWithTag<View>(OVERRIDE_MARK_TAG) == null) {
        row.addView(
            InkWidgets.textView(row.context, "已换", tokens.textCaption, InkColors.INK_2).apply {
                tag = OVERRIDE_MARK_TAG
                setPadding(tokens.gap / 2, 0, 0, 0)
            },
        )
    }
}

/** 当前选中的桌面条目。 */
private fun BeautifyScreen.selectedEntry(): LauncherEntry? {
    val config = AppServices.config.state.value
    val pages = snapshot(config).pages.ifEmpty { listOf(emptyList()) }
    return pages.getOrNull(currentPage)?.firstOrNull { it.key == selectedKey }
}

/** 当前图标覆盖映射，读取失败时返回空映射。 */
internal fun BeautifyScreen.overrides(): Map<String, String> =
    runCatching { AppServices.iconOverrides.overrides() }.getOrDefault(emptyMap())
