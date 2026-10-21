package com.shuiyu.octoberrain.feature.content.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.common.WereadBillSnapshot
import com.shuiyu.octoberrain.core.common.WereadFingerprintSnapshot
import com.shuiyu.octoberrain.core.common.WereadNotesSnapshot
import com.shuiyu.octoberrain.core.common.WereadRankEntry
import com.shuiyu.octoberrain.core.common.WereadSection
import com.shuiyu.octoberrain.core.common.WereadStatsPeriod
import com.shuiyu.octoberrain.core.common.WereadStatsSnapshot
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.feature.content.ContentRegistry
import com.shuiyu.octoberrain.feature.content.ContentSourceImpl
import com.shuiyu.octoberrain.feature.content.data.ContentPaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
private val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * 微信读书页面。
 *
 * 顶部按总览、统计、笔记、账单、排行分段，切换时只读取当前分段的数据并缓存到
 * 本次页面生命周期；未配置密钥或来源关闭时隐藏页签，仅显示配置或启用入口。
 *
 * @param initialSection 进入页面时选中的分段，默认总览
 */
@Suppress("TooManyFunctions")
class WereadScreen(
    private val initialSection: WereadSection = WereadSection.OVERVIEW,
) : Screen {
    private companion object {
        private const val NOTES_LIMIT = 50
        private const val RANK_LIMIT = 10
    }

    private lateinit var hostContext: Context
    private var pageView: InkPage? = null
    private var tabRow: LinearLayout? = null
    private var body: LinearLayout? = null
    private var sectionViews: WereadSectionViews? = null
    private var section: WereadSection = initialSection
    private var statsPeriod: WereadStatsPeriod = WereadStatsPeriod.WEEKLY
    private var configured = false
    private var enabled = false

    private var overview: WereadOverviewData? = null
    private val statsCache = LinkedHashMap<WereadStatsPeriod, WereadStatsSnapshot?>()
    private var fingerprint: WereadFingerprintSnapshot? = null
    private var notes: WereadNotesSnapshot? = null
    private var bill: WereadBillSnapshot? = null
    private var rank: List<WereadRankEntry>? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null

    /** 页面容器的上下文。 */
    private val context: Context
        get() = hostContext

    override fun createView(context: Context): View {
        hostContext = context
        val created = InkPage(context, "微信读书", onBack = { goBack() })
        pageView = created
        val tabs =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams =
                    LinearLayout.LayoutParams(MATCH_PARENT, DesignRuntime.tokens.buttonHeight)
                visibility = View.GONE
            }
        tabRow = tabs
        val content =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                        topMargin = DesignRuntime.tokens.gap
                    }
            }
        body = content
        created.content.addView(tabs)
        created.content.addView(content)
        sectionViews =
            WereadSectionViews(
                context = context,
                tokens = DesignRuntime.tokens,
                onPeriodSelected = ::selectPeriod,
                onCalendar = { navigate(Destination.WereadCalendar) },
            )
        return created
    }

    override fun onResume() {
        refreshFlags()
        if (!configured) {
            showTabs(false)
            renderSetup()
            return
        }
        if (!enabled) {
            showTabs(false)
            renderDisabled()
            return
        }
        showTabs(true)
        rebuildTabs()
        loadSection(section, force = false)
    }

    override fun onPause() {
        loadJob?.cancel()
    }

    override fun onDestroy() {
        scope.cancel()
    }

    /** 重新读取密钥与来源开关。 */
    private fun refreshFlags() {
        configured = source()?.wereadClient?.isConfigured() ?: false
        enabled =
            runCatching { ContentPaths.integrations().wereadEnabled }.getOrDefault(false)
    }

    /** 显示或隐藏分段页签。 */
    private fun showTabs(visible: Boolean) {
        tabRow?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** 依据当前分段重建页签，选中项反白。 */
    private fun rebuildTabs() {
        val row = tabRow ?: return
        row.removeAllViews()
        val tokens = DesignRuntime.tokens
        WereadSectionLogic.SECTIONS.forEach { item ->
            val selected = item == section
            val tab =
                TextView(context).apply {
                    text = WereadSectionLogic.label(item)
                    gravity = Gravity.CENTER
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, tokens.textBody)
                    setTextColor(if (selected) InkColors.PAPER else InkColors.INK)
                    setBackgroundColor(if (selected) InkColors.INK else InkColors.PAPER)
                    isClickable = true
                    isFocusable = true
                    layoutParams = LinearLayout.LayoutParams(0, MATCH_PARENT, 1f)
                    setOnClickListener { selectSection(item) }
                }
            row.addView(tab)
        }
    }

    /** 切换分段；相同分段重复点击时保持当前缓存。 */
    private fun selectSection(target: WereadSection) {
        if (target == section && (body?.childCount ?: 0) > 0) return
        section = target
        rebuildTabs()
        loadSection(target, force = false)
    }

    /** 切换统计周期，仅读取尚未缓存的周期。 */
    private fun selectPeriod(period: WereadStatsPeriod) {
        if (period == statsPeriod && statsCache.containsKey(period)) return
        statsPeriod = period
        section = WereadSection.STATS
        rebuildTabs()
        loadStats(period)
    }

    /** 读取并绘制指定分段。 */
    @Suppress("TooGenericExceptionCaught")
    private fun loadSection(
        target: WereadSection,
        force: Boolean,
    ) {
        if (force) invalidateSection(target)
        val container = prepareBody()
        val content = source()
        if (content == null) {
            container.addView(
                ContentUi.emptyCard(
                    context = context,
                    title = "微信读书尚未就绪",
                    message = "桌面服务尚未完成初始化，请稍后重新进入。",
                ),
            )
            return
        }
        val loading = ContentUi.loading(container)
        loadJob?.cancel()
        loadJob =
            scope.launch {
                try {
                    when (target) {
                        WereadSection.OVERVIEW -> loadOverview(content)
                        WereadSection.STATS -> loadStatsData(content, statsPeriod)
                        WereadSection.NOTES -> notes = content.wereadNotes(NOTES_LIMIT)
                        WereadSection.BILL -> bill = content.wereadBill()
                        WereadSection.RANK -> rank = content.wereadRank(RANK_LIMIT)
                    }
                    ContentUi.removeLoading(loading)
                    renderSection(target, container)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    ContentUi.removeLoading(loading)
                    ContentUi.errorCard(container, "读取失败：${error.message ?: "未知错误"}")
                }
            }
    }

    /** 统计分段只读取指定周期，指纹只在首次进入时读取。 */
    @Suppress("TooGenericExceptionCaught")
    private fun loadStats(period: WereadStatsPeriod) {
        val container = prepareBody()
        val content = source() ?: return
        val loading = ContentUi.loading(container)
        loadJob?.cancel()
        loadJob =
            scope.launch {
                try {
                    loadStatsData(content, period)
                    ContentUi.removeLoading(loading)
                    renderSection(WereadSection.STATS, container)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    ContentUi.removeLoading(loading)
                    ContentUi.errorCard(container, "读取失败：${error.message ?: "未知错误"}")
                }
            }
    }

    /** 读取总览三段数据：月度统计、书架与最近划线。 */
    private suspend fun loadOverview(content: ContentSourceImpl) {
        val month = content.wereadMonth()
        val shelf = content.wereadShelf(20)
        val highlights = content.wereadHighlights(5)
        overview = WereadOverviewData(month, shelf, highlights)
    }

    /** 读取周期统计与阅读指纹。 */
    private suspend fun loadStatsData(
        content: ContentSourceImpl,
        period: WereadStatsPeriod,
    ) {
        if (!statsCache.containsKey(period)) {
            statsCache[period] = content.wereadPeriod(period)
        }
        if (fingerprint == null) {
            fingerprint = content.wereadFingerprint()
        }
    }

    /** 清空正文并加入顶部操作行，返回正文容器。 */
    private fun prepareBody(): LinearLayout {
        val container = body ?: error("页面尚未创建")
        container.removeAllViews()
        container.addView(controlRow())
        return container
    }

    /** 顶部操作行：刷新当前分段。 */
    private fun controlRow(): View {
        val tokens = DesignRuntime.tokens
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            addView(
                InkWidgets.caption(context, "数据缓存三十分钟").apply {
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                },
            )
            addView(ContentUi.actionText(context, "刷新") { loadSection(section, force = true) })
            setPadding(0, 0, 0, tokens.gap)
        }
    }

    /** 依据缓存绘制当前分段。 */
    private fun renderSection(
        target: WereadSection,
        container: LinearLayout,
    ) {
        val views = sectionViews ?: return
        when (target) {
            WereadSection.OVERVIEW ->
                views.renderOverview(
                    container,
                    overview ?: WereadOverviewData(null, emptyList(), emptyList()),
                )
            WereadSection.STATS ->
                views.renderStats(
                    container,
                    statsPeriod,
                    statsCache[statsPeriod],
                    fingerprint,
                )
            WereadSection.NOTES ->
                views.renderNotes(container, notes ?: WereadNotesSnapshot())
            WereadSection.BILL -> views.renderBill(container, bill)
            WereadSection.RANK -> views.renderRank(container, rank.orEmpty())
        }
    }

    /** 清空指定分段的缓存，供刷新使用。 */
    private fun invalidateSection(target: WereadSection) {
        when (target) {
            WereadSection.OVERVIEW -> overview = null
            WereadSection.STATS -> {
                statsCache.clear()
                fingerprint = null
            }
            WereadSection.NOTES -> notes = null
            WereadSection.BILL -> bill = null
            WereadSection.RANK -> rank = null
        }
    }

    /** 未配置密钥时的引导，不显示分段。 */
    private fun renderSetup() {
        val container = body ?: return
        container.removeAllViews()
        container.addView(
            ContentUi.emptyCard(
                context = context,
                title = "未配置微信读书",
                message = "在「微读墨记」设置页填写或扫描二维码录入接口密钥，即可同步书架、阅读时长与划线。",
                actionLabel = "打开设置",
                onAction = { navigateToSettings() },
            ),
        )
    }

    /** 跳转到微读墨记设置页，密钥统一在该页面录入。 */
    private fun navigateToSettings() {
        runCatching { AppServices.router.navigate(Destination.WereadApp) }
            .onFailure { toast("无法打开设置页") }
    }

    /** 来源关闭时的提示，不显示分段。 */
    private fun renderDisabled() {
        val container = body ?: return
        container.removeAllViews()
        container.addView(
            ContentUi.noticeCard(
                context = context,
                message = "微信读书集成已关闭，当前不会请求任何数据。",
                actionLabel = "启用",
                onAction = { setEnabled(true) },
            ),
        )
        container.addView(
            ContentUi.emptyCard(
                context = context,
                title = "集成已关闭",
                message = "启用后显示书架、月度统计与划线。",
                actionLabel = "启用",
                onAction = { setEnabled(true) },
            ),
        )
    }

    /** 启用或关闭集成。 */
    private fun setEnabled(enabledValue: Boolean) {
        val saved =
            runCatching {
                AppServices.config.edit { config ->
                    config.copy(integrations = config.integrations.copy(wereadEnabled = enabledValue))
                }
            }.isSuccess
        if (!saved) {
            toast("保存失败，请稍后重试")
            return
        }
        source()?.wereadClient?.invalidate()
        onResume()
    }

    /** 当前模块的数据源。 */
    private fun source(): ContentSourceImpl? = ContentRegistry.source()

    /** 返回上一页。 */
    private fun goBack() {
        runCatching { AppServices.router.back() }
    }

    /** 打开内置页面。 */
    private fun navigate(destination: Destination) {
        runCatching { AppServices.router.navigate(destination) }
    }

    /** 显示简短提示。 */
    private fun toast(message: String) {
        runCatching { AppServices.router.toast(message) }
    }
}
