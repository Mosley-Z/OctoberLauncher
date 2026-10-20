package com.shuiyu.octoberrain.feature.content.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.design.InkTokens
import com.shuiyu.octoberrain.feature.content.ContentRegistry
import com.shuiyu.octoberrain.feature.content.ContentSourceImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 内容页面基类。
 *
 * 统一页面容器、数据读取协程、错误提示与宿主交互入口。子类在 onResume 中
 * 调用 [load]，在给定的容器内按顺序添加视图；读取过程中的异常会落到红色
 * 提示卡片，页面本身不会崩溃。
 */
abstract class ContentScreen(
    private val screenTitle: String,
) : Screen {
    private companion object {
        private const val TAG = "内容页面"
    }

    private lateinit var hostContext: Context
    private var pageView: InkPage? = null
    private var renderJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 页面容器。 */
    protected val page: InkPage
        get() = checkNotNull(pageView) { "页面视图尚未创建" }

    /** 页面正文容器，已包含页边距。 */
    protected val content: LinearLayout
        get() = page.content

    /** 设计令牌。 */
    protected val tokens: InkTokens
        get() = DesignRuntime.tokens

    /** 创建页面的宿主上下文。 */
    protected val context: Context
        get() = hostContext

    /** 当前模块的数据源，模块尚未安装时返回 null。 */
    protected fun source(): ContentSourceImpl? = ContentRegistry.source()

    override fun createView(context: Context): View {
        hostContext = context
        val created = InkPage(context, screenTitle, onBack = { goBack() })
        pageView = created
        return created
    }

    /**
     * 读取并渲染页面内容。
     *
     * 先清空正文并显示加载提示，再执行 [render]；渲染结束后移除加载提示，
     * 协程被取消时不输出错误卡片，其他异常记录日志并展示提示，避免页面停留
     * 在空白状态或残留加载文案。
     */
    protected fun load(render: suspend (LinearLayout) -> Unit) {
        renderJob?.cancel()
        renderJob =
            scope.launch {
                val container = content
                container.removeAllViews()
                val loading = ContentUi.loading(container)
                try {
                    render(container)
                    // 正文渲染完成后移除加载提示，避免与已渲染内容同时显示
                    ContentUi.removeLoading(loading)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    InkLog.w(TAG, "$screenTitle 加载失败：${error.message}", error)
                    container.removeAllViews()
                    ContentUi.errorCard(container, "读取失败：${error.message ?: "未知错误"}")
                }
            }
    }

    /** 在页面作用域内启动一次操作，异常时提示用户而不中断页面。 */
    protected fun runTask(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                InkLog.w(TAG, "$screenTitle 操作失败：${error.message}", error)
                toast("操作失败：${error.message ?: "未知错误"}")
            }
        }
    }

    /** 清空正文，供需要重建内容的操作使用。 */
    protected fun clearContent(): LinearLayout {
        val container = content
        container.removeAllViews()
        return container
    }

    /** 返回上一页。 */
    protected fun goBack() {
        runCatching { AppServices.router.back() }
            .onFailure { InkLog.w(TAG, "返回失败：${it.message}", it) }
    }

    /** 打开指定目的地。 */
    protected fun navigate(destination: Destination) {
        runCatching { AppServices.router.navigate(destination) }
            .onFailure { InkLog.w(TAG, "跳转失败：${it.message}", it) }
    }

    /** 显示简短提示。 */
    protected fun toast(message: String) {
        runCatching { AppServices.router.toast(message) }
            .onFailure { InkLog.w(TAG, "提示失败：${it.message}", it) }
    }

    /** 通知宿主进行局部重绘。 */
    protected fun repaint() {
        runCatching { AppServices.refresh.requestPartial() }
            .onFailure { InkLog.w(TAG, "请求局部重绘失败：${it.message}", it) }
    }

    /** 打开文件，失败时提示用户。 */
    protected fun openFile(file: java.io.File): Boolean {
        val library = source()?.files
        if (library == null) {
            toast("内容模块尚未就绪")
            return false
        }
        val intent = library.openIntent(file)
        if (intent == null) {
            toast("无法打开该文件，请确认已安装对应阅读应用")
            return false
        }
        return runCatching {
            hostContext.startActivity(intent)
            true
        }.getOrElse {
            InkLog.w(TAG, "打开文件失败：${it.message}", it)
            toast("无法打开该文件，请确认已安装对应阅读应用")
            false
        }
    }

    /** 存储权限入口。 */
    protected fun requestStoragePermission() {
        runCatching { AppServices.permissions.openStorageSettings() }
            .onFailure {
                InkLog.w(TAG, "打开存储权限设置失败：${it.message}", it)
                toast("请到系统设置中授予文件访问权限")
            }
    }

    override fun onPause() {
        renderJob?.cancel()
    }

    override fun onDestroy() {
        scope.cancel()
    }
}
