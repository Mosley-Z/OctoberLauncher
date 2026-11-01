package com.shuiyu.octoberrain.feature.home

import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.Window
import android.view.WindowManager
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.DesktopMode
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.EntryKeys
import com.shuiyu.octoberrain.core.common.EntryType
import com.shuiyu.octoberrain.core.common.FolderAutoGroup
import com.shuiyu.octoberrain.core.common.FolderFailure
import com.shuiyu.octoberrain.core.common.FolderResult
import com.shuiyu.octoberrain.core.common.FolderRules
import com.shuiyu.octoberrain.core.common.IconTargets
import com.shuiyu.octoberrain.core.common.ImmersiveLandscapeAdmission
import com.shuiyu.octoberrain.core.common.ImmersiveLandscapeBounds
import com.shuiyu.octoberrain.core.common.ImmersiveLandscapeGeometry
import com.shuiyu.octoberrain.core.common.InstalledApp
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.LauncherEntry
import com.shuiyu.octoberrain.core.common.SortMode
import com.shuiyu.octoberrain.core.common.SpecialEntry
import com.shuiyu.octoberrain.core.common.WEREAD_BOARD_WIDGET_ID
import com.shuiyu.octoberrain.core.common.forWidgetPage
import com.shuiyu.octoberrain.core.data.NotificationCenter
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkGlyphs
import com.shuiyu.octoberrain.core.device.DeviceRuntime
import com.shuiyu.octoberrain.core.widget.BaseWidgetView
import com.shuiyu.octoberrain.core.widget.WidgetHost
import com.shuiyu.octoberrain.core.widget.WidgetNoteOverlaySource
import com.shuiyu.octoberrain.core.widget.WidgetPlaceholder
import com.shuiyu.octoberrain.core.widget.WidgetRefreshPolicy
import com.shuiyu.octoberrain.core.widget.WidgetRegistry
import com.shuiyu.octoberrain.core.widget.WidgetTouchAction
import com.shuiyu.octoberrain.core.widget.WidgetView
import com.shuiyu.octoberrain.core.widget.hitRegionAt
import com.shuiyu.octoberrain.core.widget.triggersOnMinute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 桌面主视图。
 *
 * 职责：
 * 1. 按 [DesktopMetrics] 绘制宫格或列表形态的入口，首页顶部保留组件槽；
 * 2. 处理左右翻页、列表上下翻页、宫格竖向手势不打开项目菜单；
 * 3. 长按进入整理模式，支持拖动排序与单条菜单；
 * 4. 作为 [WidgetHost] 承载组件绘制与局部重绘。
 *
 * 视图只读取已经组装好的数据，数据订阅与生命周期由 [HomeScreen] 负责。
 */
