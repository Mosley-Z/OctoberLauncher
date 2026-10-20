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
import com.shuiyu.octoberrain.feature.settings.qr.KeyQrPanel
import kotlinx.coroutines.launch

/**
 * 书架设置页面。
 *
 * 行控件由 [SettingsCatalog.bookshelfGroups] 的声明生成；
 * 本页提供密钥输入与清除、缓存刷新、目录名称编辑与书架跳转等动作。
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
            "bookshelf.wereadKey" to { promptSecret() },
            "bookshelf.wereadKeyScan" to { openKeyPanel() },
            "bookshelf.wereadKeyClear" to { clearSecret() },
            "bookshelf.wereadRefresh" to { refreshCache() },
            "bookshelf.wereadImport" to { importWeread() },
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
                                    "阅读数据缓存 30 分钟，进度同步最多保留最近 200 条记录；刷新会清空旧缓存并重新拉取。",
                                    DesignRuntime.tokens.textCaption,
                                    InkColors.INK_2,
                                ),
                            )
                        }
                    section.content(body)
                }
            },
        )

    /** 输入并校验接口密钥，确认后写入配置。 */
    private fun promptSecret() {
        val context = contextOrNull() ?: return
        val current = SettingsUi.settings().integrations.wereadApiKey
        InkDialogs.input(
            context,
            "微信读书接口密钥",
            hint = "粘贴以 wrk- 开头的密钥",
            initial = current,
            password = true,
        ) { value ->
            val key = value.trim()
            val error = WereadKeyValidator.validateOrNull(key)
            if (error != null) {
                SettingsUi.toast(context, error)
            } else {
                updateIntegrations { it.copy(wereadApiKey = key, wereadEnabled = true) }
                SettingsUi.toast(context, "密钥已保存")
            }
        }
    }

    /** 打开局域网扫码录密钥面板，由手机浏览器提交密钥。 */
    private fun openKeyPanel() {
        val context = contextOrNull() ?: return
        KeyQrPanel(context).show()
    }

    /** 清除本机保存的密钥，并停止微信读书数据刷新。 */
    private fun clearSecret() {
        val context = contextOrNull() ?: return
        if (SettingsUi
                .settings()
                .integrations.wereadApiKey
                .isBlank()
        ) {
            SettingsUi.toast(context, "尚未配置密钥")
            return
        }
        updateIntegrations { it.copy(wereadApiKey = "", wereadEnabled = false) }
        SettingsUi.toast(context, "密钥已清除")
        scope.launch { runCatching { AppServices.content.refreshAll() } }
    }

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

    /** 发起从微读墨记迁移数据，结果以提示形式展示。 */
    private fun importWeread() {
        val context = contextOrNull() ?: return
        val importer = AppServices.wereadImport
        if (importer == null) {
            SettingsUi.toast(context, "当前版本不含迁移入口")
            return
        }
        importer.requestImport { message -> SettingsUi.toast(context, message) }
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
