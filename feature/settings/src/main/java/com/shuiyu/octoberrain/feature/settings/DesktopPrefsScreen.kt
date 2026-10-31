package com.shuiyu.octoberrain.feature.settings

import android.os.SystemClock
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.DesktopKind
import com.shuiyu.octoberrain.core.common.GridAlign
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.SortMode
import com.shuiyu.octoberrain.core.common.TrayConfig
import com.shuiyu.octoberrain.core.common.effectiveWidgetId
import com.shuiyu.octoberrain.core.common.withFirstGrid
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.design.InkWidgets
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 桌面偏好设置页面。
 *
 * 页面按模式方向、首页布局、页码、交互分组排列，每行保持标题与右侧控件；
 * 连续步进以 [STEP_DEBOUNCE_MS] 合并写入，避免每次点击都重建整页。
 */
class DesktopPrefsScreen : SettingsBaseScreen("桌面与布局") {
    override fun beforeDimensionsRebuild() {
        flushPendingDesktop()
    }

    /** 尚未写入的桌面配置，连续步进在该对象上累加。 */
    private var pendingDesktop: DesktopConfig? = null

    /** 防抖写入任务。 */
    private var pendingJob: Job? = null

    /** 隐藏应用数量行，管理子页写入后同步。 */
    private var hiddenAppsRow: SettingsRow? = null

    /** 离开页面时立即落盘，避免防抖窗口内的改动丢失。 */
    override fun onPause() {
        flushPendingDesktop()
    }

    override fun onDestroy() {
        flushPendingDesktop()
        super.onDestroy()
    }

    /**
     * 构建页面内容。
     *
     * 参数 page 为承载内容的页面；config 为当前配置快照。
     */
    override fun build(
        page: InkPage,
        config: LauncherConfig,
    ) {
        val startedAt = SystemClock.elapsedRealtime()
        SettingsUi.beginRowCount(page)
        DesktopModeSettings.entry(page, beforeChange = { flushPendingDesktop() }, onModeChanged = { rebuildLater() })
        buildHomeLayout(page, config.desktop)
        buildIconAndLabel(page)
        buildInteraction(page, config.desktop)
        InkWidgets
            .button(page.context, "恢复本页默认", filled = true) {
                confirmResetLayout()
            }.also { page.content.addView(it) }
        // InkLog 当前只提供 d/w/e，构建耗时与行数用调试级输出。
        InkLog.d(
            TAG,
            "桌面偏好页构建完成：${SystemClock.elapsedRealtime() - startedAt}ms，${SettingsUi.rowCount()} 行",
        )
    }

    /** 只显示当前形态的容量控件，切换前先落盘连续操作。 */
    private fun buildHomeLayout(
        page: InkPage,
        desktop: DesktopConfig,
    ) {
        val section = SettingsUi.section(page, "桌面布局", "列 × 行，自动按可用空间计算")
        section.choose(
            label = "桌面形态",
            options = KIND_OPTIONS,
            selected = { if (SettingsUi.settings().desktop.kind == DesktopKind.LIST) 1 else 0 },
        ) { index ->
            flushPendingDesktop()
            updateDesktop { it.copy(kind = if (index == 1) DesktopKind.LIST else DesktopKind.GRID) }
            rebuildLater()
        }
        if (desktop.kind == DesktopKind.LIST) {
            buildListLayout(page, desktop)
        } else {
            buildGridLayout(page, desktop)
        }
    }

