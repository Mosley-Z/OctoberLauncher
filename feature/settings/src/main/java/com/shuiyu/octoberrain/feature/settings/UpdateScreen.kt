package com.shuiyu.octoberrain.feature.settings

import android.text.format.Formatter
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.ChangelogEntry
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.UpdateInfo
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.design.InkWidgets
import kotlinx.coroutines.launch

/**
 * 检查更新页面。
 *
 * 进入页面后自动查询更新地址，展示新版本信息与更新日志；
 * 下载完成后通过系统安装器安装，安装失败时给出允许未知来源的提示。
 */
class UpdateScreen : SettingsBaseScreen("检查更新") {
    /** 页面状态。 */
    private sealed interface State {
        /** 尚未检查。 */
        data object Idle : State

        /** 正在检查。 */
        data object Checking : State

        /** 已是最新版本。 */
        data object Latest : State

        /** 发现新版本。 */
        data class Available(
            val info: UpdateInfo,
        ) : State

        /** 正在下载或安装。 */
        data class Working(
            val message: String,
        ) : State

        /** 检查或下载失败。 */
        data class Failure(
            val message: String,
        ) : State
    }

    private var state: State = State.Idle
    private var autoStarted = false
    private var changelog: List<ChangelogEntry> = emptyList()
    private var changelogRequested = false

    override fun build(
        page: InkPage,
        config: LauncherConfig,
    ) {
        val context = page.context
        val currentVersion = AppPackageInfo.versionName(context)

        val status = SettingsUi.section(page, "版本", "当前版本 $currentVersion")
        status.info("版本名", currentVersion)
        status.info("版本号", AppPackageInfo.versionCode(context).toString())
        status.info("更新来源", config.integrations.updateUrl.ifBlank { "GitHub Releases" })

        when (val snapshot = state) {
            State.Idle, State.Checking -> status.info("检查状态", "正在检查更新…")
            State.Latest -> status.info("检查状态", "已是最新版本")
            is State.Failure -> status.info("检查状态", snapshot.message)
            is State.Working -> status.info("检查状态", snapshot.message)
            is State.Available -> {
                status.info("检查状态", "发现新版本")
                buildAvailable(page, snapshot.info)
            }
        }

        val actions = SettingsUi.section(page, "操作", null)
        actions.button(
            label = if (state is State.Checking) "检查中…" else "检查更新",
            filled = state !is State.Checking,
        ) {
            if (state !is State.Checking) check()
        }
        if (state is State.Available) {
            actions.button(label = "下载并安装", filled = true) {
                val info = (state as? State.Available)?.info ?: return@button
                downloadAndInstall(info)
            }
        }
        if (state is State.Working) {
            actions.button(label = "正在处理，请稍候", filled = false) {
                SettingsUi.toast(context, "下载或安装进行中")
            }
        }

        buildChangelog(page)

        SettingsUi.note(
            page,
            "安装前需在系统设置中允许本应用安装未知来源应用。",
        )

        page.content.addView(
            InkWidgets.row(context, "关于") {
                AppServices.router.navigate(Destination.ProjectAbout)
            },
        )

        if (!autoStarted) {
            autoStarted = true
            if (state is State.Idle) check()
        }
        if (!changelogRequested) {
            changelogRequested = true
            loadChangelog()
        }
    }

    /** 新版本详情。 */
    private fun buildAvailable(
        page: InkPage,
        info: UpdateInfo,
    ) {
        val context = page.context
        val section = SettingsUi.section(page, "新版本", "版本 ${info.versionName}")
        section.info("版本名", info.versionName)
        section.info("版本号", info.versionCode.toString())
        section.info("安装包大小", Formatter.formatFileSize(context, info.sizeBytes))
        if (info.sha256.isNotBlank()) {
            section.info("校验摘要", info.sha256.take(16) + "…")
        }
        if (info.notes.isNotBlank()) {
            SettingsUi.note(page, "更新说明：${info.notes}")
        }
    }

    /** 更新日志列表。 */
    private fun buildChangelog(page: InkPage) {
        val section = SettingsUi.section(page, "更新日志", "按版本倒序")
        if (changelog.isEmpty()) {
            section.info("记录", if (changelogRequested) "暂无记录" else "读取中…")
            return
        }
        changelog.forEach { entry ->
            section.subheading("${entry.version} · ${entry.date}")
            entry.items.forEach { item ->
                paragraph(page, section, "· $item")
            }
        }
    }

    /** 向分组追加一段带内边距的正文。 */
    private fun paragraph(
        page: InkPage,
        section: SettingsSection,
        text: String,
    ) {
        val body = InkWidgets.body(page.context, text)
        val gap = DesignRuntime.tokens.gap
        body.setPadding(gap, gap / 2, gap, gap / 2)
        section.content(body)
    }

    /** 查询更新。 */
    private fun check() {
        val context = contextOrNull() ?: return
        state = State.Checking
        rebuildLater()
        scope.launch {
            val result = runCatching { AppServices.updates.check(true) }
            state =
                result.fold(
                    onSuccess = { info ->
                        if (info == null) State.Latest else State.Available(info)
                    },
                    onFailure = { State.Failure("检查失败，请确认网络与更新地址") },
                )
            rebuildLater()
            if (state is State.Available) {
                SettingsUi.toast(context, "发现新版本")
            }
        }
    }

    /** 下载安装包并启动系统安装器。 */
    private fun downloadAndInstall(info: UpdateInfo) {
        val context = contextOrNull() ?: return
        state = State.Working("正在下载更新包…")
        rebuildLater()
        scope.launch {
            val file = runCatching { AppServices.updates.download(info) }.getOrNull()
            if (file == null) {
                state = State.Failure("下载失败，请检查网络后重试")
                rebuildLater()
                return@launch
            }
            state = State.Working("正在启动安装器…")
            rebuildLater()
            val installed = runCatching { AppServices.updates.install(file) }
            if (installed.isFailure) {
                state = State.Failure("无法启动安装，请允许安装未知来源应用")
                rebuildLater()
            } else {
                state = State.Failure("已交给系统安装器处理")
                rebuildLater()
            }
        }
    }

    /** 读取更新日志。 */
    private fun loadChangelog() {
        scope.launch {
            changelog = runCatching { AppServices.updates.changelog() }.getOrDefault(emptyList())
            rebuildLater()
        }
    }
}
