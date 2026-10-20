package com.shuiyu.octoberrain.feature.home

import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.NegativeHost
import com.shuiyu.octoberrain.core.common.NegativeModule
import com.shuiyu.octoberrain.core.common.NegativeModuleRegistry
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * 负一屏页面。
 *
 * 负一屏呈现为墨台列表态：由内容模块注册的墨台容器负责顶栏、五个分区、设置态与页内浮层，
 * 本页面只提供整屏容器、生命周期转发与左滑退出判定。容器以单个模块视图铺满页面，不再按
 * 卡片列表渲染，也不再提供任何详情页入口。
 *
 * 左滑退出阈值取十四像素与屏宽百分之十二的较大值；顶栏的返回入口与系统返回键由墨台容器
 * 自行处理展开态、设置态与浮层关闭，容器不消费返回键时交给宿主返回桌面。
 */
internal class NegativeScreen :
    Screen,
    NegativeHost {
    /** 页面根容器。 */
    private var frame: FrameLayout? = null

    /** 页面上下文，由 [createView] 记录，供模块创建视图与解析资源。 */
    override lateinit var context: Context

    /** 页面可用宽度，单位 dp；模块据此选择紧凑或宽松布局。 */
    override val widthDp: Int
        get() = context.resources.configuration.screenWidthDp

    /** 墨台容器视图。 */
    private var deskView: View? = null

    /** 当前绑定的模块，数量为一时为墨台容器。 */
    private val modules = ArrayList<NegativeModule>()

    /** 最近一次绑定使用的模块 id 列表，用于识别配置变化。 */
    private var boundIds: List<String> = emptyList()

    /** 是否已完成首次绑定。 */
    private var bound = false

    /** 页面是否处于前台。 */
    private var resumed = false

    /** 页面级协程作用域，用于收集配置变化。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 配置收集任务，销毁页面时取消。 */
    private var configJob: Job? = null

    /**
     * 创建负一屏视图。
     *
     * 返回整屏容器，内容由墨台模块视图铺满；配置流首次发射时完成绑定，之后配置变化会触发
     * 重新绑定。容器未注册时显示说明文案，不提供任何页面跳转。
     */
    override fun createView(context: Context): View {
        this.context = context
        val root =
            SwipeBackFrame(context).apply {
                // 负一屏是独立页面，必须整屏不透明，避免透出下层桌面
                setBackgroundColor(InkColors.PAPER)
            }
        frame = root
        // 配置流会立即发射当前值，因此首次构建也在该回调内完成。
        configJob =
            scope.launch {
                AppServices.config.state.collect { cfg -> applyConfig(cfg) }
            }
        return root
    }

    /** 页面回到前台：转发模块恢复。 */
    override fun onResume() {
        resumed = true
        modules.forEach { runCatching { it.onResume() } }
        frame?.invalidate()
    }

    /** 页面离开前台：暂停全部模块。 */
    override fun onPause() {
        resumed = false
        modules.forEach { runCatching { it.onPause() } }
    }

    /**
     * 处理系统返回键。
     *
     * 墨台容器处于浮层或设置态时由容器消费返回键；容器不消费时返回 false，由宿主返回桌面。
     */
    override fun onBack(): Boolean {
        val view = deskView ?: return false
        val handled =
            runCatching {
                view.javaClass
                    .getMethod(METHOD_HANDLE_BACK)
                    .invoke(view) as? Boolean
            }.getOrNull()
        return handled == true
    }

    /** 页面销毁：转发模块销毁、取消配置收集并清空引用。 */
    override fun onDestroy() {
        resumed = false
        modules.forEach { runCatching { it.onDestroy() } }
        modules.clear()
        configJob?.cancel()
        configJob = null
        scope.cancel()
        frame = null
        deskView = null
    }

    /** 模块请求重绘时刷新宿主容器。 */
    override fun requestRedraw() {
        frame?.invalidate()
    }

    /**
     * 模块请求打开内置页面。
     *
     * 负一屏自身不发起任何页面跳转：墨台容器的全部交互都在当前页面完成，外部应用由浮层内
     * 的按钮单独启动。该方法保留以满足 [NegativeHost] 契约，收到请求时只刷新当前页面。
     *
     * @param destination 模块请求的目标，此处忽略。
     */
    override fun open(destination: Destination) {
        requestRedraw()
    }

    /**
     * 按当前配置绑定墨台容器。
     *
     * 配置中的模块列表未变化时直接返回；变化时重建容器并按当前前台状态恢复模块。
     *
     * @param cfg 当前配置快照。
     */
    private fun applyConfig(cfg: LauncherConfig) {
        val root = frame ?: return
        val ids =
            cfg.desktop.negativePage.modules
                .filter { NegativeModuleRegistry.get(it) != null }
        val deskId = DESK_MODULE_ID
        val nextIds = if (NegativeModuleRegistry.get(deskId) != null) listOf(deskId) else ids
        if (bound && nextIds == boundIds) return
        modules.forEach { runCatching { it.onPause() } }
        modules.clear()
        root.removeAllViews()
        deskView = null
        boundIds = nextIds
        bound = true
        val module = nextIds.firstOrNull()?.let { NegativeModuleRegistry.get(it) }
        val view = module?.let { runCatching { it.createView(context, this) }.getOrNull() }
        if (module != null && view != null) {
            modules.add(module)
            deskView = view
            root.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        } else {
            root.addView(
                buildEmptyState(context),
                FrameLayout
                    .LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { gravity = Gravity.CENTER },
            )
        }
        if (resumed) modules.forEach { runCatching { it.onResume() } }
    }

    /**
     * 构建容器未注册时的说明视图。
     *
     * @param context 页面上下文。
     * @return 说明视图，不包含任何页面跳转入口。
     */
    private fun buildEmptyState(context: Context): View {
        val tokens = DesignRuntime.tokens
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(tokens.pageGutter, tokens.pageTop, tokens.pageGutter, tokens.pageBottom)
            addView(InkWidgets.heading(context, EMPTY_TITLE).apply { gravity = Gravity.CENTER_HORIZONTAL })
            addView(InkWidgets.spacer(context, tokens.gap))
            addView(InkWidgets.caption(context, EMPTY_HINT).apply { gravity = Gravity.CENTER_HORIZONTAL })
        }
    }

    /**
     * 负一屏根容器。
     *
     * 在事件分发入口记录按下位置；抬起时水平位移满足左滑阈值且大于纵向位移，则请求返回
     * 桌面。事件按原链路继续分发，不消费、不拦截，模块内部的点击、拖动与滚动保持原行为。
     */
    private inner class SwipeBackFrame(
        context: Context,
    ) : FrameLayout(context) {
        /** 按下点相对根容器的横坐标。 */
        private var downX = 0f

        /** 按下点相对根容器的纵坐标。 */
        private var downY = 0f

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x
                    downY = ev.y
                }
                MotionEvent.ACTION_UP -> maybeSwipeBack(ev)
                else -> Unit
            }
            return super.dispatchTouchEvent(ev)
        }

        /**
         * 判定并执行左滑返回。
         *
         * @param ev 抬起事件；向左位移达到阈值且水平占优时，在事件分发结束后返回桌面，
         * 避免在分发过程中移除当前页面引起触摸状态错乱。
         */
        private fun maybeSwipeBack(ev: MotionEvent) {
            val dx = ev.x - downX
            val dy = ev.y - downY
            if (abs(dx) < swipeThresholdPx() || abs(dx) <= abs(dy)) return
            if (dx > 0f) return
            post {
                if (isAttachedToWindow) {
                    runCatching { AppServices.router.back() }
                }
            }
        }

        /**
         * 计算左滑退出阈值。
         *
         * @return 十四像素与屏宽百分之十二的较大值。
         */
        private fun swipeThresholdPx(): Float {
            val widthPx = resources.displayMetrics.widthPixels.toFloat()
            return max(SWIPE_MIN_PX, widthPx * SWIPE_WIDTH_RATIO)
        }
    }

    private companion object {
        /** 墨台模块标识。 */
        const val DESK_MODULE_ID: String = "desk"

        /** 墨台容器的返回键处理方法名，跨模块解耦以避免 feature/home 依赖 feature/content。 */
        const val METHOD_HANDLE_BACK: String = "handleBack"

        /** 容器未注册时的标题。 */
        const val EMPTY_TITLE: String = "墨台暂不可用"

        /** 容器未注册时的说明。 */
        const val EMPTY_HINT: String = "内容模块尚未就绪，请稍后再试"

        /** 左滑退出最小阈值，单位为像素。 */
        const val SWIPE_MIN_PX: Float = 14f

        /** 左滑退出阈值占屏宽比例。 */
        const val SWIPE_WIDTH_RATIO: Float = 0.12f
    }
}