    private fun buildGridLayout(
        page: InkPage,
        desktop: DesktopConfig,
    ) {
        val section = SettingsUi.section(page, "宫格容量", "首页与其他页分别保存；自为自动")
        for (landscape in listOf(false, true)) {
            section.gridSize(
                label = if (landscape) "首页 横屏" else "首页 竖屏",
                initial =
                    (if (landscape) desktop.landscapeFirstPageColumns else desktop.portraitFirstPageColumns)
                        .let { it ?: desktop.firstPageColumns } to
                        (
                            (if (landscape) desktop.landscapeFirstPageRows else desktop.portraitFirstPageRows)
                                ?: desktop.firstPageRows
                        ),
                fit = { columns, rows -> fitFirstDirection(landscape, columns, rows) },
            ) { columns, rows -> scheduleDesktop { it.withFirstGrid(landscape, columns, rows) } }
        }
        section.gridSize(
            label = "其他页 默认",
            initial = desktop.columns to desktop.rows,
            fit = { columns, rows -> fitGrid(false, columns, rows) },
        ) { columns, rows -> scheduleDesktop { it.copy(columns = columns, rows = rows) } }
        // 其他页按方向分别保存容量，转向后各自生效；0 沿用上面的默认值。
        section.gridSize(
            label = "其他页 横屏",
            initial = desktop.landscapeColumns to desktop.landscapeRows,
            fit = { columns, rows -> fitDirection(true, columns, rows) },
        ) { columns, rows ->
            scheduleDesktop { it.copy(landscapeColumns = columns, landscapeRows = rows) }
        }
        section.gridSize(
            label = "其他页 竖屏",
            initial = desktop.portraitColumns to desktop.portraitRows,
            fit = { columns, rows -> fitDirection(false, columns, rows) },
        ) { columns, rows ->
            scheduleDesktop { it.copy(portraitColumns = columns, portraitRows = rows) }
        }
    }

    private fun fitFirstDirection(
        landscape: Boolean,
        columns: Int,
        rows: Int,
    ): Pair<Int, Int> {
        if (landscape != isLandscape()) return columns to rows
        val desktop = (pendingDesktop ?: SettingsUi.settings().desktop).withFirstGrid(landscape, columns, rows)
        val snapshot = previewLayout(desktop)
        return (if (columns == 0) 0 else snapshot.firstPageColumns) to
            (if (rows == 0) 0 else snapshot.firstPageRows)
    }

    private fun fitGrid(
        first: Boolean,
        columns: Int,
        rows: Int,
    ): Pair<Int, Int> {
        val base = pendingDesktop ?: SettingsUi.settings().desktop
        if (first && base.effectiveWidgetId.isBlank()) return columns to rows
        val target =
            if (first) {
                base.copy(firstPageColumns = columns, firstPageRows = rows)
            } else {
                base.copy(
                    columns = columns,
                    rows = rows,
                    landscapeColumns = 0,
                    landscapeRows = 0,
                    portraitColumns = 0,
                    portraitRows = 0,
                )
            }
        val snapshot = previewLayout(target)
        val actualColumns = if (first) snapshot.firstPageColumns else snapshot.columns
        val actualRows = if (first) snapshot.firstPageRows else snapshot.rowsPerPage
        return (if (columns == 0) 0 else actualColumns) to (if (rows == 0) 0 else actualRows)
    }

    /**
     * 方向容量回读：只在与当前屏幕方向一致时用预览快照收敛，另一方向原样保留。
     *
     * 参数 landscape 表示目标方向；columns 与 rows 为请求的行列，0 表示自动。
     * 返回按当前视口换算后的实际值，避免用户设置的格数被悄悄放大。
     */
    private fun fitDirection(
        landscape: Boolean,
        columns: Int,
        rows: Int,
    ): Pair<Int, Int> {
        val current = isLandscape()
        if (current != landscape) return columns to rows
        val base = pendingDesktop ?: SettingsUi.settings().desktop
        val target =
            if (landscape) {
                base.copy(landscapeColumns = columns, landscapeRows = rows)
            } else {
                base.copy(portraitColumns = columns, portraitRows = rows)
            }
        val snapshot = previewLayout(target)
        return (if (columns == 0) 0 else snapshot.columns) to (if (rows == 0) 0 else snapshot.rowsPerPage)
    }

    /** 当前屏幕是否横屏，与版面换算使用同一判据。 */
    private fun isLandscape(): Boolean {
        val context = contextOrNull() ?: return false
        return context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    }