internal class HomeView(
    context: Context,
) : View(context),
    WidgetHost {
    /** 尺寸令牌，随 DesignRuntime 初始化后可用。 */
    private val tokens get() = DesignRuntime.tokens

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val iconStore = IconStore(scope)
    private val iconRedraw =
        HomeIconRedraw(
            post = { postOnAnimation(it) },
            cancel = { removeCallbacks(it) },
            redraw = {
                invalidate()
                folderOverlay?.invalidateIcons()
                appPicker?.invalidateIcons()
            },
        )
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // 绘制画笔：复用实例，避免每帧分配。
    private val fillPaint = Paint()
    private val strokePaint = Paint()
    private val labelPaint = Paint()
    private val labelOutlinePaint = Paint()
    private val glyphPaint = Paint()

    // 数据快照
    private var config: LauncherConfig? = null
    private var apps: List<InstalledApp> = emptyList()

    // 包级索引：隐藏、卸载与应用信息按包名查询
    private var appsByPackage: Map<String, InstalledApp> = emptyMap()

    // 组件键索引：启动、图标与文件夹成员按组件键查询
    private var appsByKey: Map<String, InstalledApp> = emptyMap()
    private var entries: List<LauncherEntry> = emptyList()
    private var metrics: DesktopMetrics? = null
    private var previewMembers: Map<String, List<LauncherEntry>> = emptyMap()
    private var editableMembers: Map<String, List<LauncherEntry>> = emptyMap()
    private var openFolderId: String? = null
    private var folderDialog: Dialog? = null
    private var folderOverlay: FolderOverlay? = null
    private var appPicker: AppPickerOverlay? = null
    private var folderPickerDialog: Dialog? = null

    // 自动成组状态：目录快照就绪后只执行一次，浮层打开期间不重复触发
    private var autoGroupInFlight = false
    private var legacyConfirmOpen = false

    // 分页与组件
    private var currentPage = 0
    private val immersiveHome = ImmersiveHome()
    private var landscapeGeometry: ImmersiveLandscapeGeometry? = null

    private val noteOverlay =
        DesktopNoteOverlay {
            invalidate()
            requestPartial()
        }
    private val immersive get() = config?.desktop?.mode == DesktopMode.IMMERSIVE && !editing
    private var widget: WidgetView? = null
    private val widgetCache = mutableMapOf<String, WidgetView>()
    private var hostVisible = false
    private var immersiveRailTouch = false
    private var immersiveNavigationCandidate = false
    private var immersiveNavigationOwned = false
    private var editReturnPage = 0
    private var widgetId: String? = null
    private var widgetPolicy: WidgetRefreshPolicy = WidgetRefreshPolicy.MANUAL
    private var widgetWidth = 0
    private var widgetHeight = 0

    /** 预览模式：由美化模式创建，只绘制指定页、不响应触摸且不写回布局快照。 */
    var previewMode = false
        set(value) {
            field = value
            iconRedraw.setPreviewMode(value)
        }

    /** 预览目标页码，由 [setPreviewPage] 设置。 */
    private var previewPage = 0

    // 手势状态
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var gestureAxis = 0
    private var verticalConsumed = false
    private var widgetTouch = false
    private var widgetGestureConsumed = false
    private var widgetLongPressFired = false

    // 组件手势是否已发生位移：位移超过系统触摸阈值后抬手不再转发点击，避免滚动松手误触
    private var widgetGestureMoved = false

    // 组件按下点坐标，换算为组件局部像素后供长按与点击分发使用
    private var widgetLocalX = 0f
    private var widgetLocalY = 0f
    private var pressedIndex = -1

    // 整理模式状态
    private var editing = false
    private var dragIndex = -1
    private var dragMoved = false
    private var editHoldTriggered = false

    /** 首页右滑请求进入负一屏时回调，由宿主完成页面跳转。 */
    var onNegativePageRequested: (() -> Unit)? = null

    /** 长按计时到点后进入整理模式。 */
    private val enterEditRunnable =
        Runnable {
            val index = pressedIndex
            if (!editing && index >= 0 && index < entries.size) {
                editHoldTriggered = true
                enterEditMode(index)
            }
        }

    /** 组件长按计时到点后转发给组件，坐标取按下点的组件局部像素。 */
    private val widgetLongPressRunnable =
        Runnable {
            widgetLongPressFired = true
            if (immersive) {
                if (!immersiveRailTouch) widget?.onTouch(WidgetTouchAction.CANCEL, widgetLocalX, widgetLocalY)
                immersiveNavigationCandidate = false
                showBlankMenu()
            } else {
                widget?.onTouch(WidgetTouchAction.CANCEL, widgetLocalX, widgetLocalY)
                widget?.onLongPress(widgetLocalX, widgetLocalY)
                    ?: openDestination(Destination.PageWidgetPicker(currentPage))
            }
        }

    init {
        isFocusable = true
        isClickable = true
        // 电子墨水屏关闭抗锯齿，保持笔画锐利。
        val antiAlias = !DeviceRuntime.profile.display.isEink
        fillPaint.isAntiAlias = antiAlias
        strokePaint.isAntiAlias = antiAlias
        glyphPaint.isAntiAlias = antiAlias
        glyphPaint.style = Paint.Style.STROKE
    }

    /**
     * 应用一份数据快照。
     *
     * 参数 apps 为应用目录；config 为当前配置。入口、度量与组件会按需重建。
     */
    fun applyData(
        apps: List<InstalledApp>,
        config: LauncherConfig,
    ) {
        val oldConfig = this.config
        val iconsChanged = oldConfig?.appearance != config.appearance
        if (apps == this.apps && !homeConfigChanged(oldConfig, config) && !iconsChanged) return
        // 图标覆盖变化时清空位图缓存，使新图片与恢复默认都能立即生效。
        if (iconsChanged) iconStore.clear()
        val previousKey = entries.getOrNull(dragIndex)?.key
        val effective = initializeInstallOrder(apps, config)
        closeForModeChange(oldConfig, effective)
        this.apps = apps
        this.appsByPackage = apps.associateBy { it.packageName }
        this.appsByKey = apps.associateBy { EntryKeys.appEntryKey(it) }
        this.config = effective
        this.entries = DesktopEntries.build(apps, effective.desktop)
        rebuildFolderMembers(effective)
        if (typographyChanged(oldConfig, effective)) closeFolder()
        val previousWidget = widget
        ensureWidget(effective)
        if (previousWidget !== widget ||
            homeConfigChanged(oldConfig, effective)
        ) {
            widget?.onConfigChanged(widgetConfig(effective))
        }
        recomputeMetrics(closeOpenFolder = false)
        clampPage()
        restoreEditingTarget(previousKey)
        bindOpenFolder()
        invalidate()
        maybeProcessAutoGroup()
    }

    private fun initializeInstallOrder(
        apps: List<InstalledApp>,
        config: LauncherConfig,
    ): LauncherConfig {
        // 首次启用桌面时把入口顺序固定为安装时间升序；预览模式只读，不写入配置。
        var effective = config
        if (!previewMode && apps.isNotEmpty() && !config.desktop.installOrderInitialized) {
            val order = buildInitialOrder(apps, config)
            runCatching {
                AppServices.config.edit { cfg ->
                    if (cfg.desktop.installOrderInitialized) {
                        cfg
                    } else {
                        cfg.copy(
                            desktop =
                                cfg.desktop.copy(
                                    order = order,
                                    sortMode = SortMode.MANUAL,
                                    installOrderInitialized = true,
                                ),
                        )
                    }
                }
            }
            // 本轮直接按写入后的配置组装入口，避免等待配置流回环时出现一次旧顺序。
            effective =
                config.copy(
                    desktop =
                        config.desktop.copy(
                            order = order,
                            sortMode = SortMode.MANUAL,
                            installOrderInitialized = true,
                        ),
                )
        }
        return effective
    }

    private fun rebuildFolderMembers(config: LauncherConfig) {
        val hidden = config.desktop.hiddenPackages
        previewMembers =
            config.desktop.folders.associate { folder ->
                folder.id to previewFolderMembers(folder, appsByKey, hidden)
            }
        editableMembers =
            config.desktop.folders.associate { folder ->
                folder.id to editableFolderMembers(folder, appsByKey, hidden)
            }
    }

    private fun typographyChanged(
        old: LauncherConfig?,
        next: LauncherConfig,
    ): Boolean =
        when {
            old?.appearance?.textScale != next.appearance.textScale -> true
            old?.appearance?.labelFontPath != next.appearance.labelFontPath -> true
            old?.appearance?.themeTokensJson != next.appearance.themeTokensJson -> true
            old?.desktop?.labelTextSp != next.desktop.labelTextSp -> true
            else -> false
        }

    private fun restoreEditingTarget(previousKey: String?) {
        if (editing) {
            // 整理模式下数据刷新时按入口键恢复拖动目标，避免下标漂移。
            dragIndex = if (previousKey != null) entries.indexOfFirst { it.key == previousKey } else -1
            pressedIndex = dragIndex
            if (previousKey != null && dragIndex < 0) exitEditMode()
        } else {
            pressedIndex = -1
        }
    }

    private fun closeForModeChange(
        previous: LauncherConfig?,
        next: LauncherConfig,
    ) {
        if (previous?.desktop?.mode != next.desktop.mode) {
            closeFolder()
            noteOverlay.close()
            immersiveHome.closeDrawer()
        }
    }

    /**
     * 生成首次启用桌面的入口顺序。
     *
     * 参数 apps 为应用目录快照，config 为当前配置。
     * 应用按安装时间升序排列，同一时间按标签与包名确定次序；已隐藏的应用不写入顺序；
     * 内置特殊入口沿用 [DesktopEntries] 当前顺序追加在末尾。
     */
    private fun buildInitialOrder(
        apps: List<InstalledApp>,
        config: LauncherConfig,
    ): List<String> {
        val appKeys =
            apps
                .filter { it.packageName !in config.desktop.hiddenPackages }
                .sortedWith(
                    compareBy<InstalledApp> { it.firstInstallTime }
                        .thenBy { it.label }
                        .thenBy { it.packageName }
                        .thenBy { it.activityName.orEmpty() },
                ).map { EntryKeys.appEntryKey(it) }
        val specialKeys =
            DesktopEntries
                .build(apps, config.desktop)
                .filter { it.type == EntryType.SPECIAL }
                .map { it.key }
        return appKeys + specialKeys
    }

    /** 数据或图标变化后立即重绘。 */
    fun refreshNow() {
        invalidate()
    }

    /**
     * 刷新首页组件。
     *
     * 参数 force 表示是否强制重新加载内容；未配置组件时只重绘桌面。
     * 刷新完成后请求一次局部重绘，让组件自行绘制的区域同步到屏幕。
     */
    fun refreshCurrentWidget(force: Boolean = true) {
        val current = widget
        if (current == null) {
            invalidate()
            return
        }
        runCatching { current.onRefresh(force) }
        invalidate()
        requestPartial()
    }

    private var minuteRefreshDay = java.time.LocalDate.now()

    /**
     * 整分事件触发的组件刷新。
     *
     * 沉浸时钟由宿主统一更新；月历与待办周历仅跨日重读，内容变化另走通知。
     * 其他按分钟刷新的组件保持原有策略。
     */
    fun refreshWidgetByMinute() {
        if (folderDialog != null) return
        val today = java.time.LocalDate.now()
        val dayChanged = today != minuteRefreshDay
        minuteRefreshDay = today
        when {
            immersive -> if (!immersiveHome.drawerOpen) invalidate()
            metrics?.widgetRect(currentPage)?.isEmpty != false -> Unit
            !widgetPolicy.triggersOnMinute() -> Unit
            else -> {
                val id = config?.desktop?.forWidgetPage(currentPage)?.widgetId
                if (dayChanged || (id != "calendar" && id != "todo_week")) refreshCurrentWidget(true)
            }
        }
    }

    /**
     * 处理音量键翻页。
     *
     * 参数 up 为 true 表示音量上键，翻到上一页；为 false 表示音量下键，翻到下一页。
     * 网格与列表两种形态共用同一分页逻辑；整理模式下消费按键但不翻页；
     * 已处于首尾页时同样消费按键，避免系统调整音量。返回值恒为 true。
     */
    fun turnPageByVolume(up: Boolean): Boolean {
        if (folderDialog != null) return true
        noteOverlay.close()
        if (immersive) return true
        if (editing) return true
        val m = metrics ?: return true
        // 页码变化时由页方法内部请求局部刷新，这里只负责重绘。
        if (up) previousPage(m) else nextPage(m)
        invalidate()
        return true
    }

    /**
     * 返回键处理。
     *
     * 整理模式下先退出整理模式并返回 true，其余情况返回 false 交由上层处理。
     */
    fun handleBack(): Boolean =
        when {
            appPicker?.cancel() == true -> true
            folderPickerDialog?.isShowing == true -> {
                folderPickerDialog?.cancel()
                folderPickerDialog = null
                true
            }
            closeFolder() -> true
            noteOverlay.close() -> true
            immersiveHome.closeDrawer() -> {
                updateWidgetVisibility()
                invalidate()
                true
            }
            editing -> {
                exitEditMode()
                true
            }
            else -> false
        }

    /** 桌面可见性传给自绘组件，暂停后台轮播。 */
    fun setHostVisible(visible: Boolean) {
        hostVisible = visible
        iconRedraw.setVisible(visible)
        if (!visible) noteOverlay.close()
        updateWidgetVisibility()
    }

    private fun updateWidgetVisibility() {
        val previous = widget
        config?.let { cfg ->
            ensureWidget(cfg)
            if (previous !== widget) widget?.onConfigChanged(widgetConfig(cfg))
        }
        val pageVisible = immersive || metrics?.widgetRect(currentPage)?.isEmpty == false
        widget?.onHostVisible(hostVisible && pageVisible && !immersiveHome.drawerOpen && folderDialog == null)
    }

    /** 页面销毁时释放组件、计时器与协程。 */
    fun release() {
        iconRedraw.release()
        closeFolderPickers()
        closeFolder()
        noteOverlay.close()
        handler.removeCallbacksAndMessages(null)
        landscapeGeometry = null
        immersiveHome.landscapeGeometry = null
        widgetCache.values.forEach {
            it.setImmersiveLandscapeGeometry(null)
            it.onDestroy()
        }
        widgetCache.clear()
        widget = null
        widgetId = null
        iconStore.clear()
        scope.cancel()
    }

    // region 生命周期与尺寸

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        noteOverlay.close()
        recomputeMetrics()
        clampPage()
        invalidate()
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        noteOverlay.close()
        recomputeMetrics()
        clampPage()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        iconRedraw.setAttached(false)
        closeFolderPickers()
        closeFolder()
        landscapeGeometry = null
        immersiveHome.landscapeGeometry = null
        widgetCache.values.forEach { it.setImmersiveLandscapeGeometry(null) }
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        iconRedraw.setAttached(true)
        recomputeMetrics()
        clampPage()
        invalidate()
    }

    /** 依据当前画布尺寸与配置重算版面。 */
    private fun recomputeMetrics(closeOpenFolder: Boolean = true) {
        if (closeOpenFolder) closeFolder()
        if (config == null || width <= 0 || height <= 0) {
            updateLandscapeGeometry()
            return
        }
        val cfg = config ?: return
        val m = DesktopMetrics.compute(width, height, cfg)
        if (m != metrics) closeFolder()
        metrics = m
        updateLandscapeGeometry()
        // 预览视图与真实桌面共用绘制代码，但不得覆盖美化模式读取的全局快照。
        if (!previewMode) publishLayout(m)
    }

    /**
     * 设置预览页码。
     *
     * 参数 index 为目标页码，从 0 开始；越界时夹到有效范围。
     * 预览模式下当前页固定为该页，组件按当前数据刷新一次静态内容后重绘，
     * 不安装定时刷新。
     */
    fun setPreviewPage(index: Int) {
        previewPage = index
        currentPage = index.coerceAtLeast(0)
        clampPage()
        runCatching { widget?.onRefresh(false) }
        invalidate()
    }

    /**
     * 把最新条目与分页结果写入布局快照服务。
     *
     * 应用安装卸载、排序、隐藏、列行数与屏幕尺寸变化都会经过版面重算，
     * 因此在这里同步即可让美化模式读取到与桌面一致的分页。
     */
    private fun publishLayout(m: DesktopMetrics) {
        if (!AppServices.hasLayoutProvider()) return
        HomeDesktopLayoutProvider.update(m, entries)
    }

    /** 校正当前页码，避免数据减少后停留在空页；预览模式下固定为预览页。 */
    private fun clampPage() {
        val m = metrics ?: return
        val target = if (previewMode) previewPage else currentPage
        currentPage = target.coerceIn(0, m.pageCount(entries.size) - 1)
        updateWidgetVisibility()
    }

    /** 按配置要求重绘并触发局部刷新请求。 */
    private fun requestPartial() {
        runCatching { AppServices.refresh.requestPartial() }
    }

    /**
     * 图标覆盖变化后刷新桌面图标。
     *
     * 应用目录在加载图标时会优先返回覆盖图片，桌面只需清空内存缓存并重绘。
     * 这里使用局部刷新，使图标在当前页原位更新，不触发整屏刷新。
     */
    fun invalidateIcons() {
        iconStore.clear()
        invalidate()
        folderOverlay?.invalidateIcons()
        requestPartial()
    }

    /** 请求整屏刷新，用于壁纸更换后清除墨水屏残影。 */
    private fun requestFullRefresh() {
        runCatching { AppServices.refresh.requestFullRefresh() }
    }

    // endregion

    // region 组件宿主

    /**
     * 按配置创建或替换组件。
     *
     * 参数 cfg 为最新配置。组件标识未变化时保留现有实例；
     * 新标识先创建视图，创建成功后再记录标识与刷新策略，
     * 组件未注册或创建失败时保持标识为空，后续配置回调可重新尝试。
     */
    private fun widgetConfig(cfg: LauncherConfig): LauncherConfig =
        if (immersive) cfg else cfg.copy(desktop = cfg.desktop.forWidgetPage(currentPage))

    private fun ensureWidget(cfg: LauncherConfig) {
        val id = if (immersive) WEREAD_BOARD_WIDGET_ID else cfg.desktop.forWidgetPage(currentPage).widgetId
        val key = if (immersive) "immersive" else "$currentPage:$id"
        val keep =
            cfg.desktop.pageWidgets
                .map { (page, slot) -> "$page:${slot.widgetId}" }
                .toSet() +
                setOf("0:${cfg.desktop.widgetId}", "immersive")
        widgetCache.keys
            .filter { it !in keep }
            .toList()
            .forEach { key ->
                widgetCache.remove(key)?.let {
                    it.setImmersiveLandscapeGeometry(null)
                    it.onDestroy()
                }
            }
        if (key == widgetId) {
            widget?.setImmersiveBookMode(immersive)
            widget?.setImmersiveLandscape(width > height)
            return
        }
        noteOverlay.close()
        widget?.onHostVisible(false)
        widget?.setImmersiveLandscapeGeometry(null)
        widget = null
        widgetWidth = 0
        widgetHeight = 0
        // 选择“不使用组件”时记录空标识，避免每次配置回调重复进入本方法。
        if (id.isBlank()) {
            widgetId = key
            widgetPolicy = WidgetRefreshPolicy.MANUAL
            return
        }
        val created = widgetCache[key] ?: runCatching { WidgetRegistry.create(id, context) }.getOrNull()
        if (created != null) {
            (created as? BaseWidgetView)?.apply {
                host = this@HomeView
                pageIndex = if (immersive) 0 else currentPage
            }
            widget = created
            widgetCache[key] = created
            created.setImmersiveBookMode(immersive)
            created.setImmersiveLandscape(width > height)
            widgetId = key
            widgetPolicy = WidgetRegistry.get(id)?.refreshPolicy ?: WidgetRefreshPolicy.MANUAL
        } else {
            // 创建失败时不写入标识，下一次配置回调继续尝试，避免永久显示占位。
            widgetId = null
            widgetPolicy = WidgetRefreshPolicy.MANUAL
        }
    }

    private fun updateLandscapeGeometry() {
        val hasWindow = rootView.width > 0 && rootView.height > 0
        val hasLandscapeContent = immersive && width > height && height > 0
        landscapeGeometry =
            if (hasLandscapeContent && hasWindow) {
                val original = immersiveHome.bookBounds(width, height)
                val origin = IntArray(2)
                val rootOrigin = IntArray(2)
                getLocationInWindow(origin)
                rootView.getLocationInWindow(rootOrigin)
                ImmersiveLandscapeGeometry
                    .create(
                        ImmersiveLandscapeBounds(
                            rootOrigin[0].toFloat(),
                            rootOrigin[1].toFloat(),
                            (rootOrigin[0] + rootView.width).toFloat(),
                            (rootOrigin[1] + rootView.height).toFloat(),
                        ),
                        origin[0].toFloat(),
                        origin[1].toFloat(),
                        width.toFloat(),
                        height.toFloat(),
                        ImmersiveLandscapeBounds(
                            original.left,
                            original.top,
                            original.right,
                            original.bottom,
                        ),
                        tokens.density,
                    ).let { candidate ->
                        candidate.admitted(
                            immersiveHome.admitsLandscapeGeometry(candidate, width),
                            (widget as? ImmersiveLandscapeAdmission)?.admitsLandscapeGeometry(candidate) == true,
                        )
                    }
            } else {
                null
            }
        immersiveHome.landscapeGeometry = landscapeGeometry
        widgetCache.values.forEach { cached ->
            cached.setImmersiveLandscapeGeometry(if (cached === widget) landscapeGeometry else null)
        }
    }

    private fun immersiveBookBounds(): RectF {
        val book = landscapeGeometry?.book ?: return immersiveHome.bookBounds(width, height)
        return RectF(book.left, book.top, book.right, book.bottom)
    }

    private fun widgetBounds(m: DesktopMetrics): RectF {
        updateLandscapeGeometry()
        return if (immersive) immersiveBookBounds() else m.widgetRect(currentPage)
    }

    private fun noteBounds(m: DesktopMetrics): RectF {
        val rect = widgetBounds(m)
        if (width > height) return RectF(rect.left, rect.top, m.forPage(currentPage).noteRightBoundary(), rect.bottom)
        val geometry = m.forPage(currentPage)
        val count = minOf(m.capacity(currentPage), (entries.size - m.startIndex(currentPage)).coerceAtLeast(0))
        val bottom =
            if (count >
                0
            ) {
                (0 until count).maxOf { entryContentBottom(geometry, currentPage, it) }
            } else {
                rect.bottom
            }
        return RectF(rect.left, rect.top, rect.right, (bottom + tokens.gap).coerceAtMost(m.bottom.toFloat()))
    }

    /** 与绘制的名称基线、字体下沉完全同源；隐藏名称时使用整格点击区域。 */
    private fun entryContentBottom(
        m: DesktopMetrics,
        page: Int,
        slot: Int,
    ): Float {
        val cell = m.cellRect(page, slot)
        if (!m.showLabels) return cell.bottom
        labelPaint.textSize = labelTextSize(tokens.textBody)
        val fm = labelPaint.fontMetrics
        return if (m.isList) {
            cell.centerY() - (fm.ascent + fm.descent) / 2f + fm.descent
        } else {
            gridLabelBaseline(m, cell) + fm.descent
        }
    }

    private fun gridLabelBaseline(
        m: DesktopMetrics,
        cell: RectF,
    ): Float {
        val top = cell.top + ((cell.height() - m.iconSize - m.labelHeight) / 2f).coerceAtLeast(0f)
        val labelHeight = maxOf(m.labelHeight.toFloat(), if (editing) tokens.textSmall else 0f)
        val fm = labelPaint.fontMetrics
        return top + m.iconSize + labelHeight / 2f - (fm.ascent + fm.descent) / 2f
    }

    override fun openNoteOverlay(widget: WidgetView): Boolean {
        val m = metrics
        val eligible = widget === this.widget && widget is WidgetNoteOverlaySource
        if (!eligible || m == null) return false
        val bounds = noteBounds(m)
        val canGrow =
            if (width >
                height
            ) {
                bounds.right > widgetBounds(m).right
            } else {
                bounds.bottom > widgetBounds(m).bottom
            }
        val opened = canGrow && noteOverlay.open(widget as WidgetNoteOverlaySource, bounds)
        if (!opened && width <= height) openDestination(Destination.Note)
        invalidate()
        return true
    }

    private fun isTransparent(entry: LauncherEntry): Boolean =
        entry.isFolder ||
            IconTargets.normalizeStoredKey(entry.key) in config?.appearance?.transparentIconTargets.orEmpty()

    private fun drawImmersiveIcon(
        canvas: Canvas,
        rect: RectF,
        entry: LauncherEntry,
        forceDefault: Boolean,
    ) {
        // 抽屉的应用使用原生图标，但十雨设置是特殊入口，必须走品牌图标加载器。
        if (entry.isFolder || !forceDefault || entry.special == SpecialEntry.SETTINGS) {
            drawIconFrame(canvas, rect, entry)
            return
        }
        val drawable =
            entry.packageName?.let { packageName ->
                runCatching {
                    // 抽屉按组件显示原生图标：多入口应用各自的启动 Activity 图标不同。
                    val activityName = entry.activityName
                    if (activityName != null) {
                        context.packageManager.getActivityIcon(ComponentName(packageName, activityName))
                    } else {
                        context.packageManager.getApplicationIcon(packageName)
                    }
                }.getOrNull()
            }
        if (drawable != null) {
            drawable.setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
            drawable.draw(canvas)
        } else {
            drawFallbackGlyph(canvas, rect, entry)
        }
    }

    private fun exitImmersive() {
        noteOverlay.close()
        immersiveHome.closeDrawer()
        AppServices.config.edit { it.copy(desktop = it.desktop.copy(mode = DesktopMode.NORMAL)) }
        invalidate()
    }

    /** 组件请求重绘：桌面为单屏自绘视图，直接整屏失效即可。 */
    override fun requestWidgetDraw(widget: WidgetView) {
        invalidate()
    }

    /** 组件请求跳转内置页面。 */
    override fun openDestination(destination: Destination) {
        runCatching {
            AppServices.router.navigate(
                if (destination ==
                    Destination.WidgetPicker
                ) {
                    Destination.PageWidgetPicker(currentPage)
                } else {
                    destination
                },
            )
        }
    }

    // endregion

    // region 绘制

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateLandscapeGeometry()
        val m = metrics ?: return
        val cfg = config ?: return
        // 壁纸由宿主全屏视图绘制，桌面保持透明，不再自行绘制底图。
        if (immersive) {
            if (!immersiveHome.drawerOpen) drawWidget(canvas, m, cfg)
            val railLength = if (width > height) height else width
            val railCapacity = ((railLength - tokens.pageGutter * 2) / tokens.dp(48f)).coerceAtLeast(2) - 1
            val favoriteLimit =
                cfg.desktop.favoriteCount
                    .coerceIn(3, 6)
                    .coerceAtMost(railCapacity)
            val favorites =
                cfg.desktop.favoriteEntryKeys
                    .mapNotNull { key ->
                        FavoriteEntryResolver.resolve(key, entries)
                    }.ifEmpty {
                        if (cfg.desktop.favoriteEntryKeys.isEmpty()) entries.take(favoriteLimit) else emptyList()
                    }.take(favoriteLimit)
            immersiveHome.draw(
                canvas,
                width,
                height,
                entries,
                favorites,
                cfg.appearance.showLabels,
                ::drawImmersiveIcon,
                ::launchEntry,
                ::exitImmersive,
            ) {
                noteOverlay.close()
                updateWidgetVisibility()
                invalidate()
                requestPartial()
            }
        } else {
            drawPages(canvas, m, cfg)
            drawPageIndicator(canvas, m, cfg)
        }
        noteOverlay.draw(canvas, noteBounds(m))
        // 预览只展示静态版面，不进入整理模式，因此不绘制整理提示。
        if (!previewMode && editing) drawEditBanner(canvas)
    }

    /** 绘制当前页；翻页采用直接切换，不绘制滑动预览与相邻页。 */
    private fun drawPages(
        canvas: Canvas,
        m: DesktopMetrics,
        cfg: LauncherConfig,
    ) {
        drawPage(canvas, m, cfg, currentPage)
    }

    /** 绘制单页内容。 */
    private fun drawPage(
        canvas: Canvas,
        m: DesktopMetrics,
        cfg: LauncherConfig,
        page: Int,
    ) {
        if (!m.widgetRect(page).isEmpty) drawWidget(canvas, m, cfg)
        val start = m.startIndex(page)
        val capacity = m.capacity(page)
        var drawn = 0
        for (slot in 0 until capacity) {
            val index = start + slot
            if (index >= entries.size) break
            drawn++
            drawEntry(canvas, m, page, slot, index)
        }
        if (drawn == 0 && page == 0) {
            drawEmptyHint(canvas, m)
        }
    }

    /** 绘制空桌面提示。 */
    private fun drawEmptyHint(
        canvas: Canvas,
        m: DesktopMetrics,
    ) {
        val paint = DesignRuntime.textPaint(tokens.textBody, InkColors.INK_2)
        paint.textAlign = Paint.Align.CENTER
        val cx = width / 2f
        val cy = (m.top + m.bottom) / 2f
        canvas.drawText("未发现可启动的应用", cx, cy, paint)
    }

    /** 绘制组件槽外框与组件内容。 */
    private fun drawWidget(
        canvas: Canvas,
        m: DesktopMetrics,
        cfg: LauncherConfig,
    ) {
        val rect = widgetBounds(m)
        if (rect.isEmpty) return
        val view = widget
        // 透明背景的组件（例如时钟）不绘制纸白底与边框，让壁纸从槽位透出；
        // 组件缺失时仍绘制占位底色，保证提示文字有可读背景。
        if (!immersive && (view == null || !view.transparentBackground)) {
            val radius = 0f
            fillPaint.style = Paint.Style.FILL
            fillPaint.color = InkColors.PAPER
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = tokens.stroke.toFloat()
            strokePaint.color = InkColors.INK_4
            if (!immersive) canvas.drawLine(rect.left, rect.bottom, rect.right, rect.bottom, strokePaint)
        }
        val w = rect.width().toInt()
        val h = rect.height().toInt()
        if (w <= 0 || h <= 0) return
        canvas.save()
        canvas.clipRect(rect)
        canvas.translate(rect.left, rect.top)
        if (view != null) {
            // 非基类组件由宿主转发尺寸变化；基类在 draw 内部自行缓存尺寸。
            if (view !is BaseWidgetView && (w != widgetWidth || h != widgetHeight)) {
                widgetWidth = w
                widgetHeight = h
                runCatching { view.onSizeChanged(w, h) }
            }
            runCatching { view.draw(canvas, w, h) }
                .onFailure { WidgetPlaceholder.draw(canvas, w, h, "组件绘制失败") }
        } else {
            WidgetPlaceholder.draw(canvas, w, h, "未安装组件 ${cfg.desktop.widgetId}")
        }
        canvas.restore()
    }

    /** 绘制一个入口，按形态分派。 */
    private fun drawEntry(
        canvas: Canvas,
        m: DesktopMetrics,
        page: Int,
        slot: Int,
        index: Int,
    ) {
        val geometry = m.forPage(page)
        if (geometry.isList) {
            drawListCell(canvas, geometry, page, slot, index)
        } else {
            drawGridCell(canvas, geometry, page, slot, index)
        }
    }

    /** 绘制宫格单元：图标框、标签、拖动与按压状态。 */
    private fun drawGridCell(
        canvas: Canvas,
        m: DesktopMetrics,
        page: Int,
        slot: Int,
        index: Int,
    ) {
        val cell = m.cellRect(page, slot)
        val entry = entries[index]
        if (editing && index == dragIndex) drawSelectedCell(canvas, cell)
        val contentHeight = m.iconSize + m.labelHeight
        val top = cell.top + ((cell.height() - contentHeight) / 2f).coerceAtLeast(0f)
        val iconRect =
            RectF(
                cell.centerX() - m.iconSize / 2f,
                top,
                cell.centerX() + m.iconSize / 2f,
                top + m.iconSize,
            )
        drawIconFrame(canvas, iconRect, entry)
        drawEntryBadge(canvas, iconRect, entry)
        val normalLabelVisible = m.showLabels && m.labelHeight > 0
        val editingLabelVisible = editing && isTransparent(entry)
        if (normalLabelVisible || editingLabelVisible) {
            // 标签字号取正文令牌，按图墨以较小字号呈现完整名称。
            labelPaint.textSize = labelTextSize(tokens.textBody)
            val maxWidth = cell.width()
            val text = ellipsize(entry.label, labelPaint, maxWidth)
            val baseline = gridLabelBaseline(m, cell)
            drawLabelText(canvas, text, cell.centerX(), baseline, Paint.Align.CENTER)
            if (!editing && index == pressedIndex) {
                // 按压反馈改为标签下划黑线，避免整格灰底在墨水屏上产生大片残影。
                val half = labelPaint.measureText(text) / 2f
                drawPressUnderline(canvas, cell.centerX() - half, cell.centerX() + half, baseline)
            }
        }
    }

    /** 绘制列表行：小图标、标签、行分隔线与按压状态。 */
    private fun drawListCell(
        canvas: Canvas,
        m: DesktopMetrics,
        page: Int,
        slot: Int,
        index: Int,
    ) {
        val cell = m.cellRect(page, slot)
        val entry = entries[index]
        if (editing && index == dragIndex) drawSelectedCell(canvas, cell)
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = tokens.stroke.toFloat()
        strokePaint.color = InkColors.INK_4
        canvas.drawLine(cell.left, cell.bottom, cell.right, cell.bottom, strokePaint)
        labelPaint.textSize = labelTextSize(tokens.textBody)
        val showLabels = m.showLabels || editing && isTransparent(entry)
        val textWidth = if (showLabels) labelPaint.measureText(entry.label) else 0f
        val contentWidth =
            (m.iconSize + if (showLabels) m.gap + textWidth else 0f)
                .coerceAtMost((cell.width() - tokens.gap).coerceAtLeast(1f))
        val iconLeft =
            when (config?.desktop?.listAlign) {
                com.shuiyu.octoberrain.core.common.GridAlign.CENTER -> cell.centerX() - contentWidth / 2f
                com.shuiyu.octoberrain.core.common.GridAlign.RIGHT -> cell.right - tokens.gap / 2f - contentWidth
                else -> cell.left + tokens.gap / 2f
            }
        val iconTop = cell.centerY() - m.iconSize / 2f
        val iconRect = RectF(iconLeft, iconTop, iconLeft + m.iconSize, iconTop + m.iconSize)
        drawIconFrame(canvas, iconRect, entry)
        if (showLabels) {
            val textLeft = iconRect.right + m.gap
            val maxWidth = (cell.right - textLeft - tokens.gap / 2f).coerceAtLeast(1f)
            val text = ellipsize(entry.label, labelPaint, maxWidth)
            val fm = labelPaint.fontMetrics
            val baseline = cell.centerY() - (fm.ascent + fm.descent) / 2f
            drawLabelText(canvas, text, textLeft, baseline, Paint.Align.LEFT)
            if (!editing && index == pressedIndex) {
                drawPressUnderline(canvas, textLeft, textLeft + labelPaint.measureText(text), baseline)
            }
        }
    }

    /** 按压反馈：标签下方绘制按宽度比例的黑色下划线。 */
    private fun drawPressUnderline(
        canvas: Canvas,
        leftEdge: Float,
        rightEdge: Float,
        baseline: Float,
    ) {
        if (rightEdge <= leftEdge) return
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = maxOf(1f, width * PRESS_UNDERLINE_RATIO)
        strokePaint.color = InkColors.INK
        // 下划线落在标签基线下方的字体下沉区域之后，紧贴文字下沿。
        val underlineY = baseline + labelPaint.descent() + strokePaint.strokeWidth
        canvas.drawLine(leftEdge, underlineY, rightEdge, underlineY, strokePaint)
    }

    /** 拖动目标反馈：加粗描边。 */
    private fun drawSelectedCell(
        canvas: Canvas,
        cell: RectF,
    ) {
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = (tokens.stroke * 2).toFloat()
        strokePaint.color = InkColors.INK
        val radius = tokens.radiusSmall.toFloat()
        canvas.drawRoundRect(cell, radius, radius, strokePaint)
    }

    /**
     * 绘制应用图标。
     *
     * 第三轮调整后不再绘制外白线、白底与内黑线，图标位图直接铺满图标矩形；
     * 图标尺寸仍由 DesktopMetrics 的 iconSize 决定。缺图时绘制内置线稿或首字符占位，
     * 并在位图加载完成后重绘。
     */
    private fun drawIconFrame(
        canvas: Canvas,
        rect: RectF,
        entry: LauncherEntry,
    ) {
        when {
            entry.isFolder ->
                FolderPreviewPainter.draw(canvas, rect, previewMembers[entry.folder?.id].orEmpty(), ::drawIconFrame)
            isTransparent(entry) -> if (editing) drawSelectedCell(canvas, rect)
            rect.width() <= 0f || rect.height() <= 0f -> Unit
            else -> drawAppIconFrame(canvas, rect, entry)
        }
    }

    private fun drawAppIconFrame(
        canvas: Canvas,
        rect: RectF,
        entry: LauncherEntry,
    ) {
        val sizePx = rect.width().coerceAtMost(rect.height()).toInt()
        val bitmap = iconStore.get(entry.key, sizePx)
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, rect, null)
        } else {
            iconStore.request(entry, sizePx, iconRedraw::changed)
            drawFallbackGlyph(canvas, rect, entry)
        }
    }

    /**
     * 计算标签字号。
     *
     * 参数 fallbackPx 为设计令牌给出的基准字号；桌面配置中的标签字号大于零时
     * 以 sp 换算为像素，并限制在基准字号的合理倍率区间内。
     */
    private fun labelTextSize(fallbackPx: Float): Float {
        val sp = config?.desktop?.labelTextSp ?: 0f
        if (sp <= 0f) return fallbackPx
        val scaled = DesignRuntime.tokens.textSize(sp)
        return scaled.coerceIn(fallbackPx * 0.6f, fallbackPx * 2.4f)
    }

    /**
     * 绘制应用通知角标。
     *
     * 参数 rect 为图标框位置；角标绘制在图标右上角，以反色圆底加数字呈现，
     * 数量超过配置上限时以加号结尾。
     */
    private fun drawEntryBadge(
        canvas: Canvas,
        rect: RectF,
        entry: LauncherEntry,
    ) {
        if (isTransparent(entry)) return
        val text = badgeFor(entry) ?: return
        val radius = (rect.width() * 0.16f).coerceAtLeast(tokens.dot * 2f)
        val centerX = rect.right - radius * 0.8f
        val centerY = rect.top + radius * 0.8f
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = InkColors.INK
        canvas.drawCircle(centerX, centerY, radius, fillPaint)
        val paint = DesignRuntime.textPaint(radius * 1.05f, InkColors.PAPER)
        paint.textAlign = Paint.Align.CENTER
        val fm = paint.fontMetrics
        canvas.drawText(text, centerX, centerY - (fm.ascent + fm.descent) / 2f, paint)
    }

    /** 取条目在桌面显示的角标文本，无通知或未启用时返回空。 */
    private fun badgeFor(entry: LauncherEntry): String? {
        val cfg = config ?: return null
        if (!cfg.notifications.badgeEnabled) return null
        val pkg = entry.packageName ?: return null
        // 以通知中心的可视条目为准，合并条目按累计条数计数。
        val count =
            NotificationCenter
                .visibleItems(cfg.notifications)
                .filter { it.packageName == pkg && !it.isSummary }
                .sumOf { it.count }
        if (count <= 0) return null
        val max = cfg.notifications.badgeMax.coerceAtLeast(1)
        return if (count > max) "$max+" else count.toString()
    }

    /** 首页右滑进入负一屏是否启用。 */
    private fun negativePageEnabled(): Boolean = config?.desktop?.negativePage?.enabled == true

    /** 图标缺失时的占位绘制：特殊入口画内置图形，应用画首字符。 */
    private fun drawFallbackGlyph(
        canvas: Canvas,
        rect: RectF,
        entry: LauncherEntry,
    ) {
        val glyph =
            when (entry.special) {
                SpecialEntry.BOOKSHELF, SpecialEntry.WEREAD -> InkGlyphs.Kind.BOOK
                SpecialEntry.FILES -> InkGlyphs.Kind.FOLDER
                SpecialEntry.SEARCH -> InkGlyphs.Kind.SEARCH
                SpecialEntry.SETTINGS -> InkGlyphs.Kind.GEAR
                null -> null
            }
        if (glyph != null) {
            glyphPaint.style = Paint.Style.STROKE
            glyphPaint.strokeWidth = tokens.glyphStroke.toFloat()
            glyphPaint.color = InkColors.INK_3
            InkGlyphs.draw(canvas, glyph, rect.centerX(), rect.centerY(), rect.width() * 0.58f, glyphPaint)
            return
        }
        val letter = entry.label.firstOrNull()?.toString() ?: "?"
        val paint = DesignRuntime.textPaint(rect.width() * 0.46f, InkColors.INK_3)
        paint.textAlign = Paint.Align.CENTER
        val fm = paint.fontMetrics
        val baseline = rect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(letter, rect.centerX(), baseline, paint)
    }

    /** 绘制带白色描边的标签文字，先描边后填字。 */
    private fun drawLabelText(
        canvas: Canvas,
        text: String,
        x: Float,
        baseline: Float,
        align: Paint.Align,
    ) {
        labelPaint.typeface = DesignRuntime.typeface()
        labelPaint.textAlign = align
        labelPaint.style = Paint.Style.FILL
        labelPaint.color = InkColors.INK
        labelOutlinePaint.typeface = labelPaint.typeface
        labelOutlinePaint.textSize = labelPaint.textSize
        labelOutlinePaint.textAlign = align
        labelOutlinePaint.style = Paint.Style.STROKE
        labelOutlinePaint.color = InkColors.PAPER
        labelOutlinePaint.strokeWidth = maxOf(1f, labelPaint.textSize * LABEL_OUTLINE_RATIO)
        canvas.drawText(text, x, baseline, labelOutlinePaint)
        canvas.drawText(text, x, baseline, labelPaint)
    }

    /** 绘制页码指示点，实心点表示当前页。 */
    private fun drawPageIndicator(
        canvas: Canvas,
        m: DesktopMetrics,
        cfg: LauncherConfig,
    ) {
        val pages = m.pageCount(entries.size)
        if (!cfg.desktop.showPageIndicator || pages <= 1) return
        if (pages > 7) {
            val paint = DesignRuntime.textPaint(tokens.textSmall, InkColors.INK)
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${currentPage + 1} / $pages", width / 2f, (height - tokens.pageBottom).toFloat(), paint)
            return
        }
        val dot = maxOf(tokens.dot, tokens.dp(4f))
        val dotGap = tokens.dotGap
        val totalWidth = pages * dot + (pages - 1) * dotGap
        var cx = width / 2f - totalWidth / 2f + dot / 2f
        val cy = height - tokens.pageBottom - tokens.indicatorHeight / 2f
        fillPaint.style = Paint.Style.FILL
        // 先铺一条白色胶囊底，避免页码点落在壁纸花纹上不可见。
        fillPaint.color = InkColors.PAPER
        val left = width / 2f - totalWidth / 2f
        val pad = dotGap
        canvas.drawRoundRect(
            left - pad,
            cy - dot * 1.1f,
            left + totalWidth + pad,
            cy + dot * 1.1f,
            dot * 1.1f,
            dot * 1.1f,
            fillPaint,
        )
        for (page in 0 until pages) {
            fillPaint.color = if (page == currentPage) InkColors.INK else InkColors.INK_4
            canvas.drawCircle(cx, cy, dot / 2f, fillPaint)
            cx += dot + dotGap
        }
    }

    /** 整理模式提示条：顶部黑底白字。 */
    private fun drawEditBanner(canvas: Canvas) {
        val height = tokens.textCaption + tokens.gap * 2
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = InkColors.INK
        canvas.drawRect(0f, 0f, width.toFloat(), height, fillPaint)
        val paint = DesignRuntime.textPaint(tokens.textCaption, InkColors.PAPER)
        paint.textAlign = Paint.Align.CENTER
        val fm = paint.fontMetrics
        val baseline = height / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText("整理模式：拖动排序，点击图标打开菜单，点击空白退出", width / 2f, baseline, paint)
    }

    /** 单行省略，超出宽度时截断并追加省略号。 */
    private fun ellipsize(
        text: CharSequence,
        paint: Paint,
        maxWidth: Float,
    ): String {
        val raw = text.toString()
        if (maxWidth <= 0f) return ""
        if (paint.measureText(raw) <= maxWidth) return raw
        val ellipsis = "…"
        var end = raw.length
        while (end > 0 && paint.measureText(raw.substring(0, end) + ellipsis) > maxWidth) {
            end--
        }
        return raw.substring(0, end) + ellipsis
    }

    // endregion

    // region 触摸与手势

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 预览视图只用于展示，不响应触摸、长按与手势。
        if (previewMode) return false
        if (folderDialog != null) return true
        if (noteOverlay.visible) return noteOverlay.onTouchEvent(event)
        if (immersive) return handleImmersiveTouch(event, metrics ?: return false)
        val m = metrics ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return handleDown(event, m)
            MotionEvent.ACTION_MOVE -> {
                handleMove(event, m)
                return true
            }
            MotionEvent.ACTION_UP -> {
                handleUp(event, m, cancelled = false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handleUp(event, m, cancelled = true)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun canOpenNegativePage(
        cancelled: Boolean,
        dx: Float,
        horizontal: Boolean,
    ): Boolean {
        val rightFromHome = currentPage == 0 && dx > 0 && horizontal
        return !cancelled && rightFromHome && negativePageEnabled()
    }

    /** 沉浸书封拥有内部切书；封外横滑由宿主拥有，抽屉独占触摸。 */
    private fun handleImmersiveTouch(
        event: MotionEvent,
        m: DesktopMetrics,
    ): Boolean {
        updateLandscapeGeometry()
        if (immersiveHome.drawerOpen) return immersiveHome.onTouch(event)
        val rect = widgetBounds(m)
        val x = event.x - rect.left
        val y = event.y - rect.top
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> immersiveDown(event, x, y)
            MotionEvent.ACTION_MOVE -> immersiveMove(event, x, y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> immersiveEnd(event, x, y)
        }
        return true
    }

    private fun immersiveDown(
        event: MotionEvent,
        x: Float,
        y: Float,
    ) {
        downX = event.x
        downY = event.y
        widgetLocalX = x
        widgetLocalY = y
        widgetLongPressFired = false
        widgetGestureConsumed = false
        widgetGestureMoved = false
        immersiveNavigationOwned = false
        // 书页区域内的横滑全部交给组件轮播，宿主只在书页之外的时钟与栏区接管导航。
        val book = immersiveBookBounds()
        immersiveRailTouch = !book.contains(event.x, event.y)
        immersiveNavigationCandidate = immersiveRailTouch
        if (immersiveRailTouch) {
            immersiveHome.onTouch(event)
        } else {
            widget?.onTouch(WidgetTouchAction.DOWN, x, y)
        }
        handler.postDelayed(widgetLongPressRunnable, longPressMenuMs())
    }

    private fun immersiveMove(
        event: MotionEvent,
        x: Float,
        y: Float,
    ) {
        val dx = event.x - downX
        val dy = event.y - downY
        if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
            widgetGestureMoved = true
            handler.removeCallbacks(widgetLongPressRunnable)
        }
        if (widgetLongPressFired) return
        if (canOwnImmersiveNavigation(dx, dy)) {
            immersiveNavigationOwned = true
            if (!immersiveRailTouch) widget?.onTouch(WidgetTouchAction.CANCEL, x, y)
        }
        if (!immersiveNavigationOwned && !immersiveRailTouch) {
            widget?.onTouch(WidgetTouchAction.MOVE, x, y)
        }
    }

    private fun canOwnImmersiveNavigation(
        dx: Float,
        dy: Float,
    ): Boolean {
        val available = immersiveNavigationCandidate && !immersiveNavigationOwned
        return available && immersiveRightSwipe(dx, dy) && negativePageEnabled()
    }

    private fun immersiveRightSwipe(
        dx: Float,
        dy: Float,
    ): Boolean = dx >= swipeMinPx() && abs(dx) > abs(dy)

    private fun immersiveEnd(
        event: MotionEvent,
        x: Float,
        y: Float,
    ) {
        handler.removeCallbacks(widgetLongPressRunnable)
        val cancelled = event.actionMasked == MotionEvent.ACTION_CANCEL
        if (immersiveNavigationOwned) {
            finishImmersiveNavigation(event, cancelled)
        } else if (!widgetLongPressFired) {
            finishImmersiveContent(event, cancelled, x, y)
        }
        immersiveNavigationOwned = false
        immersiveNavigationCandidate = false
        invalidate()
    }

    private fun finishImmersiveNavigation(
        event: MotionEvent,
        cancelled: Boolean,
    ) {
        val allowed = !cancelled && !widgetLongPressFired
        if (allowed && immersiveRightSwipe(event.x - downX, event.y - downY)) {
            onNegativePageRequested?.invoke()
        }
    }

    private fun finishImmersiveContent(
        event: MotionEvent,
        cancelled: Boolean,
        x: Float,
        y: Float,
    ) {
        val click = !cancelled && !widgetGestureMoved
        if (immersiveRailTouch) {
            if (click) immersiveHome.onTouch(event)
        } else {
            widget?.onTouch(if (cancelled) WidgetTouchAction.CANCEL else WidgetTouchAction.UP, x, y)
            if (click) clickImmersiveWidget(x, y)
        }
    }

    private fun clickImmersiveWidget(
        x: Float,
        y: Float,
    ) {
        val view = widget
        if (view?.hitRegions().orEmpty().isEmpty() || view?.hitRegionAt(x, y) != null) {
            view?.onClick(x, y)
        }
    }

    /** 按下：判定命中组件、入口或空白，并启动长按计时。 */
    private fun handleDown(
        event: MotionEvent,
        m: DesktopMetrics,
    ): Boolean {
        downX = event.x
        downY = event.y
        downTime = event.downTime
        gestureAxis = 0
        verticalConsumed = false
        widgetLongPressFired = false
        widgetGestureConsumed = false
        widgetGestureMoved = false
        dragMoved = false
        // 新一次按下清除上一次长按进入整理模式的标记，避免后续单击被误判为长按。
        editHoldTriggered = false
        pressedIndex = -1

        if (editing) {
            val hit = entryIndexAt(m, currentPage, event.x, event.y)
            pressedIndex = hit
            dragIndex = if (hit >= 0) hit else -1
            if (hit >= 0) invalidate()
            return true
        }

        val widgetRect = widgetBounds(m)
        widgetTouch = (immersive || !m.widgetRect(currentPage).isEmpty) && widgetRect.contains(event.x, event.y)
        if (widgetTouch) {
            widgetLocalX = event.x - widgetRect.left
            widgetLocalY = event.y - widgetRect.top
            handler.postDelayed(widgetLongPressRunnable, longPressMenuMs())
            // 按下同时转发给组件，组件据此开始触摸序列；返回值不改变桌面按下流程。
            widget?.onTouch(WidgetTouchAction.DOWN, widgetLocalX, widgetLocalY)
            return true
        }

        val hit = entryIndexAt(m, currentPage, event.x, event.y)
        pressedIndex = hit
        if (hit >= 0) {
            // 按住超过整理阈值进入拖动模式；在此之前的抬手动作为单条菜单。
            handler.postDelayed(enterEditRunnable, editHoldMs())
        }
        invalidate()
        return true
    }

    /** 移动：分派横向翻页、纵向列表翻页、整理模式拖动。 */
    private fun handleMove(
        event: MotionEvent,
        m: DesktopMetrics,
    ) {
        if (editing) {
            // 长按进入整理模式后手指仍可能继续移动，命中入口即按拖动处理。
            if (dragIndex >= 0) {
                if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) {
                    dragMoved = true
                    val target = entryIndexAt(m, currentPage, event.x, event.y)
                    if (target >= 0 && target != dragIndex) moveEntry(dragIndex, target)
                }
            }
            return
        }
        if (widgetTouch) {
            handleWidgetMove(event, m)
            return
        }

        val dx = event.x - downX
        val dy = event.y - downY
        if (gestureAxis == 0) {
            if (abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                gestureAxis = 1
                handler.removeCallbacks(enterEditRunnable)
            } else if (abs(dy) > touchSlop) {
                gestureAxis = 2
                handler.removeCallbacks(enterEditRunnable)
            }
        }
        when (gestureAxis) {
            // 横向翻页在抬起时统一判定，滑动过程不绘制预览，避免墨水屏反复重绘。
            1 -> Unit
            2 -> {
                if (verticalConsumed) return
                val minPx = swipeMinPx()
                if (abs(dy) < minPx) return
                verticalConsumed = true
                if (m.isList) {
                    if (dy < 0) nextPage(m) else previousPage(m)
                }
            }
        }
    }

    private fun handleWidgetMove(
        event: MotionEvent,
        m: DesktopMetrics,
    ) {
        // 先交组件识别内部手势；未消费的横滑由宿主接回桌面翻页。
        val rect = widgetBounds(m)
        widgetLocalX = event.x - rect.left
        widgetLocalY = event.y - rect.top
        // 位移超过系统触摸阈值即视为组件手势已开始，抬手不再转发点击
        if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) {
            widgetGestureMoved = true
            handler.removeCallbacks(widgetLongPressRunnable)
        }
        val view = widget
        if (widgetLongPressFired || gestureAxis == 1) return
        val consumed = view?.onTouch(WidgetTouchAction.MOVE, widgetLocalX, widgetLocalY) == true
        widgetGestureConsumed = widgetGestureConsumed || consumed
        val dx = event.x - downX
        val dy = event.y - downY
        if (!widgetGestureConsumed && abs(dx) >= swipeMinPx() && abs(dx) > abs(dy)) {
            view?.onTouch(WidgetTouchAction.CANCEL, widgetLocalX, widgetLocalY)
            gestureAxis = 1
        }
    }

    /** 抬起：判定点击、滑动翻页、整理模式菜单或退出。 */
    private fun handleUp(
        event: MotionEvent,
        m: DesktopMetrics,
        cancelled: Boolean,
    ) {
        handler.removeCallbacks(enterEditRunnable)
        handler.removeCallbacks(widgetLongPressRunnable)
        val dx = event.x - downX
        val dy = event.y - downY
        val moved = abs(dx) > touchSlop || abs(dy) > touchSlop
        val duration = event.eventTime - downTime

        if (widgetTouch && gestureAxis == 1) {
            widgetTouch = false
            // 组件在接管时已收到CANCEL，下面复用桌面翻页逻辑。
        }
        if (widgetTouch) {
            widgetTouch = false
            val rect = widgetBounds(m)
            val localX = event.x - rect.left
            val localY = event.y - rect.top
            widgetLocalX = localX
            widgetLocalY = localY
            val view = widget
            // 先结束组件触摸序列，再判定点击，两者不复用同一次抬起。
            val endAction =
                if (cancelled || widgetLongPressFired) WidgetTouchAction.CANCEL else WidgetTouchAction.UP
            view?.onTouch(endAction, localX, localY)
            val tapEligible = !cancelled && !moved && !widgetGestureMoved && !widgetLongPressFired
            if (tapEligible) {
                // 声明了命中区域的组件只在区域内转发点击；未声明区域的组件保持整卡转发。
                val declared = view?.hitRegions().orEmpty().isNotEmpty()
                val hit = view?.hitRegionAt(localX, localY)
                if (!declared || hit != null) {
                    view?.onClick(localX, localY)
                }
            }
            invalidate()
            return
        }

        if (editing) {
            if (!cancelled && !dragMoved && !editHoldTriggered) {
                val hit = entryIndexAt(m, currentPage, event.x, event.y)
                if (hit >= 0) showEntryMenu(hit) else exitEditMode()
            } else if (!cancelled && dragMoved) {
                persistOrder()
            }
            dragIndex = -1
            dragMoved = false
            editHoldTriggered = false
            pressedIndex = -1
            invalidate()
            return
        }

        if (gestureAxis == 1) {
            val minPx = swipeMinPx()
            val horizontal = abs(dx) >= minPx && abs(dx) > abs(dy)
            // 首页继续右滑且启用负一屏时跳转。
            if (canOpenNegativePage(cancelled, dx, horizontal)) {
                pressedIndex = -1
                invalidate()
                onNegativePageRequested?.invoke()
                return
            }
            if (!cancelled && horizontal) {
                if (dx < 0) nextPage(m) else previousPage(m)
            }
            // 首尾边界只做重绘，翻页本身已在页方法内请求整屏刷新。
            pressedIndex = -1
            invalidate()
            return
        }

        if (!cancelled && !moved) {
            if (duration >= longPressMenuMs()) {
                if (pressedIndex >= 0 && pressedIndex < entries.size) {
                    showEntryMenu(pressedIndex)
                } else {
                    showBlankMenu()
                }
            } else if (pressedIndex >= 0 && pressedIndex < entries.size) {
                launchEntry(entries[pressedIndex])
            }
        }
        pressedIndex = -1
        invalidate()
    }

    /** 计算触点对应的全局入口下标，未命中返回 -1。 */
    private fun entryIndexAt(
        m: DesktopMetrics,
        page: Int,
        x: Float,
        y: Float,
    ): Int {
        val slot = m.slotAt(page, x, y)
        if (slot < 0) return -1
        val index = m.startIndex(page) + slot
        return if (index in entries.indices) index else -1
    }

    /** 翻到下一页，成功时请求局部重绘并返回 true。 */
    private fun nextPage(m: DesktopMetrics): Boolean {
        val pages = m.pageCount(entries.size)
        if (currentPage >= pages - 1) return false
        noteOverlay.close()
        currentPage++
        updateWidgetVisibility()
        pressedIndex = -1
        invalidate()
        requestPartial()
        return true
    }

    /** 翻到上一页，成功时请求局部重绘并返回 true。 */
    private fun previousPage(m: DesktopMetrics): Boolean {
        if (currentPage <= 0) return false
        noteOverlay.close()
        currentPage--
        updateWidgetVisibility()
        pressedIndex = -1
        invalidate()
        requestPartial()
        return true
    }

    /** 当前配置的单条菜单长按时长，单位毫秒。 */
    private fun longPressMenuMs(): Long {
        val configured = config?.desktop?.longPressMenuMs ?: DEFAULT_LONG_PRESS_MENU_MS
        return configured.coerceAtLeast(MIN_LONG_PRESS_MENU_MS).toLong()
    }

    /**
     * 当前配置的整理模式触发时长，单位毫秒。
     *
     * 该值必须明显大于菜单阈值，否则两次长按语义会重叠，
     * 因此下界取菜单阈值加 [MIN_EDIT_GAP_MS]。
     */
    private fun editHoldMs(): Long {
        val configured = config?.desktop?.editHoldMs ?: DEFAULT_EDIT_HOLD_MS
        val lower = (longPressMenuMs() + MIN_EDIT_GAP_MS)
        return configured.toLong().coerceAtLeast(lower)
    }

    /** 当前配置的最小滑动距离，单位像素。 */
    private fun swipeMinPx(): Int {
        val dp = config?.desktop?.swipeMinDp ?: DEFAULT_SWIPE_MIN_DP
        return tokens.dp(dp.toFloat()).coerceAtLeast(touchSlop)
    }

    // endregion

    // region 交互动作

    /** 桌面与沉浸抽屉共用的文件夹窗口，不改变抽屉开关或页码。 */
    private fun openFolder(folderId: String) {
        if (previewMode || config?.desktop?.folders?.none { it.id == folderId } != false) return
        closeFolder()
        handler.removeCallbacks(enterEditRunnable)
        handler.removeCallbacks(widgetLongPressRunnable)
        noteOverlay.close()
        val overlay = FolderOverlay(context)
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setContentView(overlay)
        openFolderId = folderId
        folderOverlay = overlay
        folderDialog = dialog
        overlay.drawIcon = ::drawIconFrame
        overlay.onDismissRequested = { closeFolder() }
        overlay.onAddRequested = { id -> openAppPicker(id, emptyList()) }
        overlay.onMemberLongPress = { member -> showFolderMemberMenu(folderId, member) }
        overlay.onLaunch = { member ->
            launchEntry(member)
            closeFolder()
        }
        dialog.setOnDismissListener {
            if (folderDialog === dialog) {
                folderDialog = null
                folderOverlay = null
                openFolderId = null
                updateWidgetVisibility()
                invalidate()
                requestPartial()
            }
        }
        bindOpenFolder()
        updateWidgetVisibility()
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0f)
            setGravity(Gravity.CENTER)
            setLayout(
                minOf(resources.displayMetrics.widthPixels - tokens.dp(32f), tokens.dp(420f)).coerceAtLeast(1),
                minOf(this@HomeView.height - tokens.dp(32f), tokens.dp(600f)).coerceAtLeast(1),
            )
        }
        requestPartial()
    }

    private fun closeFolder(): Boolean {
        val dialog = folderDialog ?: return false
        folderDialog = null
        folderOverlay = null
        openFolderId = null
        dialog.dismiss()
        updateWidgetVisibility()
        invalidate()
        requestPartial()
        return true
    }

    private fun bindOpenFolder() {
        val id = openFolderId ?: return
        val folder = config?.desktop?.folders?.firstOrNull { it.id == id }
        folderOverlay?.bind(
            folder,
            editableMembers[id].orEmpty(),
            onRename = { name -> renameFolder(id, name) },
            onDissolve = { requestFolderDissolve(id) },
            unavailableKeys =
                editableMembers[id]
                    .orEmpty()
                    .filterNot { resolveEntryApp(it, apps) != null }
                    .map { it.key }
                    .toSet(),
        )
    }

    private fun requestFolderRename(folderId: String) {
        val folder =
            AppServices.config.state.value.desktop.folders
                .firstOrNull { it.id == folderId } ?: return
        InkDialogs.input(
            context,
            "改名",
            initial = folder.name,
            requestIme = true,
            submit = { name -> renameFolder(folderId, name) },
        )
    }

    /**
     * 提交文件夹改名。
     *
     * 名称为空或写入失败时返回假，输入框据此保留当前文本与对话框，供修正后重试；
     * 写入失败同时给出提示，避免把草稿误认为已经保存。
     */
    private fun renameFolder(
        folderId: String,
        name: String,
    ): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            InkDialogs.toast(context, "名称不能为空")
            return false
        }
        return applyFolderOperation { source -> FolderRules.rename(source, folderId, trimmed) }
    }

    private fun requestFolderDissolve(folderId: String) {
        InkDialogs.confirm(
            context,
            "解散文件夹",
            "解散后，应用将回到桌面。",
            confirmLabel = "解散",
            onConfirm = {
                applyFolderOperation { source -> FolderRules.dissolve(source, folderId) }
            },
        )
    }

    private fun applyFolderOperation(operation: (DesktopConfig) -> FolderResult?): Boolean {
        val source = AppServices.config.state.value.desktop
        val result = operation(source)
        return when {
            result == null -> false
            FolderStore.apply(result, source) != null -> {
                InkDialogs.toast(context, "保存失败，请重试")
                false
            }
            else -> {
                // 只在事务已发布后读取权威配置；失败不预先改标题、成员或本地入口。
                applyData(AppServices.catalog.apps.value, AppServices.config.state.value)
                requestPartial()
                true
            }
        }
    }

    /**
     * 在目录快照与旧键解析就绪后执行一次自动成组，并排队处理旧包级引用确认。
     *
     * 只处理真实刷新后的目录快照；预览模式、存在其他浮层或已有判定在途时直接返回。
     * 写盘失败不记录处理状态，下一轮应用数据时重新判定。
     */
    private fun maybeProcessAutoGroup() {
        if (!canProcessAutoGroup()) return
        autoGroupInFlight = true
        try {
            processAutoGroupStep()
        } finally {
            autoGroupInFlight = false
        }
    }

    /**
     * 判定当前快照是否具备自动成组条件。
     *
     * 预览模式、已有判定在途、确认浮层打开、存在其他浮层或旧键尚未解析完成时都不执行，
     * 避免在迁移窗口内按旧键建立错误的组。
     */
    private fun canProcessAutoGroup(): Boolean =
        !previewMode && !autoGroupInFlight && !legacyConfirmOpen && catalogReady() && legacyKeysResolved()

    /** 判定目录快照已就绪且没有浮层占用交互。 */
    private fun catalogReady(): Boolean = apps.isNotEmpty() && overlaysClosed()

    /**
     * 判定旧包级入口键解析是否已完成。
     *
     * 解析完成前桌面顺序仍是旧键，归组会消费这些键并写入处理状态，
     * 使旧引用确认浮层不再出现；解析由配置流发布后本方法才返回真。
     */
    private fun legacyKeysResolved(): Boolean =
        runCatching { AppServices.config.state.value.desktop.componentKeyMigrated }.getOrDefault(false)

    /** 判定当前没有文件夹浮层、应用选择浮层或文件夹选择浮层占用交互。 */
    private fun overlaysClosed(): Boolean = folderDialog == null && appPicker == null && folderPickerDialog == null

    /**
     * 取当前实际呈现的入口顺序，供归组与旧引用替换定位。
     *
     * 入口列表已按排序模式与用户顺序组装，包含文件夹与特殊入口键；
     * 归组据此把新文件夹放到成员原本的显示位置，其他条目相对顺序不变。
     */
    private fun visibleOrder(): List<String> = entries.map { it.key }

    /**
     * 执行一次自动成组或旧引用确认。
     *
     * 先按当前目录整理同包多入口，成功写入后由配置流触发下一轮应用数据；没有需要整理的包时
     * 取出第一个待确认旧引用并显示确认浮层，一次只处理一个包。
     */
    private fun processAutoGroupStep() {
        val desktop = AppServices.config.state.value.desktop
        val labels = applicationLabels(apps)
        val expected = FolderAutoGroup.run(desktop, apps, labels, visibleOrder = visibleOrder())
        if (expected != null) {
            FolderStore.applyAutoGroup(expected, desktop)
        } else {
            FolderAutoGroup.pendingLegacy(desktop, apps, labels).firstOrNull()?.let { pending ->
                legacyConfirmOpen = true
                showLegacyGroupConfirm(pending, labels)
            }
        }
    }

    /**
     * 读取应用级名称，供文件夹命名使用。
     *
     * 参数 apps 为目录快照；包管理器读取失败的包不进入映射，由规则回退组件名称。
     */
    private fun applicationLabels(apps: List<InstalledApp>): Map<String, String> {
        val labels = LinkedHashMap<String, String>()
        for (packageName in apps.map { it.packageName }.distinct()) {
            val label = runCatching { AppServices.catalog.applicationLabel(packageName) }.getOrNull()
            if (!label.isNullOrBlank()) labels[packageName] = label
        }
        return labels
    }

    /**
     * 显示旧包级引用的转换确认。
     *
     * 一次只处理一个包；确认与暂不转换都会写入一次决定，写盘失败时保留待确认状态供下一轮重试。
     */
    private fun showLegacyGroupConfirm(
        pending: FolderAutoGroup.LegacyPending,
        labels: Map<String, String>,
    ) {
        val message =
            buildString {
                append("旧配置中存在无法区分具体页面的应用入口，可整理为「")
                append(pending.folderName)
                append("」文件夹。\n\n")
                append("确认后按当前安装的 ")
                append(pending.entryCount)
                append(" 个入口建立文件夹，位置保持在桌面当前顺序；")
                if (pending.hostFolderId != null) {
                    append("原入口位于「")
                    append(pending.hostFolderName.orEmpty())
                    append("」文件夹内时，将移出该文件夹并在其后新建顶层文件夹。\n\n")
                } else {
                    append("原入口原位替换为文件夹。\n\n")
                }
                append("旧记录无法还原原先选择的页面，暂不转换则保留原样，之后可在整理模式手动处理。")
                append("整理后可随时改名、移出或解散，不会再次自动整理。")
            }
        InkDialogs.confirm(
            context,
            "整理旧应用入口",
            message,
            confirmLabel = "确认整理",
            cancelLabel = "暂不转换",
            onConfirm = { resolveLegacyGroup(pending, accepted = true, labels = labels) },
            onCancel = { resolveLegacyGroup(pending, accepted = false, labels = labels) },
        )
    }

    /**
     * 写入一次旧包级引用的决定，并继续处理下一个待确认的包。
     *
     * 参数 accepted 为真表示转换为组件文件夹，为假表示记录暂不转换；写盘失败时提示并保留状态。
     */
    private fun resolveLegacyGroup(
        pending: FolderAutoGroup.LegacyPending,
        accepted: Boolean,
        labels: Map<String, String>,
    ) {
        legacyConfirmOpen = false
        val desktop = AppServices.config.state.value.desktop
        val expected =
            FolderAutoGroup.resolveLegacy(
                desktop,
                apps,
                pending.packageName,
                accepted,
                labels,
                visibleOrder = visibleOrder(),
            )
        if (expected == null) return
        if (!FolderStore.applyAutoGroup(expected, desktop)) {
            InkDialogs.toast(context, "保存失败，稍后重试")
            return
        }
        maybeProcessAutoGroup()
    }

    /**
     * 重新发起一次已拒绝的旧包级引用整理。
     *
     * 参数 packageName 为要重新处理的包名。先把该包决定从已拒绝恢复为待确认并写盘，
     * 写入成功后复用确认浮层；取消确认时布局保持不变，状态仍可再次主动发起。
     */
    private fun reopenDeclinedLegacy(packageName: String) {
        val desktop = AppServices.config.state.value.desktop
        val reopened = FolderAutoGroup.reopenDeclined(desktop, packageName) ?: return
        AppServices.config.edit { config -> config.copy(desktop = reopened) }
        val labels = applicationLabels(apps)
        val pending =
            FolderAutoGroup
                .pendingLegacy(AppServices.config.state.value.desktop, apps, labels)
                .firstOrNull { it.packageName == packageName }
        if (pending != null) {
            legacyConfirmOpen = true
            showLegacyGroupConfirm(pending, labels)
        }
    }

    private fun openAppPicker(
        targetFolderId: String?,
        preselected: List<String>,
    ) {
        val desktop = AppServices.config.state.value.desktop
        val target = desktop.folders.firstOrNull { it.id == targetFolderId }
        if (targetFolderId != null && target == null) return
        val apps = AppServices.catalog.apps.value
        val picker = AppPickerOverlay(context)
        picker.candidates = FolderAppSelection.candidates(apps, desktop.folders, desktop.hiddenPackages, targetFolderId)
        picker.drawIcon = ::drawIconFrame
        appPicker?.dismiss()
        appPicker = picker
        picker.open(
            targetFolder = target,
            newFolderName = "文件夹",
            preselected = preselected,
            existingFolders = desktop.folders,
            apps =
                apps.map {
                    LauncherEntry(
                        key = EntryKeys.appEntryKey(it),
                        type = EntryType.APP,
                        label = it.label,
                        packageName = it.packageName,
                        activityName = it.activityName,
                    )
                },
            onCancel = { if (appPicker === picker) appPicker = null },
            onSubmit = { keys, name ->
                if (submitFolderMembers(targetFolderId, keys, name)) {
                    picker.dismiss()
                    if (appPicker === picker) appPicker = null
                    closeFolder()
                }
            },
        )
    }

    private fun closeFolderPickers() {
        appPicker?.dismiss()
        appPicker = null
        folderPickerDialog?.dismiss()
        folderPickerDialog = null
    }

    private fun requestMoveIntoFolder(entry: LauncherEntry) {
        folderPickerDialog?.dismiss()
        folderPickerDialog =
            FolderPickerDialog(context).open(
                AppServices.config.state.value.desktop.folders,
            ) { folder ->
                if (folder == null) {
                    openAppPicker(null, listOf(entry.key))
                    true
                } else {
                    submitFolderMembers(folder.id, listOf(entry.key), null)
                }
            }
    }

    private fun submitFolderMembers(
        targetFolderId: String?,
        keys: List<String>,
        name: String?,
    ): Boolean {
        val apps = AppServices.catalog.apps.value
        val (valid, invalidCount) = FolderAppSelection.revalidate(keys, apps.map { it.packageName }.toSet())
        if (invalidCount > 0) InkDialogs.toast(context, "已跳过 $invalidCount 个失效应用")
        if (valid.isEmpty()) {
            InkDialogs.toast(context, "没有可添加的应用")
            return false
        }
        // Captures the submission-time desktop and performs the sole Store.apply.
        return applyFolderOperation { source ->
            val snapshot = FolderAppSelection.candidates(apps, source.folders, source.hiddenPackages, targetFolderId)
            val eligible = FolderAppSelection.selectedKeys(snapshot, valid.toSet(), targetFolderId).toSet()
            // Keep the original picker order even if labels changed while it was open.
            val ordered = valid.filter { it in eligible }
            val plan = FolderAppSelection.plan(snapshot, valid.toSet(), targetFolderId)
            if (ordered.isEmpty()) {
                InkDialogs.toast(context, "没有可添加的应用")
                return@applyFolderOperation null
            }
            InkDialogs.toast(
                context,
                listOfNotNull(
                    if (plan.added > 0) "添加 ${plan.added} 个" else null,
                    if (plan.moved > 0) "移动 ${plan.moved} 个" else null,
                ).joinToString("，"),
            )
            if (targetFolderId == null) {
                FolderRules.create(source, name.orEmpty().trim(), ordered, FolderRules.nextFolderId(source))
            } else {
                FolderRules.addMembers(source, targetFolderId, ordered)
            }
        }
    }

    /**
     * 启动入口；应用入口交给应用目录，文件夹打开窗口，特殊入口交给内置处理。
     *
     * 应用入口按组件精确身份解析，失效入口给出失败提示，不改用同包其他入口；
     * 启动结果由应用目录返回，失败时同样提示，便于用户区分点击无效与系统拒绝。
     */
    private fun launchEntry(entry: LauncherEntry) {
        if (entry.isFolder) {
            entry.folder?.id?.let(::openFolder)
        } else if (!entry.isApp) {
            entry.special?.let { AppServices.catalog.launchSpecial(it) }
        } else {
            launchAppEntry(entry)
        }
    }

    /** 启动应用入口；失效或系统拒绝时提示，不改用同包其他入口。 */
    private fun launchAppEntry(entry: LauncherEntry) {
        val app = resolveEntryApp(entry, apps)
        if (app == null) {
            InkDialogs.toast(context, "启动失败，请重试")
            return
        }
        // 启动结果为假说明系统拒绝或目标 Activity 已失效，只提示，不改用同包其他入口。
        val launched = runCatching { AppServices.catalog.launch(app) }.getOrDefault(false)
        if (!launched) InkDialogs.toast(context, "启动失败，请重试")
    }

    /** 进入整理模式，参数 index 为按下时命中的入口。 */
    private fun enterEditMode(index: Int) {
        if (!editing && immersive) {
            editReturnPage = currentPage
            editHoldTriggered = true
            immersiveHome.closeDrawer()
            currentPage = 0
        }
        editing = true
        config?.let { ensureWidget(it) }
        updateWidgetVisibility()
        dragIndex = index
        pressedIndex = index
        dragMoved = false
        invalidate()
        requestPartial()
    }

    /** 退出整理模式并恢复普通桌面。 */
    private fun exitEditMode() {
        editing = false
        if (immersive) currentPage = editReturnPage
        config?.let { ensureWidget(it) }
        updateWidgetVisibility()
        dragIndex = -1
        pressedIndex = -1
        dragMoved = false
        editHoldTriggered = false
        invalidate()
        requestPartial()
    }

    /** 本地移动入口，用于拖动过程中的即时反馈。 */
    private fun moveEntry(
        from: Int,
        to: Int,
    ) {
        if (from !in entries.indices || to !in entries.indices) return
        val list = entries.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        entries = list
        dragIndex = to
        pressedIndex = to
        invalidate()
    }

    /** 拖动结束后把当前顺序写回配置。 */
    private fun persistOrder() {
        val order = entries.map { it.key }
        runCatching {
            AppServices.config.edit { cfg ->
                cfg.copy(desktop = cfg.desktop.copy(order = order, sortMode = SortMode.MANUAL))
            }
        }
    }

    /** 把入口置顶：写入自定义顺序首位。 */
    private fun pinEntry(entry: LauncherEntry) {
        runCatching {
            AppServices.config.edit { cfg ->
                val order = ArrayList<String>()
                order.add(entry.key)
                cfg.desktop.order.forEach { if (it != entry.key) order.add(it) }
                cfg.copy(desktop = cfg.desktop.copy(order = order, sortMode = SortMode.MANUAL))
            }
        }
    }

    /** 隐藏应用：把包名写入隐藏集合。 */
    private fun hideEntry(packageName: String) {
        runCatching {
            AppServices.config.edit { cfg ->
                cfg.copy(desktop = cfg.desktop.copy(hiddenPackages = cfg.desktop.hiddenPackages + packageName))
            }
        }
    }

    /** 打开系统卸载确认界面。 */
    private fun requestUninstall(packageName: String) {
        AppUninstall.request(context, packageName)
    }

    /** 弹出单条入口的长按菜单。 */
    private fun showEntryMenu(index: Int) {
        val entry = entries.getOrNull(index) ?: return
        when (entry.type) {
            EntryType.FOLDER -> showFolderEntryMenu(entry)
            EntryType.APP -> showAppEntryMenu(entry)
            EntryType.SPECIAL -> showSpecialEntryMenu(entry)
        }
    }

    private fun showFolderEntryMenu(entry: LauncherEntry) {
        val id = entry.folder?.id ?: return
        InkDialogs.choose(context, entry.label, listOf("改名", "解散")) { picked ->
            exitEditMode()
            when (picked) {
                0 -> requestFolderRename(id)
                1 -> requestFolderDissolve(id)
            }
        }
    }

    private fun showAppEntryMenu(entry: LauncherEntry) {
        val labels = ArrayList<CharSequence>()
        val actions = ArrayList<() -> Unit>()
        if (AppServices.config.state.value.desktop.folders
                .none { entry.key in it.memberKeys }
        ) {
            labels.add("移入文件夹")
            actions.add { requestMoveIntoFolder(entry) }
        }
        collectEntryActions(entry, labels, actions)
        showEntryActions(entry, labels, actions)
    }

    private fun showSpecialEntryMenu(entry: LauncherEntry) {
        val labels = ArrayList<CharSequence>()
        val actions = ArrayList<() -> Unit>()
        collectEntryActions(entry, labels, actions)
        showEntryActions(entry, labels, actions)
    }

    private fun showFolderMemberMenu(
        folderId: String,
        entry: LauncherEntry,
    ) {
        val labels = arrayListOf<CharSequence>("移出文件夹")
        val actions = arrayListOf<() -> Unit>({ removeFolderMember(folderId, entry) })
        if (entry.packageName in appsByPackage) {
            collectEntryActions(entry, labels, actions, onPin = { pinFolderMember(folderId, entry) })
        }
        showEntryActions(entry, labels, actions)
    }

    /**
     * 将文件夹成员移到组内首位。
     *
     * 参数 folderId 为目标文件夹标识，entry 为待置顶成员；其余成员保持原顺序。
     * 文件夹不存在或不含该成员时返回失败；一次事务的结果仅更新成员顺序，保留桌面顺序与排序模式。
     */
    private fun pinFolderMember(
        folderId: String,
        entry: LauncherEntry,
    ) {
        applyFolderOperation { source ->
            val target = source.folders.firstOrNull { it.id == folderId }
            if (target == null || entry.key !in target.memberKeys) {
                return@applyFolderOperation FolderResult.Failure(FolderFailure.FOLDER_NOT_FOUND)
            }
            val folders =
                source.folders.map { folder ->
                    if (folder.id == folderId) {
                        folder.copy(memberKeys = listOf(entry.key) + folder.memberKeys.filter { it != entry.key })
                    } else {
                        folder
                    }
                }
            FolderResult.Success(source.copy(folders = folders))
        }
    }

    private fun removeFolderMember(
        folderId: String,
        entry: LauncherEntry,
    ) {
        applyFolderOperation { source ->
            if (entry.packageName in appsByPackage) {
                FolderRules.removeMember(source, folderId, entry.key)
            } else {
                // 已卸载成员只清理文件夹引用，不向桌面顺序追加失效键。
                val folders =
                    source.folders.map { folder ->
                        if (folder.id == folderId) {
                            folder.copy(memberKeys = folder.memberKeys - entry.key)
                        } else {
                            folder
                        }
                    }
                FolderResult.Success(source.copy(folders = folders))
            }
        }
    }

    private fun collectEntryActions(
        entry: LauncherEntry,
        labels: ArrayList<CharSequence>,
        actions: ArrayList<() -> Unit>,
        onPin: (() -> Unit)? = null,
    ) {
        val app = entry.packageName?.let { appsByPackage[it] }
        val targetId = IconTargets.normalizeStoredKey(entry.key)
        labels.add("更换图标")
        actions.add { pickEntryIcon(targetId) }
        // 仅在已设置自定义图标时提供恢复入口，避免出现无效选项。
        if (hasCustomIcon(targetId)) {
            labels.add("恢复默认图标")
            actions.add { restoreDefaultIcon(targetId) }
        }
        if (app != null) {
            labels.add("应用信息")
            actions.add { runCatching { AppServices.router.navigate(Destination.AppInfo(app.packageName)) } }
        }
        if (!immersive) {
            val targetPage = currentPage
            labels.add("选择组件")
            actions.add { openDestination(Destination.PageWidgetPicker(targetPage)) }
        }
        labels.add("置顶")
        actions.add(onPin ?: { pinEntry(entry) })
        if (app != null) {
            labels.add("隐藏")
            actions.add { hideEntry(app.packageName) }
            if (!app.isSystem) {
                labels.add("卸载")
                actions.add { requestUninstall(app.packageName) }
            }
        }
    }

    private fun showEntryActions(
        entry: LauncherEntry,
        labels: List<CharSequence>,
        actions: List<() -> Unit>,
    ) {
        if (labels.isEmpty()) return
        InkDialogs.choose(context, entry.label, labels) { picked ->
            exitEditMode()
            actions.getOrNull(picked)?.invoke()
        }
    }

    /** 弹出空白处长按菜单：更换壁纸、美化模式、进入整理模式、设置与恢复隐藏应用。 */
    private fun showBlankMenu() {
        val labels = ArrayList<CharSequence>()
        val actions = ArrayList<() -> Unit>()
        if (!immersive) {
            labels.add("选择组件")
            actions.add { openDestination(Destination.PageWidgetPicker(currentPage)) }
        }
        labels.add("更换壁纸")
        actions.add { pickWallpaper() }
        labels.add("美化模式")
        actions.add {
            runCatching { AppServices.router.navigate(Destination.Beautify) }
            // 从美化页返回后按新配置重绘，此处先请求局部刷新。
            invalidate()
            requestPartial()
        }
        labels.add("进入整理模式")
        actions.add { enterEditMode(-1) }
        val declined = config?.let { FolderAutoGroup.declinedPackages(it.desktop) }.orEmpty()
        if (declined.isNotEmpty()) {
            labels.add("整理旧应用入口")
            actions.add {
                InkDialogs.choose(context, "整理旧应用入口", declined) { picked ->
                    declined.getOrNull(picked)?.let { reopenDeclinedLegacy(it) }
                }
            }
        }
        labels.add("十雨设置")
        actions.add { runCatching { AppServices.router.navigate(Destination.Settings) } }
        val hidden =
            config
                ?.desktop
                ?.hiddenPackages
                ?.toList()
                .orEmpty()
        if (hidden.isNotEmpty()) {
            labels.add("显示已隐藏应用")
            actions.add { showHiddenMenu(hidden) }
        }
        InkDialogs.choose(context, "桌面", labels) { picked ->
            actions.getOrNull(picked)?.invoke()
        }
    }

    /**
     * 通过文件选择器更换壁纸。
     *
     * 选择器返回图片 Uri 后交给外观服务应用；成功时提示并整屏刷新，
     * 失败或未装配选择器时给出提示。
     */
    private fun pickWallpaper() {
        if (!AppServices.hasPicker()) {
            runCatching { AppServices.router.toast("文件选择器不可用") }
            return
        }
        val opened =
            runCatching {
                AppServices.picker.pickForImport(arrayOf("image/*")) { uri ->
                    if (uri == null) return@pickForImport
                    scope.launch {
                        val applied =
                            runCatching { AppServices.appearance.setWallpaper(uri) }
                                .getOrDefault(false)
                        if (applied) {
                            runCatching { AppServices.router.toast("壁纸已更新") }
                            invalidate()
                            requestFullRefresh()
                        } else {
                            runCatching { AppServices.router.toast("壁纸应用失败") }
                        }
                    }
                }
            }.getOrDefault(false)
        if (!opened) runCatching { AppServices.router.toast("文件选择器不可用") }
    }

    /**
     * 打开图标选择页为指定入口更换图标。
     *
     * 参数 targetId 为应用包名或 special:<名称>；图标来源由设置模块的图标选择页提供，
     * 选中后写入图标覆盖存储并递增覆盖版本，桌面通过版本收集器清空缓存并重绘。
     */
    private fun pickEntryIcon(targetId: String) {
        if (!AppServices.hasIconOverrides()) {
            runCatching { AppServices.router.toast("图标存储不可用") }
            return
        }
        runCatching { AppServices.router.navigate(Destination.IconPicker(targetId)) }
            .onFailure {
                runCatching { AppServices.router.toast("无法打开图标选择页") }
            }
    }

    /** 判断指定入口是否已设置自定义图标。 */
    private fun hasCustomIcon(targetId: String): Boolean =
        runCatching {
            AppServices.hasIconOverrides() &&
                AppServices.iconOverrides.overrides().containsKey(IconTargets.normalizeStoredKey(targetId))
        }.getOrDefault(false)

    /**
     * 恢复指定入口的默认图标。
     *
     * 清除图标覆盖记录并失效该入口的图标缓存，随后局部重绘，
     * 使桌面从应用目录或内置字形重新读取图标，无需整屏刷新。
     */
    private fun restoreDefaultIcon(targetId: String) {
        if (!AppServices.hasIconOverrides()) {
            runCatching { AppServices.router.toast("图标存储不可用") }
            return
        }
        runCatching { AppServices.iconOverrides.clearOverride(targetId) }
        iconStore.invalidate(targetId)
        runCatching { AppServices.router.toast("已恢复默认图标") }
        invalidate()
        requestPartial()
    }

    /** 从隐藏集合中恢复指定应用。 */
    private fun showHiddenMenu(hidden: List<String>) {
        val packages = hidden.sortedBy { appsByPackage[it]?.label ?: it }
        val labels = packages.map { pkg -> appsByPackage[pkg]?.label ?: pkg }
        InkDialogs.choose(context, "已隐藏应用", labels) { picked ->
            val pkg = packages.getOrNull(picked) ?: return@choose
            runCatching {
                AppServices.config.edit { cfg ->
                    cfg.copy(desktop = cfg.desktop.copy(hiddenPackages = cfg.desktop.hiddenPackages - pkg))
                }
            }
        }
    }

    // endregion

    companion object {
        /** 默认单条菜单长按时长，与配置默认值一致。 */
        private const val DEFAULT_LONG_PRESS_MENU_MS = 400

        /** 单条菜单长按时长下限，避免配置异常时轻点即弹菜单。 */
        private const val MIN_LONG_PRESS_MENU_MS = 200

        /** 默认整理模式触发时长，与配置默认值一致。 */
        private const val DEFAULT_EDIT_HOLD_MS = 1200

        /** 整理模式与单条菜单之间的最小时长间隔。 */
        private const val MIN_EDIT_GAP_MS = 400L

        /** 默认最小滑动距离，单位 dp。 */
        private const val DEFAULT_SWIPE_MIN_DP = 48

        /** 按压下划线线宽相对屏幕宽度的比例，取自 480 宽基准的 2px。 */
        private const val PRESS_UNDERLINE_RATIO = 2f / 480f

        /** 标签白描边宽度相对字号的比例，取自图墨的 0.14。 */
        private const val LABEL_OUTLINE_RATIO = 0.14f
    }
}
