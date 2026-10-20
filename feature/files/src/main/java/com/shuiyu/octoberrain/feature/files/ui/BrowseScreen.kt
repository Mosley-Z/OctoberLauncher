package com.shuiyu.octoberrain.feature.files.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.common.VolumeKeySink
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.device.DeviceRuntime
import com.shuiyu.octoberrain.core.render.RefreshScheduler
import com.shuiyu.octoberrain.feature.files.data.FileItem
import com.shuiyu.octoberrain.feature.files.data.FileListing
import com.shuiyu.octoberrain.feature.files.data.FilePrefs
import com.shuiyu.octoberrain.feature.files.data.StorageAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 文件浏览页。
 *
 * 页面由网格与列表两个面板共用分页容器，目录列举在后台线程完成后一次性写入面板；
 * 排序、隐藏文件与视图形态来自模块偏好，进入目录、返回上级与翻页都在本类处理。
 * 页面自身不读写共享存储之外的状态，写入与删除后的缩略图失效通过 [notifyFileChanged] 通知。
 *
 * 参数 initialPath 为入口指定的起始目录，null 表示回到上次浏览位置。
 *
 * 本类同时承担页面生命周期、目录加载、面板切换与权限引导，方法数量高于 detekt 默认阈值；
 * 这些职责共享同一份页面状态，因此保留单类实现并在此处抑制函数数量检查。
 */