    private fun buildListLayout(
        page: InkPage,
        desktop: DesktopConfig,
    ) {
        val section = SettingsUi.section(page, "列表布局", "数量与左右留白")
        val values = (0..12).toList()
        val labels = values.map { if (it == 0) "自动" else "$it 条" }

        fun countRow(first: Boolean) {
            section.stepper(
                label = if (first) "首页条数" else "其他页条数",
                values = labels,
                selected = { if (first) desktop.listFirstRows else desktop.listRows },
                normalize = { value -> fitListCount(first, value) },
            ) { value ->
                scheduleDesktop { if (first) it.copy(listFirstRows = value) else it.copy(listRows = value) }
            }
        }
        countRow(true)
        countRow(false)
        section.choose("文字对齐", ALIGN_OPTIONS, { ALIGN_VALUES.indexOf(SettingsUi.settings().desktop.listAlign) }) {
            updateDesktop { old -> old.copy(listAlign = ALIGN_VALUES[it]) }
        }
        section.choose("左右边距", listOf("窄", "标准", "宽"), { listOf(12, 20, 28).indexOf(desktop.listMarginDp) }) {
            updateDesktop { old -> old.copy(listMarginDp = listOf(12, 20, 28)[it]) }
        }
    }

    private fun fitListCount(
        first: Boolean,
        value: Int,
    ): Int {
        if (value == 0) return 0
        val base = pendingDesktop ?: SettingsUi.settings().desktop
        val target = if (first) base.copy(listFirstRows = value) else base.copy(listRows = value)
        val snapshot = previewLayout(target)
        return if (first) snapshot.firstPageRows else snapshot.rowsPerPage
    }

    private fun previewLayout(desktop: DesktopConfig): com.shuiyu.octoberrain.core.common.DesktopLayoutSnapshot =
        if (AppServices.hasLayoutProvider()) {
            AppServices.layoutProvider.preview(SettingsUi.settings().copy(desktop = desktop))
        } else {
            DesktopPagination.fallbackSnapshot(SettingsUi.settings().copy(desktop = desktop))
        }

    /** 页码显示属于布局，图标和名称样式统一由外观页管理。 */
    private fun buildIconAndLabel(page: InkPage) {
        SettingsUi.section(page, "页面指示").toggle(
            label = "页码指示",
            get = { SettingsUi.settings().desktop.showPageIndicator },
        ) { enabled -> updateDesktop { it.copy(showPageIndicator = enabled) } }
    }

    /** 交互：排序、长按延迟、滑动阈值与音量键翻页。 */
    private fun buildInteraction(
        page: InkPage,
        desktop: DesktopConfig,
    ) {
        val section = SettingsUi.section(page, "交互", "排序与长按手势")
        hiddenAppsRow =
            section.action(
                label = "管理隐藏应用",
                value = "${desktop.hiddenEntryCount()} 个",
            ) {
                showHiddenApps()
            }
        section.choose(
            label = "排序方式",
            options = SORT_OPTIONS,
            selected = { SORT_MODES.indexOf(SettingsUi.settings().desktop.sortMode).coerceAtLeast(0) },
        ) { index ->
            updateDesktop { it.copy(sortMode = SORT_MODES[index]) }
        }
        section.stepper(
            label = "长按菜单延迟",
            values = MENU_PRESS_OPTIONS,
            selected = { selectedIndex(MENU_PRESS_VALUES, desktop.longPressMenuMs.toFloat()) },
        ) { index ->
            scheduleDesktop { it.copy(longPressMenuMs = MENU_PRESS_VALUES[index].toInt()) }
        }
        section.stepper(
            label = "整理模式延迟",
            values = EDIT_PRESS_OPTIONS,
            selected = { selectedIndex(EDIT_PRESS_VALUES, desktop.editHoldMs.toFloat()) },
        ) { index ->
            scheduleDesktop { it.copy(editHoldMs = EDIT_PRESS_VALUES[index].toInt()) }
        }
        section.stepper(
            label = "滑动灵敏度",
            values = SWIPE_OPTIONS,
            selected = { selectedIndex(SWIPE_VALUES, desktop.swipeMinDp.toFloat()) },
        ) { index ->
            scheduleDesktop { it.copy(swipeMinDp = SWIPE_VALUES[index].toInt()) }
        }
        section.toggle(
            label = "音量键翻页",
            get = { SettingsUi.settings().tray.volumeKeyFlip },
            hint = "桌面状态下以音量键翻页",
        ) { enabled ->
            updateTray { it.copy(volumeKeyFlip = enabled) }
        }
    }

