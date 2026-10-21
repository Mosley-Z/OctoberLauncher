package com.shuiyu.octoberrain.feature.wereadapp

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.inkread.weekread.ui.SegTabView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.feature.wereadapp.ui.WereadReadingPage
import com.shuiyu.octoberrain.feature.wereadapp.ui.WereadSettingsPage
import com.shuiyu.octoberrain.feature.wereadapp.ui.WereadTodoPage

/** 主容器的页面分段，顺序与页签一致。 */
internal enum class WereadAppTab {
    /** 阅读页：内部再分本周、本月、本书、洞察四段。 */
    READING,

    /** 页面内设置页：显示、卡片、锁屏密码、成就与联动等表单。 */
    SETTINGS,

    /** 待办页：清单增删改查。 */
    TODO,
}

/**
 * 设置内微读墨记页面。
 *
 * 页面为全屏结构，顶部三段页签（阅读、设置、待办）常显；阅读页内部再分形态。
 * 页面状态由 [WereadAppView] 持有，退出时释放。
 */
class WereadAppScreen : Screen {
    /** 页面视图，未创建或已销毁时为空。 */
    private var appView: WereadAppView? = null

    /** 创建页面视图并记录引用。 */
    override fun createView(context: Context): View = WereadAppView(context).also { appView = it }

    /** 页面回到前台时刷新当前分段数据。 */
    override fun onResume() {
        appView?.refresh()
    }

    /** 返回键：先退回阅读页，已在阅读页时交由宿主处理。 */
    override fun onBack(): Boolean = appView?.back() ?: false

    /** 释放页面持有的资源。 */
    override fun onDestroy() {
        appView?.release()
        appView = null
    }
}

/**
 * 页面根视图。
 *
 * 纵向排列页签栏与内容容器，三个页签常显；
 * 各分段内容按需创建并缓存，切换时只替换内容容器的子视图。
 */
@Suppress("TooManyFunctions")
internal class WereadAppView(
    context: Context,
) : LinearLayout(context) {
    /** 内容容器，承载当前分段的页面视图。 */
    private val body = FrameLayout(context)

    /** 顶部主导航，复用微读 SegTabView 控件。 */
    private val tabBar = SegTabView(context)

    /** 当前形态文案，显示在主导航首项。 */
    private var formLabel = "本周"

    /** 当前可见的页签顺序，三段常显。 */
    private val tabs = mutableListOf<WereadAppTab>()

    /** 已创建的分段视图，按分段缓存。 */
    private val pages = mutableMapOf<WereadAppTab, View>()

    /** 当前分段。 */
    private var current: WereadAppTab = WereadAppTab.READING

    init {
        orientation = VERTICAL
        setBackgroundColor(InkColors.PAPER)
        addView(tabBar, LayoutParams(LayoutParams.MATCH_PARENT, DesignRuntime.tokens.topBarHeight))
        addView(InkWidgets.divider(context))
        addView(
            body,
            LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f),
        )
        tabBar.setListener { index -> onMainTab(index) }
        rebuildTabs()
        showTab(WereadAppTab.READING)
    }

    /** 刷新当前分段，供页面回到前台与切回页签时调用。 */
    fun refresh() {
        when (current) {
            WereadAppTab.READING -> readingPage()?.refresh()
            WereadAppTab.SETTINGS -> settingsPage()?.refresh()

            WereadAppTab.TODO -> todoPage()?.refresh()
        }
    }

    /** 处理返回键：不在阅读页时切回阅读页并返回 true。 */
    fun back(): Boolean {
        if (current == WereadAppTab.READING) return false
        showTab(WereadAppTab.READING)
        return true
    }

    /** 释放各分段资源。 */
    fun release() {
        readingPage()?.release()
        pages.clear()
        removeAllViews()
    }

    /** 读取当前分段。 */
    fun tab(): WereadAppTab = current

    /** 重建页签列表；三个页签常显。 */
    private fun rebuildTabs() {
        tabs.clear()
        tabs.add(WereadAppTab.READING)
        tabs.add(WereadAppTab.SETTINGS)
        tabs.add(WereadAppTab.TODO)
        val labels =
            arrayOf(
                "$formLabel▽",
                "设置",
                "待办",
            )
        tabBar.setLabels(labels)
        if (current !in tabs) current = WereadAppTab.READING
        tabBar.setSelected(tabs.indexOf(current).coerceAtLeast(0))
    }

    /**
     * 主导航点击：首项展开形态下拉。
     *
     * 参数 index 为主导航分段下标，0 为首项；首项已在阅读页时展开形态下拉，
     * 否则先切回阅读页。
     */
    private fun onMainTab(index: Int) {
        if (index == 0) {
            if (current == WereadAppTab.READING) {
                readingPage()?.toggleFormMenu()
            } else {
                showTab(WereadAppTab.READING)
            }
            return
        }
        showTab(tabs.getOrNull(index) ?: return)
    }

    /**
     * 切换到指定分段并刷新内容。
     *
     * 参数 tab 为目标分段，三个分段都可直接切换。
     */
    fun showTab(tab: WereadAppTab) {
        if (tab !in tabs) return
        current = tab
        tabBar.setSelected(tabs.indexOf(tab))
        val page = pages.getOrPut(tab) { createPage(tab) }
        body.removeAllViews()
        body.addView(
            page,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        if (tab == WereadAppTab.READING) readingPage()?.selectReadingTab()
        refresh()
    }

    /** 按分段创建页面视图。 */
    private fun createPage(tab: WereadAppTab): View =
        when (tab) {
            WereadAppTab.READING ->
                WereadReadingPage(context).apply {
                    listener = readingListener()
                }

            WereadAppTab.SETTINGS ->
                WereadSettingsPage(context).apply {
                    listener =
                        object : WereadSettingsPage.Listener {
                            override fun onSaveAndBack() {
                                showTab(WereadAppTab.READING)
                            }
                        }
                }

            WereadAppTab.TODO -> WereadTodoPage(context)
        }

    /** 阅读页外部跳转回调。 */
    private fun readingListener(): WereadReadingPage.Listener =
        object : WereadReadingPage.Listener {
            override fun onOpenDesk() {
                if (AppServices.hasRouter()) {
                    AppServices.router.navigate(Destination.Negative)
                }
            }

            override fun onOpenBook(bookId: String) {
                openExternal("weread://reading?bId=$bookId")
            }

            override fun onOpenReading() {
                openExternal("weread://reading")
            }

            override fun onToast(message: String) {
                if (AppServices.hasRouter()) {
                    AppServices.router.toast(message)
                }
            }

            override fun onFormChanged(label: String) {
                formLabel = label
                rebuildTabs()
            }
        }

    /** 用外部链接打开微信读书，未安装时给出提示。 */
    private fun openExternal(url: String) {
        if (!AppServices.hasRouter()) return
        runCatching { AppServices.router.openUrl(url) }
            .onFailure { AppServices.router.toast("未找到可打开微信读书的应用") }
    }

    /** 读取已创建的阅读页，未创建时返回 null。 */
    private fun readingPage(): WereadReadingPage? = pages[WereadAppTab.READING] as? WereadReadingPage

    /** 读取已创建设置页，未创建时返回 null。 */
    private fun settingsPage(): WereadSettingsPage? = pages[WereadAppTab.SETTINGS] as? WereadSettingsPage

    /** 读取已创建待办页，未创建时返回 null。 */
    private fun todoPage(): WereadTodoPage? = pages[WereadAppTab.TODO] as? WereadTodoPage
}
