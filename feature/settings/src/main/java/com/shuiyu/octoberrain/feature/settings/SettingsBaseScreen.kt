package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import android.view.View
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.design.InkPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 设置页基类：统一创建带返回栏的 InkPage，并在页面回到前台时按需重建内容。
 *
 * 子类只需实现 [build] 填充页面；配置在外部发生变化时，
 * [onResume] 会比较配置快照并重建，保证从系统设置页返回后状态正确。
 * 标题传 null 或空串时顶栏只保留返回按钮，页面内容自行绘制标题。
 * showBack 为 false 且标题为空时，顶栏整行不占高度，内容贴顶显示，
 * 返回操作交由系统返回键处理。
 */
abstract class SettingsBaseScreen(
    private val screenTitle: CharSequence?,
    private val showBack: Boolean = true,
) : Screen {
    /** 页面级协程作用域，随页面销毁取消。 */
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 当前页面容器，未创建或已销毁时为 null。 */
    protected var page: InkPage? = null
        private set

    private var lastConfig: LauncherConfig? = null

    final override fun createView(context: Context): View {
        val view =
            InkPage(context, screenTitle, showBack = showBack) {
                AppServices.router.back()
            }
        // 设计层 InkPage 的顶栏始终占 topBarHeight；本页无返回按钮也无标题时，
        // 直接移除顶层容器中的空顶栏与分隔线，让内容贴顶。
        if (!showBack && screenTitle.isNullOrEmpty()) {
            val root = view.getChildAt(0) as? android.widget.LinearLayout
            if (root != null && root.childCount >= 2) {
                root.removeViewAt(1)
                root.removeViewAt(0)
            }
        }
        page = view
        val config = AppServices.config.state.value
        build(view, config)
        lastConfig = config
        return view
    }

    final override fun onResume() {
        val current = page ?: return
        val config = AppServices.config.state.value
        if (config != lastConfig || needsRebuild()) {
            rebuild(current, config)
        }
        onResumed()
    }

    protected open fun onResumed() = Unit

    /** 子类在配置未变化但界面需要重建时返回 true，例如权限状态变化。 */
    protected open fun needsRebuild(): Boolean = false

    /** 取当前页面上下文，页面尚未创建时返回 null。 */
    protected fun contextOrNull(): Context? = page?.context

    /** 立即按当前配置重建页面内容。 */
    protected fun rebuildNow() {
        val current = page ?: return
        rebuild(current, AppServices.config.state.value)
    }

    /** 在下一帧重建页面内容，避免在触摸事件分发的中间阶段移除视图。 */
    protected fun rebuildLater() {
        val current = page ?: return
        current.post {
            if (page === current) rebuild(current, AppServices.config.state.value)
        }
    }

    /** 清空内容容器后重新生成页面。 */
    private fun rebuild(
        target: InkPage,
        config: LauncherConfig,
    ) {
        target.content.removeAllViews()
        build(target, config)
        lastConfig = config
        AppServices.refresh.requestPartial()
    }

    /**
     * 按声明表给出的生效方式刷新界面。
     *
     * 参数 effect 由设置项声明指定：外观类变化先重建设计令牌再整屏刷新；
     * 页结构变化先局部刷新再重建页面；其余按局部或整屏刷新处理。
     * 未声明生效方式时不做任何刷新，交由页面动作自行处理。
     */
    internal fun applySettingEffect(effect: SettingEffect) {
        SettingEffectDispatcher.dispatch(effect, effectHooks())
    }

    /** 页面侧生效动作：局部与整屏刷新、外观重建、页面重建与内容刷新。 */
    private fun effectHooks(): SettingEffectHooks =
        object : SettingEffectHooks {
            override fun partialRefresh() {
                SettingsUi.refresh()
            }

            override fun fullRefresh() {
                SettingsUi.fullRefresh()
            }

            override fun applyAppearance() {
                SettingsUi.applyAppearance(SettingsUi.settings().appearance)
            }

            override fun rebuildPage() {
                rebuildLater()
            }

            override fun refreshContent() {
                RefreshSchedulerBridge.notifyContentChanged()
                scope.launch { runCatching { AppServices.content.refreshAll() } }
            }
        }

    /** 供声明表渲染器回调生效方式。 */
    internal fun settingEffectSink(): SettingEffectSink = SettingEffectSink { effect -> applySettingEffect(effect) }

    override fun onDestroy() {
        scope.cancel()
        page = null
    }

    /** 子类在此追加分组与控件。 */
    protected abstract fun build(
        page: InkPage,
        config: LauncherConfig,
    )
}