    /** 写入桌面配置并请求局部刷新，用于一次性改动。 */
    private fun updateDesktop(transform: (DesktopConfig) -> DesktopConfig) {
        SettingsUi.edit { config -> config.copy(desktop = transform(config.desktop)) }
        SettingsUi.refresh()
    }

    /** 写入托盘配置并请求局部刷新。 */
    private fun updateTray(transform: (TrayConfig) -> TrayConfig) {
        SettingsUi.edit { config -> config.copy(tray = transform(config.tray)) }
        SettingsUi.refresh()
    }

    /**
     * 合并连续步进的配置写入。
     *
     * 参数 transform 在待写入配置上累加；[STEP_DEBOUNCE_MS] 内的多次调用只落盘一次。
     */
    private fun scheduleDesktop(transform: (DesktopConfig) -> DesktopConfig) {
        val base = pendingDesktop ?: SettingsUi.settings().desktop
        pendingDesktop = transform(base)
        pendingJob?.cancel()
        pendingJob =
            scope.launch {
                delay(STEP_DEBOUNCE_MS)
                commitPendingDesktop()
            }
    }

    /** 提交待写入的桌面配置，与当前配置相同时跳过。 */
    private fun commitPendingDesktop() {
        pendingJob = null
        val target = pendingDesktop ?: return
        pendingDesktop = null
        if (target != SettingsUi.settings().desktop) {
            SettingsUi.edit { it.copy(desktop = target) }
            SettingsUi.refresh()
        }
    }

    /** 立即提交待写入配置并取消防抖任务。 */
    private fun flushPendingDesktop() {
        pendingJob?.cancel()
        pendingJob = null
        commitPendingDesktop()
    }

    /** 展示隐藏应用管理子页。 */
    private fun showHiddenApps() {
        val context = contextOrNull() ?: return
        SettingsUi.overlayPage(context, "隐藏应用") { dialogPage ->
            buildHiddenList(dialogPage)
        }
    }

    /**
     * 构建隐藏应用列表。
     *
     * 参数 page 为子页内容容器；开关切换后由行控件就地更新显示。
     */
    private fun buildHiddenList(page: InkPage) {
        buildFileVisibility(page)
        val apps =
            AppServices.catalog.apps.value
                .sortedBy { it.label.lowercase() }
        if (apps.isEmpty()) {
            SettingsUi.note(page, "应用列表尚未加载完成。")
            return
        }
        val section = SettingsUi.section(page, "安装应用", "开为显示，关闭后该应用从桌面隐藏")
        apps.forEach { app ->
            section.toggle(
                label = app.label,
                get = {
                    !SettingsUi
                        .settings()
                        .desktop.hiddenPackages
                        .contains(app.packageName)
                },
            ) { visible ->
                updateDesktop { desktop ->
                    val packages =
                        if (visible) {
                            desktop.hiddenPackages - app.packageName
                        } else {
                            desktop.hiddenPackages + app.packageName
                        }
                    desktop.copy(hiddenPackages = packages)
                }
                hiddenAppsRow?.setValue("${SettingsUi.settings().desktop.hiddenEntryCount()} 个")
            }
        }
    }

    /** The project file entry is separate from installed system file managers. */
    private fun buildFileVisibility(page: InkPage) {
        SettingsUi.section(page, "内置入口", "开为显示，关闭后从桌面隐藏").toggle(
            label = "文件",
            get = { FILES_ENTRY_KEY !in SettingsUi.settings().desktop.hiddenSpecials },
        ) { visible ->
            updateDesktop { desktop ->
                val hidden =
                    if (visible) {
                        desktop.hiddenSpecials - FILES_ENTRY_KEY
                    } else {
                        desktop.hiddenSpecials +
                            FILES_ENTRY_KEY
                    }
                desktop.copy(hiddenSpecials = hidden)
            }
            hiddenAppsRow?.setValue("${SettingsUi.settings().desktop.hiddenEntryCount()} 个")
        }
    }

