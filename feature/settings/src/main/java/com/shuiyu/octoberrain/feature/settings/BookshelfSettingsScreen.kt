package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.IntegrationsConfig
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.design.InkWidgets
import kotlinx.coroutines.launch

/**
 * 书架设置页面。
 *
 * 行控件由 [SettingsCatalog.bookshelfGroups] 的声明生成；
 * 本页提供缓存刷新、目录名称编辑与书架跳转等动作。
 */
class BookshelfSettingsScreen : SettingsBaseScreen("书架设置") {
    override fun build(
        page: InkPage,
        config: LauncherConfig,
    ) {
        SettingsSpecRenderer.renderPage(
            page = page,
            groups = SettingsCatalog.bookshelfGroups,
            config = config,
            sink = settingEffectSink(),
            actions = bookshelfActions(),
            customs = bookshelfCustoms(),
        )
    }

    /** 书架页的动作表，键为设置项标识。 */
    private fun bookshelfActions(): Map<String, () -> Unit> =
        mapOf(
            "bookshelf.wereadRefresh" to { refreshCache() },
            "bookshelf.wereadSyncNow" to { syncWeread() },
            "bookshelf.booksDir" to { promptDirectory() },
            "bookshelf.open" to { openBookshelf(contextOrNull()) },
        )

    /** 书架页的自定义行表：缓存说明为整段正文。 */
    private fun bookshelfCustoms(): Map<String, (SettingsSection) -> Unit> =
        mapOf(
            "bookshelf.wereadCacheNote" to { section ->
                page?.let { inkPage ->
                    val gap = DesignRuntime.tokens.gap
                    val body =
                        LinearLayout(inkPage.context).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(gap, gap / 2, gap, gap)
                            addView(
                                InkWidgets.textView(
                                    inkPage.context,
                                    "书架与笔记按完整索引同步；阅读进度逐本补齐，网络失败时保留已有数据，下次同步继续补充。",
                                    DesignRuntime.tokens.textCaption,
                                    InkColors.INK_2,
                                ),
                            )
                        }
                    section.content(body)
                }
            },
        )

    /** 编辑本地书籍目录名称。 */
    private fun promptDirectory() {
        val context = contextOrNull() ?: return
        InkDialogs.input(
            context,
            "目录名称",
            hint = "例如 Books 或 Download/Books",
            initial = SettingsUi.settings().integrations.booksDir,
        ) { value ->
            if (value.isBlank()) {
                SettingsUi.toast(context, "目录名称不能为空")
            } else {
                updateIntegrations { it.copy(booksDir = value.trim()) }
                SettingsUi.toast(context, "目录已改为 $value")
            }
        }
    }

    /** 触发内容层刷新全部缓存。 */
    private fun refreshCache() {
        val context = contextOrNull() ?: return
        scope.launch {
            val done = runCatching { AppServices.content.refreshAll() }.isSuccess
            if (done) {
                SettingsUi.toast(context, "微信读书缓存已刷新")
            } else {
                SettingsUi.toast(context, "缓存刷新失败，请检查网络与密钥")
            }
        }
    }

    /**
     * 用已配置密钥直连接口同步数据。
     *
     * 同步在后台线程执行，结束后以提示形式回传状态；未配置密钥或正在同步时给出行内说明。
     */
    private fun syncWeread() {
        val context = contextOrNull() ?: return
        val host = AppServices.wereadSync
        if (host == null) {
            SettingsUi.toast(context, "当前版本不含同步入口")
            return
        }
        SettingsUi.toast(context, host.statusText())
        host.syncNow { _, message ->
            SettingsUi.toast(context, message)
        }
    }
}

/** 写入整合配置并请求局部刷新。 */
private fun updateIntegrations(transform: (IntegrationsConfig) -> IntegrationsConfig) {
    SettingsUi.edit { config -> config.copy(integrations = transform(config.integrations)) }
    SettingsUi.refresh()
}

/** 跳转到书架，失败时提示。 */
private fun openBookshelf(context: Context?) {
    runCatching { AppServices.router.navigate(Destination.Bookshelf) }
        .onFailure { error ->
            if (context != null) SettingsUi.toast(context, "无法打开书架：${error.message ?: "未知错误"}")
        }
}