@Suppress("TooManyFunctions")
internal class BrowseScreen(
    private val initialPath: String?,
) : Screen,
    VolumeKeySink {
    private lateinit var container: FrameLayout
    private lateinit var tokens: FileTokens
    private lateinit var prefs: FilePrefs
    private lateinit var storage: StorageAccess
    private lateinit var listing: FileListing

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pane: BrowsePane? = null
    private var currentPath: String = ""
    private var loadJob: Job? = null
    private var permissionView: View? = null

    override fun createView(context: Context): View {
        val textScale =
            runCatching { AppServices.config.state.value.appearance.textScale }
                .getOrDefault(1f)
        tokens =
            FileTokens(
                profile = DeviceRuntime.profile,
                textScale = textScale,
            )
        prefs = FilePrefs(context)
        storage = StorageAccess(context)
        listing = FileListing(storage, prefs)
        container = FrameLayout(context).apply { setBackgroundColor(InkColors.PAPER) }
        currentPath = resolveStartPath()
        if (storage.hasAccess()) showBrowseContent() else showPermissionView()
        return container
    }

    override fun onResume() {
        if (!::container.isInitialized) return
        when {
            !storage.hasAccess() -> showPermissionView()
            permissionView != null -> showBrowseContent()
            else -> load(currentPath, pane?.pagerState?.firstIndex ?: 0, resetPage = false)
        }
    }

    override fun onPause() {
        if (::prefs.isInitialized && currentPath.isNotEmpty()) prefs.lastPath = currentPath
    }

    override fun onBack(): Boolean {
        val currentPane = pane
        val parent = if (currentPane == null) null else listing.parentOf(currentPath)
        return when {
            currentPane == null -> false
            currentPane.closePanel() -> true
            parent != null -> {
                load(parent, 0, resetPage = true)
                true
            }
            else -> false
        }
    }

    override fun onDestroy() {
        loadJob?.cancel()
        scope.cancel()
        pane = null
        permissionView = null
    }

    override fun onVolumeKey(up: Boolean): Boolean {
        val currentPane = pane ?: return false
        currentPane.turnPage(forward = !up)
        return true
    }

    /**
     * 通知某路径的内容已变化。
     *
     * 参数 path 为发生写入或删除的文件路径。调用后丢弃该路径的内存与磁盘缩略图，
     * 触发刷新调度并请求局部刷新，供后续文件操作与编辑流程共用。
     */
    fun notifyFileChanged(path: String) {
        pane?.invalidateThumb(path)
        RefreshScheduler.notifyContentChanged()
        AppServices.refresh.requestPartial()
    }

    /** 建立网格或列表面板，并写入目录数据。 */
    private fun showBrowseContent() {
        permissionView = null
        container.removeAllViews()
        val created = buildPane()
        pane = created
        container.addView(created, matchParent())
        load(currentPath, 0, resetPage = true)
    }

    /** 按偏好创建面板并绑定回调。 */
    private fun buildPane(): BrowsePane {
        val callbacks =
            object : BrowsePane.Callbacks {
                override fun onEnterDir(item: FileItem) {
                    load(item.path, 0, resetPage = true)
                }

                override fun onOpenItem(item: FileItem) {
                    if (!item.isImage) {
                        AppServices.router.toast(BrowseStrings.OPEN_PENDING)
                        return
                    }
                    val images = pane?.itemSnapshot().orEmpty().filter { it.isImage }
                    if (images.isEmpty()) return
                    val index = images.indexOfFirst { it.path == item.path }.coerceAtLeast(0)
                    AppServices.router.navigate(Destination.PhotoViewer(images.map { it.path }, index))
                }

                override fun onDetailRequested(item: FileItem) {
                    AppServices.router.toast(BrowseStrings.DETAIL_PENDING)
                }

                override fun onToggleView() {
                    prefs.grid = !prefs.grid
                    rebuildPane()
                }

                override fun onPrefsChanged() {
                    load(currentPath, pane?.pagerState?.firstIndex ?: 0, resetPage = false)
                }

                override fun onManageFolders() {
                    AppServices.router.toast(BrowseStrings.FOLDERS_PENDING)
                }

                override fun onBackRequested() {
                    if (!onBack()) AppServices.router.back()
                }

                override fun onPageChanged(page: Int) {
                    AppServices.refresh.requestPartial()
                }
            }
        return if (prefs.grid) {
            GridPane(container.context, tokens, prefs, scope, callbacks)
        } else {
            ListPane(container.context, tokens, prefs, scope, callbacks)
        }
    }

    /** 切换视图形态时保留当前页的页首下标。 */
    private fun rebuildPane() {
        val firstIndex = pane?.pagerState?.firstIndex ?: 0
        container.removeAllViews()
        val rebuilt = buildPane()
        pane = rebuilt
        container.addView(rebuilt, matchParent())
        load(currentPath, firstIndex, resetPage = firstIndex <= 0)
    }

    /**
     * 列举目录并刷新面板。
     *
     * 参数 path 为目标目录；firstIndex 为期望显示的条目下标；resetPage 为 true 时回到首页，
     * 否则按每页条目数换算页码。目录读取失败时面板显示错误态。
     */
    private fun load(
        path: String,
        firstIndex: Int,
        resetPage: Boolean,
    ) {
        currentPath = path
        prefs.lastPath = path
        loadJob?.cancel()
        loadJob =
            scope.launch {
                val items = withContext(Dispatchers.IO) { listing.list(path) }
                val error = listing.lastError
                val target = pane ?: return@launch
                if (error.isNotEmpty()) {
                    target.setError(error)
                } else {
                    target.setData(items, listing.nameOf(path), path)
                    target.setPage(if (resetPage) 0 else firstIndex / target.pageSize.coerceAtLeast(1))
                }
                AppServices.refresh.requestPartial()
            }
    }

    /** 起始目录依次取入口参数、上次位置与主存储根，均需当前可读。 */
    private fun resolveStartPath(): String {
        val candidates = listOfNotNull(initialPath, prefs.lastPath.ifEmpty { null }, storage.externalRoot())
        return candidates.firstOrNull { it.isNotEmpty() && storage.isReadable(it) } ?: storage.externalRoot()
    }

    /** 绘制权限缺失说明与打开存储设置的入口。 */
    private fun showPermissionView() {
        if (permissionView != null) return
        container.removeAllViews()
        pane = null
        val layout =
            LinearLayout(container.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(tokens.dp(24f), tokens.dp(24f), tokens.dp(24f), tokens.dp(24f))
                setBackgroundColor(InkColors.PAPER)
            }
        val message =
            TextView(container.context).apply {
                text = BrowseStrings.PERMISSION
                setTextColor(InkColors.INK)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, tokens.bodyText)
                gravity = Gravity.CENTER
            }
        layout.addView(message, wrapContent())
        val button = createSettingsButton()
        layout.addView(button, wrapContent(topMargin = tokens.dp(18f)))
        permissionView = layout
        container.addView(layout, matchParent())
    }

    /** 创建带描边的存储设置按钮。 */
    private fun createSettingsButton(): TextView =
        TextView(container.context).apply {
            text = BrowseStrings.OPEN_SETTINGS
            setTextColor(InkColors.INK)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, tokens.bodyText)
            gravity = Gravity.CENTER
            setPadding(tokens.dp(18f), tokens.dp(10f), tokens.dp(18f), tokens.dp(10f))
            background =
                GradientDrawable().apply {
                    setColor(InkColors.PAPER)
                    setStroke(tokens.stroke, InkColors.INK)
                }
            isClickable = true
            isFocusable = true
            setOnClickListener { AppServices.permissions.openStorageSettings() }
        }

    /** 铺满父容器的布局参数。 */
    private fun matchParent(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    /** 包裹内容的布局参数，可附带顶部间距。 */
    private fun wrapContent(topMargin: Int = 0): ViewGroup.LayoutParams {
        val params =
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        params.topMargin = topMargin
        return params
    }
}