    /** 确认后把布局字段恢复为自动，保留隐藏应用与排序。 */
    private fun confirmResetLayout() {
        val context = contextOrNull() ?: return
        InkDialogs.confirm(
            context = context,
            title = "恢复本页默认",
            message = "仅重置布局参数，隐藏应用与排序保持不变。",
            confirmLabel = "恢复",
            onConfirm = {
                pendingJob?.cancel()
                pendingJob = null
                pendingDesktop = null
                updateDesktop {
                    it.copy(
                        columns = 0,
                        rows = 0,
                        columnSpacingDp = 0,
                        rowSpacingDp = 0,
                        gridAlign = GridAlign.CENTER,
                        firstPageRows = 0,
                        firstPageColumns = 0,
                        portraitFirstPageColumns = null,
                        portraitFirstPageRows = null,
                        landscapeFirstPageColumns = null,
                        landscapeFirstPageRows = null,
                        portraitColumns = 0,
                        portraitRows = 0,
                        landscapeColumns = 0,
                        landscapeRows = 0,
                        listRows = 0,
                        listFirstRows = 0,
                        listAlign = GridAlign.LEFT,
                        listMarginDp = 20,
                    )
                }
                SettingsUi.toast(context, "布局已恢复默认")
                rebuildNow()
            },
        )
    }

    /**
     * 在有序列表中查找值的位置，缺失时返回回退序号。
     *
     * 参数 values 为取值列表；value 为目标值；fallback 为缺省序号。
     */
    private fun selectedIndex(
        values: List<Int>,
        value: Int,
        fallback: Int = 0,
    ): Int = values.indexOf(value).takeIf { it >= 0 } ?: fallback

    /** 在浮点取值列表中查找值的位置。 */
    private fun selectedIndex(
        values: List<Float>,
        value: Float,
        fallback: Int = 0,
    ): Int = values.indexOf(value).takeIf { it >= 0 } ?: fallback

    private companion object {
        private const val TAG = "DesktopPrefs"

        /** 连续步进的合并窗口。 */
        private const val STEP_DEBOUNCE_MS = 160L

        private val KIND_OPTIONS = listOf<CharSequence>("宫格", "列表")

        private val ALIGN_VALUES = listOf(GridAlign.LEFT, GridAlign.CENTER, GridAlign.RIGHT)
        private val ALIGN_OPTIONS = listOf<CharSequence>("靠左", "居中", "靠右")

        private val SORT_MODES = listOf(SortMode.INSTALL_TIME, SortMode.LABEL, SortMode.PACKAGE, SortMode.MANUAL)
        private val SORT_OPTIONS = listOf<CharSequence>("安装时间", "名称", "包名", "手动")

        private val MENU_PRESS_VALUES = listOf(300f, 350f, 400f, 450f, 500f, 550f, 600f)
        private val MENU_PRESS_OPTIONS =
            MENU_PRESS_VALUES.map { "${it.toInt()} 毫秒" }

        private val EDIT_PRESS_VALUES =
            listOf(900f, 1000f, 1100f, 1200f, 1300f, 1400f, 1500f, 1600f, 1700f, 1800f, 1900f, 2000f)
        private val EDIT_PRESS_OPTIONS = EDIT_PRESS_VALUES.map { "${it.toInt()} 毫秒" }

        private val SWIPE_VALUES = listOf(24f, 28f, 32f, 36f, 40f, 44f, 48f, 52f, 56f, 60f, 64f)
        private val SWIPE_OPTIONS = SWIPE_VALUES.map { "${it.toInt()} dp" }
    }
}

private const val FILES_ENTRY_KEY = "special:files"

private fun DesktopConfig.hiddenEntryCount(): Int =
    hiddenPackages.size + if (FILES_ENTRY_KEY in hiddenSpecials) 1 else 0
