package com.shuiyu.octoberrain.feature.files.ui.album

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.common.VolumeKeySink
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.device.DeviceRuntime
import com.shuiyu.octoberrain.feature.files.data.MediaDay
import com.shuiyu.octoberrain.feature.files.data.MediaEntry
import com.shuiyu.octoberrain.feature.files.data.MediaIndex
import com.shuiyu.octoberrain.feature.files.data.MediaIndexData
import com.shuiyu.octoberrain.feature.files.data.StorageAccess
import com.shuiyu.octoberrain.feature.files.ui.FileTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 相册页。
 *
 * 数据来自 [MediaIndex]：启动时优先读取磁盘索引并立即显示，随后在后台按索引更新时间
 * 增量刷新；索引缺失或损坏时全量重建并显示建立提示。页面按天分块展示三列缩略图，
 * 每页最多三段，点击打开查看器，长按弹出打开、详情、分享三项。
 *
 * 索引建立、磁盘读写与媒体库查询都在 IO 线程执行，本类只处理视图与页码状态。
 * 页面实现音量键翻页，与文件浏览页保持一致。
 */
@Suppress("TooManyFunctions")
internal class AlbumScreen :
    Screen,
    VolumeKeySink {
    private lateinit var container: FrameLayout
    private lateinit var tokens: FileTokens
    private lateinit var storage: StorageAccess
    private lateinit var index: MediaIndex

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var pane: AlbumPane? = null
    private var statusView: TextView? = null
    private var pageView: TextView? = null
    private var previousButton: TextView? = null
    private var nextButton: TextView? = null
    private var permissionView: View? = null
    private var loadJob: Job? = null
    private var page: Int = 0
    private var loadedOnce: Boolean = false
    private var firstOpen: Boolean = true

    override fun createView(context: Context): View {
        tokens =
            FileTokens(
                profile = DeviceRuntime.profile,
                textScale =
                    runCatching { AppServices.config.state.value.appearance.textScale }
                        .getOrDefault(1f),
            )
        storage = StorageAccess(context)
        index = MediaIndex(context)
        container = FrameLayout(context).apply { setBackgroundColor(InkColors.PAPER) }
        if (storage.hasAccess()) showContent() else showPermission()
        return container
    }

    override fun onResume() {
        if (!::container.isInitialized) return
        when {
            !storage.hasAccess() -> showPermission()
            pane == null -> showContent()
            else -> refreshAsync(index.peek() ?: return)
        }
    }

    override fun onBack(): Boolean = false

    override fun onDestroy() {
        loadJob?.cancel()
        pane?.release()
        pane = null
        permissionView = null
        scope.cancel()
    }

    override fun onVolumeKey(up: Boolean): Boolean {
        if (pane == null) return false
        return turnPage(forward = !up)
    }

    /** 建立顶栏、时间轴面板与底栏，并开始加载索引。 */
    private fun showContent() {
        permissionView = null
        container.removeAllViews()
        pane = null
        val context = container.context
        val root =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(InkColors.PAPER)
            }
        root.addView(buildTopBar(context), fillParams(tokens.topBarH))
        root.addView(divider(context), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, tokens.stroke))
        val panel = AlbumPane(context, tokens, scope, callbacks())
        pane = panel
        root.addView(panel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(divider(context), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, tokens.stroke))
        root.addView(buildBottomBar(context), fillParams(tokens.bottomBarH))
        container.addView(root, matchParent())
        loadInitial()
    }

    /** 建立顶栏：返回、标题、状态行。 */
    private fun buildTopBar(context: Context): View {
        val bar =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(tokens.padH, 0, tokens.padH, 0)
            }
        bar.addView(
            textView(context, "←", tokens.titleText, InkColors.INK).apply {
                gravity = Gravity.CENTER
                contentDescription = "返回"
                setOnClickListener { AppServices.router.back() }
            },
            LinearLayout.LayoutParams(tokens.touchMin, tokens.touchMin),
        )
        bar.addView(
            textView(context, "相册", tokens.titleText, InkColors.INK).apply {
                setPadding(tokens.gapLarge, 0, 0, 0)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        val status = textView(context, "", tokens.smallText, InkColors.INK_3)
        statusView = status
        bar.addView(status, wrapParams())
        return bar
    }

    /** 建立底栏：上一页、页码、下一页。 */
    private fun buildBottomBar(context: Context): View {
        val bar =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(tokens.padH, 0, tokens.padH, 0)
            }
        val previous = strokeButton(context, "上一页") { turnPage(forward = false) }
        previousButton = previous
        bar.addView(previous, LinearLayout.LayoutParams(tokens.navButtonWidth, tokens.dp(36f)))
        val pageLabel =
            textView(context, "", tokens.smallText, InkColors.INK_3).apply {
                gravity = Gravity.CENTER
            }
        pageView = pageLabel
        bar.addView(pageLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val next = strokeButton(context, "下一页") { turnPage(forward = true) }
        nextButton = next
        bar.addView(next, LinearLayout.LayoutParams(tokens.navButtonWidth, tokens.dp(36f)))
        return bar
    }

    /** 读取磁盘索引，命中则立即展示，否则全量重建。 */
    private fun loadInitial() {
        val started = SystemClock.elapsedRealtime()
        pane?.setLoading(true)
        pane?.setError("")
        loadJob?.cancel()
        loadJob =
            scope.launch {
                val cached = index.loadCached()
                if (cached != null) {
                    show(cached)
                    InkLog.w(
                        TAG,
                        "读取磁盘索引耗时 ${SystemClock.elapsedRealtime() - started}ms，" +
                            "条目 ${cached.entries.size}，索引文件 ${index.indexSize()} 字节",
                    )
                    refreshAsync(cached)
                } else {
                    val rebuilt = index.rebuild()
                    show(rebuilt)
                    InkLog.w(
                        TAG,
                        "首次建立索引耗时 ${SystemClock.elapsedRealtime() - started}ms，" +
                            "条目 ${rebuilt.entries.size}，日分组 ${rebuilt.days.size}，" +
                            "索引文件 ${index.indexSize()} 字节",
                    )
                }
            }
    }

    /** 按索引更新时间做增量刷新，结果有变化时重绘面板。 */
    private fun refreshAsync(base: MediaIndexData) {
        loadJob?.cancel()
        loadJob =
            scope.launch {
                val since = base.updatedAt
                val refreshed = index.refresh(since)
                if (refreshed.updatedAt != base.updatedAt) show(refreshed)
            }
    }

    /** 写入面板数据并更新状态行与页码。 */
    private fun show(data: MediaIndexData) {
        val panel = pane ?: return
        panel.setLoading(false)
        panel.setError("")
        panel.submit(data.days, page)
        page = panel.currentPage()
        statusView?.text = statusText(data)
        updatePageLabel(panel)
        loadedOnce = true
        if (firstOpen) {
            firstOpen = false
            InkLog.w(TAG, "相册页首次显示完成，共 ${data.entries.size} 张")
        }
        AppServices.refresh.requestPartial()
    }

    /**
     * 翻页。
     *
     * 参数 forward 为 true 时前进一页；已到边界返回 false，并保持页码不变。
     */
    private fun turnPage(forward: Boolean): Boolean {
        val panel = pane
        val moved = panel != null && panel.turnPage(forward)
        if (moved && panel != null) {
            page = panel.currentPage()
            updatePageLabel(panel)
            AppServices.refresh.requestPartial()
        } else {
            InkLog.d(TAG, "翻页已到边界，方向 forward=$forward")
        }
        return moved
    }

    /** 更新页码文本与按钮可用态。 */
    private fun updatePageLabel(panel: AlbumPane) {
        val pageCount = runCatching { panel.pageCount() }.getOrDefault(1)
        pageView?.text = "第 ${panel.currentPage() + 1} / $pageCount 页"
        previousButton?.isEnabled = panel.hasPrevious()
        nextButton?.isEnabled = panel.hasNext()
        previousButton?.alpha = if (panel.hasPrevious()) 1f else 0.4f
        nextButton?.alpha = if (panel.hasNext()) 1f else 0.4f
        if (loadedOnce) statusView?.alpha = if (panel.dayCount() > 0) 1f else 0.5f
    }

    /** 状态行文案：总数与最近更新时间。 */
    private fun statusText(data: MediaIndexData): String {
        val stamp =
            if (data.updatedAt > 0L) {
                SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(data.updatedAt))
            } else {
                "尚未更新"
            }
        return "共 ${data.entries.size} 张 · 更新于 $stamp"
    }

    /** 面板交互回调：打开查看器、长按菜单。 */
    private fun callbacks(): AlbumPane.Callbacks =
        object : AlbumPane.Callbacks {
            override fun onOpenPhoto(
                day: MediaDay,
                index: Int,
            ) {
                val paths = day.items.map { it.path }
                if (paths.isEmpty()) return
                runCatching {
                    AppServices.router.navigate(Destination.PhotoViewer(paths, index.coerceIn(0, paths.size - 1)))
                }.onFailure { InkLog.w(TAG, "打开查看器失败：${it.message}") }
            }

            override fun onLongPress(entry: MediaEntry) {
                val options = listOf("打开", "详情", "分享")
                InkDialogs.choose(container.context, entry.name, options) { picked ->
                    when (picked) {
                        0 -> openEntry(entry)
                        1 -> showDetail(entry)
                        2 -> share(entry)
                    }
                }
            }
        }

    /** 以单项列表打开查看器。 */
    private fun openEntry(entry: MediaEntry) {
        runCatching {
            AppServices.router.navigate(Destination.PhotoViewer(listOf(entry.path), 0))
        }.onFailure { InkLog.w(TAG, "打开查看器失败：${it.message}") }
    }

    /** 分享单张图片，通过 FileProvider 授予读取权限，失败时提示没有可用应用。 */
    private fun share(entry: MediaEntry) {
        val context = container.context
        val file = File(entry.path)
        val uri =
            runCatching { FileProvider.getUriForFile(context, context.packageName + ".files", file) }
                .getOrElse {
                    InkLog.w(TAG, "分享构造 Uri 失败：${it.message}")
                    return
                }
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = entry.mime.ifBlank { "image/*" }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "分享图片"))
        }.onFailure {
            InkLog.w(TAG, "分享失败：${it.message}")
            InkDialogs.toast(context, "没有可用的分享应用")
        }
    }

    /** 弹出详情对话框，列出路径、尺寸、类型与时间。 */
    private fun showDetail(entry: MediaEntry) {
        val size = if (entry.width > 0 && entry.height > 0) "${entry.width} × ${entry.height}" else "未知"
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(entry.time))
        val lines =
            listOf(
                "路径：${entry.path}",
                "尺寸：$size",
                "类型：${entry.mime.ifBlank { "未知" }}",
                "时间：$time",
            )
        InkDialogs.choose(container.context, "详情", lines) { }
    }

    /** 绘制权限缺失说明与打开存储设置的入口。 */
    private fun showPermission() {
        if (permissionView != null) return
        container.removeAllViews()
        pane = null
        val context = container.context
        val layout =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(tokens.dp(24f), tokens.dp(24f), tokens.dp(24f), tokens.dp(24f))
                setBackgroundColor(InkColors.PAPER)
            }
        layout.addView(
            textView(context, PERMISSION_HINT, tokens.bodyText, InkColors.INK).apply {
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        layout.addView(
            strokeButton(context, "打开存储设置") { AppServices.permissions.openStorageSettings() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, tokens.touchMin).apply {
                topMargin = tokens.dp(18f)
                gravity = Gravity.CENTER_HORIZONTAL
            },
        )
        permissionView = layout
        container.addView(layout, matchParent())
    }

    /** 创建正文文本视图。 */
    private fun textView(
        context: Context,
        text: CharSequence,
        size: Float,
        color: Int,
    ): TextView =
        TextView(context).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
            setTextColor(color)
        }

    /** 创建带描边的按钮文本视图。 */
    private fun strokeButton(
        context: Context,
        label: String,
        onClick: () -> Unit,
    ): TextView =
        TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_PX, tokens.bodyText)
            setTextColor(InkColors.INK)
            background =
                GradientDrawable().apply {
                    setColor(InkColors.PAPER)
                    setStroke(tokens.stroke, InkColors.INK)
                }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    /** 建立分隔线。 */
    private fun divider(context: Context): View = View(context).apply { setBackgroundColor(InkColors.INK_3) }

    /** 宽度铺满、给定高度的线性布局参数。 */
    private fun fillParams(height: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)

    /** 包裹内容的线性布局参数。 */
    private fun wrapParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    /** 铺满父容器的布局参数。 */
    private fun matchParent(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    private companion object {
        private const val TAG = "相册页"
        private const val PERMISSION_HINT = "查看相册需要访问共享存储，请在系统设置中授予「所有文件访问权限」"
    }
}
