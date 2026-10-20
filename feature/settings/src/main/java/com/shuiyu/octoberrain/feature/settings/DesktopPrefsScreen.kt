package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.AppearanceConfig
import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.DesktopKind
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.GridAlign
import com.shuiyu.octoberrain.core.common.IMAGE_CAROUSEL_OPTIONS
import com.shuiyu.octoberrain.core.common.IconShape
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.SortMode
import com.shuiyu.octoberrain.core.common.TrayConfig
import com.shuiyu.octoberrain.core.common.effectiveImageCarouselSeconds
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 桌面偏好设置页面。
 *
 * 页面按首页布局、网格、图标与标签、交互、组件与内容五组排列，每行保持标题与右侧控件；
 * 连续步进以 [STEP_DEBOUNCE_MS] 合并写入，避免每次点击都重建整页。
 */
class DesktopPrefsScreen : SettingsBaseScreen("桌面偏好") {
    /** 尚未写入的桌面配置，连续步进在该对象上累加。 */
    private var pendingDesktop: DesktopConfig? = null

    /** 防抖写入任务。 */
    private var pendingJob: Job? = null

    /** 隐藏应用数量行，子页切换开关后就地更新。 */
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
        buildHomeLayout(page, config.desktop)
        buildGrid(page, config.desktop)
        buildIconAndLabel(page)
        buildInteraction(page, config.desktop)
        buildContent(page, config.desktop, config)
        // 负一屏设置由声明表生成入口行，跳转行为由页面提供。
        SettingsSpecRenderer.renderPage(
            page = page,
            groups = SettingsCatalog.negativeGroups,
            config = config,
            sink = settingEffectSink(),
            actions =
                mapOf(
                    NegativeSpecs.SPEC_SETTINGS_ENTRY to {
                        AppServices.router.navigate(Destination.NegativeSettings)
                    },
                ),
        )
        // 微读墨记入口：打开设置内全屏页面，页面结构与微读墨记应用一致。
        WereadAppSpecs.render(page)
        SettingsUi.note(page, "自动档位按屏幕尺寸反算，布局改动在返回桌面后生效。")
        // InkLog 当前只提供 d/w/e，构建耗时与行数用调试级输出。
        InkLog.d(
            TAG,
            "桌面偏好页构建完成：${SystemClock.elapsedRealtime() - startedAt}ms，${SettingsUi.rowCount()} 行",
        )
    }

    /** 首页布局：形态、首页行数与每页上限。 */
    private fun buildHomeLayout(
        page: InkPage,
        desktop: DesktopConfig,
    ) {
        val section = SettingsUi.section(page, "首页布局", "形态与分页")
        section.choose(
            label = "桌面形态",
            options = KIND_OPTIONS,
            selected = { if (SettingsUi.settings().desktop.kind == DesktopKind.LIST) 1 else 0 },
        ) { index ->
            updateDesktop { it.copy(kind = if (index == 1) DesktopKind.LIST else DesktopKind.GRID) }
        }
        section.stepper(
            label = "首页应用行数",
            values = FIRST_ROW_OPTIONS,
            selected = { selectedIndex(FIRST_ROW_VALUES, desktop.firstPageRows) },
        ) { index ->
            scheduleDesktop { it.copy(firstPageRows = FIRST_ROW_VALUES[index]) }
        }
        section.stepper(
            label = "每页应用上限",
            values = PAGE_LIMIT_OPTIONS,
            selected = { selectedIndex(PAGE_LIMIT_VALUES, desktop.pageLimit) },
        ) { index ->
            scheduleDesktop { it.copy(pageLimit = PAGE_LIMIT_VALUES[index]) }
        }
    }

    /** 网格：列数、行数、对齐、间距、图标尺寸与标签字号。 */
    private fun buildGrid(
        page: InkPage,
        desktop: DesktopConfig,
    ) {
        val section = SettingsUi.section(page, "网格", "行列与尺寸，自动时按屏幕反算")
        section.stepper(
            label = "每页列数",
            values = COLUMN_OPTIONS,
            selected = { selectedIndex(COLUMN_VALUES, desktop.columns) },
        ) { index ->
            scheduleDesktop { it.copy(columns = COLUMN_VALUES[index]) }
        }
        section.stepper(
            label = "每页行数",
            values = ROW_OPTIONS,
            selected = { selectedIndex(ROW_VALUES, desktop.rows) },
        ) { index ->
            scheduleDesktop { it.copy(rows = ROW_VALUES[index]) }
        }
        section.choose(
            label = "横向位置",
            options = ALIGN_OPTIONS,
            selected = { ALIGN_VALUES.indexOf(desktop.gridAlign).coerceAtLeast(0) },
        ) { index ->
            updateDesktop { it.copy(gridAlign = ALIGN_VALUES[index]) }
        }
        section.stepper(
            label = "列间距",
            values = SPACING_OPTIONS,
            selected = { selectedIndex(SPACING_VALUES, desktop.columnSpacingDp) },
        ) { index ->
            scheduleDesktop { it.copy(columnSpacingDp = SPACING_VALUES[index]) }
        }
        section.stepper(
            label = "行间距",
            values = SPACING_OPTIONS,
            selected = { selectedIndex(SPACING_VALUES, desktop.rowSpacingDp) },
        ) { index ->
            scheduleDesktop { it.copy(rowSpacingDp = SPACING_VALUES[index]) }
        }
        section.stepper(
            label = "图标尺寸",
            values = ICON_OPTIONS,
            selected = { selectedIndex(ICON_VALUES, desktop.iconSizeDp) },
        ) { index ->
            scheduleDesktop { it.copy(iconSizeDp = ICON_VALUES[index]) }
        }
        section.stepper(
            label = "应用名字号",
            values = LABEL_OPTIONS,
            selected = { selectedIndex(LABEL_VALUES, desktop.labelTextSp) },
        ) { index ->
            scheduleDesktop { it.copy(labelTextSp = LABEL_VALUES[index]) }
        }
    }

    /** 图标与标签：标签开关、对比度与图标外形。 */
    private fun buildIconAndLabel(page: InkPage) {
        val section = SettingsUi.section(page, "图标与标签", "名称、对比度与图标外形")
        section.toggle(
            label = "显示应用名称",
            get = { SettingsUi.settings().appearance.showLabels },
        ) { enabled ->
            updateAppearance { it.copy(showLabels = enabled) }
        }
        section.toggle(
            label = "仅显示图标",
            get = { SettingsUi.settings().desktop.iconOnly },
        ) { enabled ->
            updateDesktop { it.copy(iconOnly = enabled) }
        }
        section.toggle(
            label = "高对比度",
            get = { SettingsUi.settings().appearance.highContrast },
        ) { enabled ->
            updateAppearance { it.copy(highContrast = enabled) }
        }
        section.toggle(
            label = "页码指示",
            get = { SettingsUi.settings().desktop.showPageIndicator },
        ) { enabled ->
            updateDesktop { it.copy(showPageIndicator = enabled) }
        }
        section.choose(
            label = "图标形状",
            options = SHAPE_OPTIONS,
            selected = { SHAPE_VALUES.indexOf(SettingsUi.settings().appearance.iconShape).coerceAtLeast(0) },
        ) { index ->
            updateAppearance { it.copy(iconShape = SHAPE_VALUES[index]) }
        }
        section.stepper(
            label = "图标对比度",
            values = CONTRAST_OPTIONS,
            selected = {
                SettingsUi
                    .settings()
                    .appearance.iconContrast
                    .coerceIn(0, CONTRAST_VALUES.lastIndex)
            },
        ) { index ->
            updateAppearance { it.copy(iconContrast = CONTRAST_VALUES[index]) }
        }
    }

    /** 交互：排序、长按延迟、滑动阈值与音量键翻页。 */
    private fun buildInteraction(
        page: InkPage,
        desktop: DesktopConfig,
    ) {
        val section = SettingsUi.section(page, "交互", "排序与长按手势")
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

    /** 组件与内容：当前组件、组件图片、隐藏应用、书架行数与恢复默认。 */
    private fun buildContent(
        page: InkPage,
        desktop: DesktopConfig,
        config: LauncherConfig,
    ) {
        val section = SettingsUi.section(page, "组件与内容", "组件图片、隐藏应用与书架")
        section.info("当前组件", widgetLabel(desktop.widgetId))
        if (desktop.widgetId == WIDGET_IMAGE) {
            section.action(
                label = "组件图片",
                value = imageFileName(desktop.widgetImagePath) ?: "未选择",
            ) {
                pickWidgetImage()
            }
            if (desktop.widgetImagePath != null) {
                section.action(label = "清除组件图片", hint = "删除已复制的图片") {
                    clearWidgetImage()
                }
            }
            // 多图轮播间隔只作用于图片组件，候选与 IntegrationsConfig 中的常量一致。
            section.choose(
                label = "轮播间隔",
                options = IMAGE_CAROUSEL_LABELS,
                hint = "多张图片按文件名顺序轮播的切换间隔",
                selected = {
                    IMAGE_CAROUSEL_OPTIONS.indexOf(desktop.effectiveImageCarouselSeconds).coerceAtLeast(0)
                },
            ) { index ->
                updateDesktop { it.copy(imageCarouselSeconds = IMAGE_CAROUSEL_OPTIONS[index]) }
            }
        }
        hiddenAppsRow =
            section.action(
                label = "管理隐藏应用",
                value = "${desktop.hiddenPackages.size} 个",
            ) {
                showHiddenApps()
            }
        section.action(
            label = "书架行数",
            value = "${config.integrations.bookshelfRows} 行",
        ) {
            AppServices.router.navigate(Destination.BookshelfSettings)
        }
        section.button(label = "恢复本页默认", filled = true) {
            confirmResetLayout()
        }
    }

    /** 写入桌面配置并请求局部刷新，用于一次性改动。 */
    private fun updateDesktop(transform: (DesktopConfig) -> DesktopConfig) {
        SettingsUi.edit { config -> config.copy(desktop = transform(config.desktop)) }
        SettingsUi.refresh()
    }

    /** 写入外观配置并请求局部刷新。 */
    private fun updateAppearance(transform: (AppearanceConfig) -> AppearanceConfig) {
        SettingsUi.edit { config -> config.copy(appearance = transform(config.appearance)) }
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

    /** 选择并复制组件图片。 */
    private fun pickWidgetImage() {
        val context = contextOrNull() ?: return
        SettingsUi.pickFile(context, "选择组件图片", IMAGE_MIME_TYPES, IMAGE_EXTENSIONS) { uri ->
            copyWidgetImage(uri)
        }
    }

    /**
     * 复制所选图片到应用私有目录并写入配置。
     *
     * 参数 uri 为文件选择器返回的图片地址。
     */
    private fun copyWidgetImage(uri: Uri) {
        val context = contextOrNull() ?: return
        scope.launch {
            val path =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val directory = File(context.filesDir, WIDGET_DIRECTORY).apply { mkdirs() }
                        removeCopiedImages(directory)
                        val target = File(directory, "image.${imageExtension(context, uri)}")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(target).use { output -> input.copyTo(output) }
                        } ?: error("无法读取所选图片")
                        target.absolutePath
                    }.getOrNull()
                }
            if (path == null) {
                SettingsUi.toast(context, "图片复制失败，请更换图片")
                return@launch
            }
            updateDesktop { it.copy(widgetImagePath = path) }
            SettingsUi.toast(context, "组件图片已更新")
            rebuildLater()
        }
    }

    /** 清除组件图片与已复制的副本。 */
    private fun clearWidgetImage() {
        val context = contextOrNull() ?: return
        val current = SettingsUi.settings().desktop.widgetImagePath
        scope.launch {
            withContext(Dispatchers.IO) {
                removeCopiedImages(File(context.filesDir, WIDGET_DIRECTORY))
                current?.let { path -> runCatching { File(path).delete() } }
            }
            updateDesktop { it.copy(widgetImagePath = null) }
            SettingsUi.toast(context, "组件图片已清除")
            rebuildLater()
        }
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
        val apps =
            AppServices.catalog.apps.value
                .sortedBy { it.label.lowercase() }
        if (apps.isEmpty()) {
            SettingsUi.note(page, "应用列表尚未加载完成。")
            return
        }
        val section = SettingsUi.section(page, "应用", "关闭开关后该应用从桌面隐藏")
        apps.forEach { app ->
            section.toggle(
                label = app.label,
                get = {
                    SettingsUi
                        .settings()
                        .desktop.hiddenPackages
                        .contains(app.packageName)
                },
            ) { hidden ->
                updateDesktop { desktop ->
                    val packages =
                        if (hidden) {
                            desktop.hiddenPackages + app.packageName
                        } else {
                            desktop.hiddenPackages - app.packageName
                        }
                    desktop.copy(hiddenPackages = packages)
                }
                hiddenAppsRow?.setValue("${SettingsUi.settings().desktop.hiddenPackages.size} 个")
            }
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
                        iconSizeDp = 0,
                        labelTextSp = 0f,
                        gridAlign = GridAlign.CENTER,
                        firstPageRows = 0,
                    )
                }
                SettingsUi.toast(context, "布局已恢复默认")
                rebuildNow()
            },
        )
    }

    /** 读取组件名称。 */
    private fun widgetLabel(id: String): String =
        when (id) {
            "clock" -> "大时钟"
            WIDGET_IMAGE -> "图片"
            "none" -> "关闭"
            else -> id
        }

    /** 读取组件图片文件名，路径为空时返回 null。 */
    private fun imageFileName(path: String?): String? = path?.let { File(it).name }?.takeIf { it.isNotEmpty() }

    /** 根据 MIME 类型或扩展名推断图片扩展名。 */
    private fun imageExtension(
        context: Context,
        uri: Uri,
    ): String {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        return when (mime) {
            "image/jpeg" -> "jpg"
            "image/webp" -> "webp"
            "image/bmp" -> "bmp"
            else -> {
                val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()
                if (!extension.isNullOrEmpty() && extension in IMAGE_EXTENSIONS) extension else "png"
            }
        }
    }

    /** 删除组件图片目录内的旧副本。 */
    private fun removeCopiedImages(directory: File) {
        directory
            .listFiles()
            ?.filter { it.isFile && it.name.startsWith("image.") }
            ?.forEach { it.delete() }
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

        private const val WIDGET_IMAGE = "image"
        private const val WIDGET_DIRECTORY = "widgets"

        private val IMAGE_MIME_TYPES = arrayOf("image/*")
        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "bmp")

        /** 多图轮播间隔的显示文本，顺序与 [IMAGE_CAROUSEL_OPTIONS] 一致。 */
        private val IMAGE_CAROUSEL_LABELS = IMAGE_CAROUSEL_OPTIONS.map { "$it 秒" }

        private val KIND_OPTIONS = listOf<CharSequence>("宫格", "列表")

        private val FIRST_ROW_VALUES = listOf(0, 2, 3, 4)
        private val FIRST_ROW_OPTIONS = listOf<CharSequence>("自动", "2 行", "3 行", "4 行")

        private val PAGE_LIMIT_VALUES = listOf(3, 5, 8, 10, 15, 20)
        private val PAGE_LIMIT_OPTIONS = PAGE_LIMIT_VALUES.map { "$it 页" }

        private val COLUMN_VALUES = listOf(0, 3, 4, 5, 6, 7)
        private val COLUMN_OPTIONS = listOf<CharSequence>("自动", "3 列", "4 列", "5 列", "6 列", "7 列")

        private val ROW_VALUES = listOf(0, 2, 3, 4, 5, 6)
        private val ROW_OPTIONS = listOf<CharSequence>("自动", "2 行", "3 行", "4 行", "5 行", "6 行")

        private val SPACING_VALUES = listOf(0, 4, 8, 12, 16, 20, 24)
        private val SPACING_OPTIONS = listOf<CharSequence>("自动", "4 dp", "8 dp", "12 dp", "16 dp", "20 dp", "24 dp")

        private val ICON_VALUES = listOf(0, 40, 48, 56, 64, 72, 80)
        private val ICON_OPTIONS =
            listOf<CharSequence>("自动", "40 dp", "48 dp", "56 dp", "64 dp", "72 dp", "80 dp")

        private val LABEL_VALUES = listOf(0f, 10f, 11f, 12f, 13f, 14f, 15f, 16f)
        private val LABEL_OPTIONS =
            listOf<CharSequence>("跟随主题", "10 sp", "11 sp", "12 sp", "13 sp", "14 sp", "15 sp", "16 sp")

        private val ALIGN_VALUES = listOf(GridAlign.LEFT, GridAlign.CENTER, GridAlign.RIGHT)
        private val ALIGN_OPTIONS = listOf<CharSequence>("靠左", "居中", "靠右")

        private val SHAPE_VALUES = listOf(IconShape.PILL, IconShape.ROUNDED, IconShape.SQUARE)
        private val SHAPE_OPTIONS = listOf<CharSequence>("药丸", "圆角", "直角")

        private val CONTRAST_VALUES = listOf(0, 1, 2, 3)
        private val CONTRAST_OPTIONS = listOf<CharSequence>("关闭", "低", "中", "高")

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
