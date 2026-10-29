package com.shuiyu.octoberrain

import android.content.Intent
import android.net.Uri
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.Router
import com.shuiyu.octoberrain.core.design.InkDialogs

/**
 * 路由代理：在 Activity 创建前接收导航请求并暂存，挂载后统一分发。
 *
 * 功能模块安装时可能触发导航，代理保证调用方无需关心 Activity 生命周期。
 */
object LauncherRouter : Router {
    private var navigateImpl: ((Destination) -> Unit)? = null
    private var backImpl: (() -> Boolean)? = null
    private var activity: android.app.Activity? = null
    private val pending = ArrayDeque<Destination>()

    /** 挂载宿主回调，并清空暂存请求。 */
    fun attach(
        host: android.app.Activity,
        navigate: (Destination) -> Unit,
        back: () -> Boolean,
    ) {
        activity = host
        navigateImpl = navigate
        backImpl = back
        while (pending.isNotEmpty()) {
            navigate(pending.removeFirst())
        }
    }

    /** 解除挂载。 */
    fun detach() {
        activity = null
        navigateImpl = null
        backImpl = null
    }

    override fun navigate(destination: Destination) {
        val impl = navigateImpl
        if (impl == null) {
            pending.addLast(destination)
        } else {
            impl(destination)
        }
    }

    override fun back(): Boolean = backImpl?.invoke() ?: false

    override fun openApp(packageName: String) {
        val host = activity ?: return
        val intent = host.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            toast("无法启动该应用")
            return
        }
        runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            host.startActivity(intent)
        }.onFailure { InkLog.w("路由", "启动应用失败 $packageName", it) }
    }

    override fun openUrl(url: String) {
        val host = activity ?: return
        runCatching {
            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            host.startActivity(intent)
        }.getOrElse { throw it }
    }

    override fun toast(message: String) {
        val host =
            activity ?: run {
                InkLog.d("路由", message)
                return
            }
        InkDialogs.toast(host, message)
    }
}

/** 未提供内容模块时的占位实现，避免宿主在模块缺失时崩溃。 */
class PlaceholderContentSource : com.shuiyu.octoberrain.core.common.ContentSource {
    override suspend fun readingSnapshot() = null

    override suspend fun weather() = null

    override suspend fun bookshelf(limit: Int) = emptyList<com.shuiyu.octoberrain.core.common.BookItem>()

    override suspend fun wereadShelf(limit: Int) = emptyList<com.shuiyu.octoberrain.core.common.WereadShelfItem>()

    override suspend fun wereadMonth() = null

    override suspend fun wereadHighlights(limit: Int) = emptyList<com.shuiyu.octoberrain.core.common.WereadHighlight>()

    override suspend fun searchBooks(query: String) = emptyList<com.shuiyu.octoberrain.core.common.BookItem>()

    override suspend fun refreshAll() = Unit
}

/** 未提供托盘模块时的占位实现。 */
class PlaceholderTrayController : com.shuiyu.octoberrain.core.common.TrayController {
    override fun createStatusBar(context: android.content.Context): android.view.View =
        com.shuiyu.octoberrain.core.design.InkWidgets.textView(
            context,
            "十雨桌面",
            com.shuiyu.octoberrain.core.design.DesignRuntime.tokens.textStatus,
        )
}
