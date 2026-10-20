package com.shuiyu.octoberrain.feature.content.ui.negative

import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.DEFAULT_NEGATIVE_MODULES
import com.shuiyu.octoberrain.core.common.NegativeHost
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkWidgets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 墨台列表态容器。
 *
 * 容器由三部分组成：三单位高的顶栏、按配置顺序排列的分区列表、设置态。顶栏左侧齿轮进入
 * 设置态，中部为衬线标题「墨台」，右侧显示最近更新时间与返回入口。所有点击都在容器内完成：
 * 分区内的书目行与图片行展开页内浮层，浮层内的打开微信读书按钮只启动外部应用，负一屏不再
 * 跳转任何详情页。左滑超过阈值关闭负一屏，阈值取十四像素与屏宽百分之十二的较大值。
 *
 * @param context 页面上下文。
 * @param host 负一屏宿主，用于请求重绘与读取宽度。
 * @param onClose 关闭负一屏的回调。
 */
@Suppress("TooManyFunctions")
internal class DeskContainerView(
    context: Context,
    private val host: NegativeHost,
    private val onClose: () -> Unit,
) : FrameLayout(context) {
    private val unit: Float = DeskSectionStyle.unit(context)

    private val updateTime: SimpleDateFormat = SimpleDateFormat(UPDATE_PATTERN, Locale.getDefault())

    private val column: LinearLayout

    private val body: FrameLayout

    private val listScroll: ScrollView

    private val sectionColumn: LinearLayout

    private val settingsView: DeskSettingsView

    private val overlay: DeskOverlayView

    private val sections = ArrayList<DeskSectionHolder>()

    private var settingsActive = false

    private var downX = 0f

    private var downY = 0f

    private var swipeConsumed = false

    init {
        setBackgroundColor(InkColors.PAPER)
        column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        addView(column, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        column.addView(
            buildTopBar(context),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                DeskSectionStyle.topBarHeightPx(context),
            ),
        )
        body = FrameLayout(context)
        column.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        sectionColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        listScroll = ScrollView(context).apply { isFillViewport = true }
        listScroll.addView(
            sectionColumn,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        body.addView(listScroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        settingsView =
            DeskSettingsView(
                context,
                onClose = { closeSettings() },
                onChanged = { refreshAfterSettings() },
                onPickFingerprint = { pickFingerprint() },
            )
        body.addView(
            ScrollView(context).apply {
                addView(settingsView)
                visibility = View.GONE
                tag = SETTINGS_TAG
            },
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        overlay = DeskOverlayView(context)
        addView(overlay, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // 分区构造会读取原盘数据并绑定内核页面，放在首帧之后执行，
        // 避免首帧测量与数据读取叠加造成输入事件超时。
        post { rebuildSections() }
    }

    /**
     * 构造顶栏。
     *
     * 版式与微读墨台基准一致：左侧「设置」入口贴左内边距，衬线标题「墨台」在整屏水平居中，
     * 右侧「更新于」与「返回」组成对齐到右内边距的动作簇，底部一条一像素浅灰分隔线。
     *
     * @param context 页面上下文。
     * @return 顶栏视图。
     */
    private fun buildTopBar(context: Context): FrameLayout {
        val padding = DeskSectionStyle.paddingXPx(resources.displayMetrics.widthPixels)
        val bar = FrameLayout(context)
        bar.addView(
            buildSettingsAction(context),
            FrameLayout
                .LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.START or Gravity.CENTER_VERTICAL,
                ).apply { marginStart = padding },
        )
        bar.addView(
            buildTitle(context),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        bar.addView(
            buildRightActions(context),
            FrameLayout
                .LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.END or Gravity.CENTER_VERTICAL,
                ).apply { marginEnd = padding },
        )
        bar.addView(
            View(context).apply { setBackgroundColor(DeskSectionStyle.dividerColor()) },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1,
                Gravity.BOTTOM,
            ),
        )
        return bar
    }

    /**
     * 构造顶栏左侧的设置入口。
     *
     * @param context 页面上下文。
     * @return 设置入口视图。
     */
    private fun buildSettingsAction(context: Context): View =
        InkWidgets
            .textView(
                context,
                ACTION_SETTINGS,
                DeskSectionStyle.topBarActionTextPx(context),
                InkColors.INK,
            ).apply {
                isClickable = true
                setOnClickListener { openSettings() }
            }

    /**
     * 构造顶栏居中的衬线标题。
     *
     * @param context 页面上下文。
     * @return 标题视图。
     */
    private fun buildTitle(context: Context): View =
        InkWidgets.textView(
            context,
            TITLE,
            DeskSectionStyle.topBarTitleTextPx(context),
            InkColors.INK,
            DesignRuntime.typeface("serif"),
        )

    /**
     * 构造顶栏右侧的更新时间与返回动作簇。
     *
     * @param context 页面上下文。
     * @return 动作簇视图。
     */
    private fun buildRightActions(context: Context): View {
        val actions =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        actions.addView(
            InkWidgets
                .textView(
                    context,
                    updateLabel(),
                    DeskSectionStyle.topBarUpdateTextPx(context),
                    DeskSectionStyle.updateTextColor(),
                ).apply { tag = UPDATE_TAG },
        )
        actions.addView(
            InkWidgets
                .textView(
                    context,
                    ACTION_BACK,
                    DeskSectionStyle.topBarActionTextPx(context),
                    InkColors.INK_2,
                ).apply {
                    isClickable = true
                    setOnClickListener { onClose() }
                },
            LinearLayout
                .LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = (BACK_GAP_UNITS * unit).roundToInt() },
        )
        return actions
    }

    /** 按配置顺序重建分区列表。 */
    fun rebuildSections() {
        releaseSections()
        sectionColumn.removeAllViews()
        val config = AppServices.config.state.value
        val order = effectiveOrder(config.desktop.negativePage.modules)
        order.forEachIndexed { index, id ->
            if (index > 0) sectionColumn.addView(DeskSectionStyle.buildDivider(context))
            val holder = buildSection(id) ?: return@forEachIndexed
            sections.add(holder)
            sectionColumn.addView(
                holder.view,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        refreshUpdateLabel()
        // 分区在首帧之后才装配，首帧的 onResume 早于装配完成；装配结束后补一次恢复回调，
        // 让书架与最近图片分区立即读取数据而不是停留在空抬头。
        if (isAttachedToWindow) {
            sections.forEach { it.onResume() }
        }
    }

    /**
     * 按标识构造分区。
     *
     * @param moduleId 模块标识。
     * @return 分区持有对象，未知标识返回空。
     */
    private fun buildSection(moduleId: String): DeskSectionHolder? =
        when (moduleId) {
            MODULE_BILL,
            MODULE_RANK,
            MODULE_FP,
            ->
                kernelSection(moduleId).also {
                    if (moduleId == MODULE_FP) {
                        fpSection = it
                    }
                }
            MODULE_BOOKSHELF -> {
                val shelf =
                    ShelfDeskSection(context) { item ->
                        showBook(item.title, item.author, item.progress, item.coverPath, item.deepLink)
                    }
                DeskSectionHolder(shelf, shelf::onResume, shelf::onPause, shelf::release)
            }
            MODULE_RECENT -> {
                val recent = RecentDeskSection(context) { file -> overlay.showImage(file) }
                DeskSectionHolder(recent, recent::onResume, recent::onPause, recent::release)
            }
            else -> null
        }

    /**
     * 构造内核分区。
     *
     * 分区抬头由内核页面绘制，这里只传入模块标识，不再补原生标题行。
     *
     * @param moduleId 内核模块标识。
     * @return 分区持有对象。
     */
    private fun kernelSection(moduleId: String): DeskSectionHolder {
        val section =
            DeskKernelSection(
                context = context,
                screenHost = host,
                moduleId = moduleId,
                onSettings = { openSettings() },
            )
        return DeskSectionHolder(section, section::onResume, section::onPause, section::release)
    }

    /**
     * 展开书目浮层。
     *
     * @param title 书名。
     * @param author 作者。
     * @param progress 阅读进度。
     * @param coverPath 封面路径。
     * @param deepLink 微信读书深链，可为空。
     */
    private fun showBook(
        title: String,
        author: String,
        progress: Float,
        coverPath: String?,
        deepLink: String?,
    ) {
        overlay.showBook(title, author, progress, coverPath) {
            val link = deepLink
            if (link != null) runCatching { AppServices.router.openUrl(link) }
        }
    }

    /** 打开设置态。 */
    private fun openSettings() {
        settingsActive = true
        settingsView.render()
        findViewWithTag<ScrollView>(SETTINGS_TAG)?.visibility = View.VISIBLE
        listScroll.visibility = View.GONE
    }

    /** 关闭设置态回到列表态。 */
    private fun closeSettings() {
        settingsActive = false
        findViewWithTag<ScrollView>(SETTINGS_TAG)?.visibility = View.GONE
        listScroll.visibility = View.VISIBLE
    }

    /** 设置写入后的刷新：重设分区内容并重建列表。 */
    private fun refreshAfterSettings() {
        sections.forEach { it.onPause() }
        rebuildSections()
        sections.forEach { it.onResume() }
    }

    /** 打开阅读指纹选书对话框。 */
    private fun pickFingerprint() {
        (fpSection?.view as? DeskKernelSection)?.openFingerprintPicker()
    }

    /** 刷新顶栏的更新时间文案。 */
    private fun refreshUpdateLabel() {
        updateTime.format(Date())
        val label = findViewWithTag<android.widget.TextView>(UPDATE_TAG) ?: return
        label.text = updateLabel()
    }

    /**
     * 生成更新时间文案。
     *
     * @return 形如「更新于 16:30」的文案。
     */
    private fun updateLabel(): String = UPDATE_PREFIX + updateTime.format(Date())

    /**
     * 计算实际展示的模块顺序。
     *
     * @param configured 配置中的模块顺序。
     * @return 过滤未知标识后的顺序，配置为空时取默认顺序。
     */
    private fun effectiveOrder(configured: List<String>): List<String> {
        val known = DEFAULT_MODULES.filter { configured.contains(it) }
        val extra = configured.filter { it in DEFAULT_MODULES && !known.contains(it) }
        return (known + extra).ifEmpty { DEFAULT_MODULES }
    }

    /** 页面回到前台时转发给各分区。 */
    fun onResumeSections() {
        refreshUpdateLabel()
        sections.forEach { it.onResume() }
    }

    /** 页面离开前台时转发给各分区。 */
    fun onPauseSections() {
        sections.forEach { it.onPause() }
    }

    /** 释放各分区与内核资源。 */
    fun releaseSections() {
        sections.forEach { it.release() }
        sections.clear()
        fpSection = null
    }

    /**
     * 处理返回键。
     *
     * @return 已消费返回键时返回 true。
     */
    fun handleBack(): Boolean =
        when {
            overlay.isShowing() -> {
                overlay.hide()
                true
            }
            settingsActive -> {
                closeSettings()
                true
            }
            else -> false
        }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                swipeConsumed = false
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (isExitSwipe(dx, dy, swipeConsumed)) {
                    swipeConsumed = true
                    onClose()
                    return true
                }
            }
            else -> Unit
        }
        return super.dispatchTouchEvent(event)
    }

    /**
     * 判断抬手动作是否构成左滑退出。
     *
     * @param dx 水平位移。
     * @param dy 竖直位移。
     * @param consumed 本次手势是否已被消费。
     * @return 构成左滑退出时返回 true。
     */
    private fun isExitSwipe(
        dx: Float,
        dy: Float,
        consumed: Boolean,
    ): Boolean =
        when {
            consumed -> false
            dx >= 0f -> false
            abs(dx) < swipeThreshold() -> false
            else -> abs(dx) > abs(dy)
        }

    /**
     * 计算左滑退出阈值。
     *
     * @return 十四像素与屏宽百分之十二的较大值。
     */
    private fun swipeThreshold(): Float {
        val widthPx = resources.displayMetrics.widthPixels.toFloat()
        return max(SWIPE_MIN_PX, widthPx * SWIPE_WIDTH_RATIO)
    }

    private var fpSection: DeskSectionHolder? = null

    /**
     * 分区持有对象，统一页面生命周期转发。
     *
     * @param view 分区视图。
     * @param onResume 回到前台回调。
     * @param onPause 离开前台回调。
     * @param release 释放回调。
     */
    private class DeskSectionHolder(
        val view: View,
        val onResume: () -> Unit,
        val onPause: () -> Unit,
        val release: () -> Unit,
    )

    private companion object {
        /** 顶栏标题。 */
        const val TITLE = "墨台"

        /** 顶栏设置入口文案。 */
        const val ACTION_SETTINGS = "⚙ 设置"

        /** 顶栏返回入口文案。 */
        const val ACTION_BACK = "返回 ›"

        /** 更新时间前缀。 */
        const val UPDATE_PREFIX = "更新于 "

        /** 更新时间格式。 */
        const val UPDATE_PATTERN = "HH:mm"

        /** 更新时间标签。 */
        const val UPDATE_TAG = "desk_update"

        /** 设置态视图标签。 */
        const val SETTINGS_TAG = "desk_settings"

        /** 左滑退出最小阈值，单位为像素。 */
        const val SWIPE_MIN_PX = 14f

        /** 左滑退出阈值占屏宽比例。 */
        const val SWIPE_WIDTH_RATIO = 0.12f

        /** 顶栏右侧「更新于」与「返回」之间的间距，单位为排版单位。 */
        const val BACK_GAP_UNITS = 8f

        /** 模块标识：墨单。 */
        const val MODULE_BILL = "bill"

        /** 模块标识：阅读资产。 */
        const val MODULE_RANK = "rank"

        /** 模块标识：阅读指纹。 */
        const val MODULE_FP = "fp"

        /** 模块标识：书架。 */
        const val MODULE_BOOKSHELF = "bookshelf"

        /** 模块标识：最近图片。 */
        const val MODULE_RECENT = "negative_files"

        /** 默认模块顺序；阅读指纹由内核按原版提供，运行时排在自建分区之后。 */
        val DEFAULT_MODULES: List<String> = DEFAULT_NEGATIVE_MODULES + MODULE_FP
    }
}
