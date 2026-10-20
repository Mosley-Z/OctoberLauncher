package com.shuiyu.octoberrain.core.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.shuiyu.octoberrain.core.common.Capability
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors

/**
 * 组件内命中区域的矩形描述。
 *
 * 坐标为组件局部像素，原点位于组件左上角，与绘制坐标系一致；
 * [action] 为组件自定义的动作标识，宿主只负责在命中后把坐标或动作回传组件。
 *
 * @property x 区域左边界横坐标
 * @property y 区域上边界纵坐标
 * @property width 区域宽度，取正值
 * @property height 区域高度，取正值
 * @property action 动作标识，用于区分同一组件内的不同热区
 */
data class WidgetHitRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val action: String,
) {
    init {
        require(width > 0) { "命中区域宽度必须为正数" }
        require(height > 0) { "命中区域高度必须为正数" }
    }

    /**
     * 判断局部坐标是否落在区域内。
     *
     * 边界按左闭右开处理，避免相邻区域在公共边界上重复命中。
     *
     * @param px 待判定横坐标，单位像素
     * @param py 待判定纵坐标，单位像素
     * @return 坐标落在区域内返回 true
     */
    fun contains(
        px: Float,
        py: Float,
    ): Boolean = px >= x.toFloat() && px < (x + width).toFloat() && py >= y.toFloat() && py < (y + height).toFloat()
}

/** 提供组件内命中区域描述的视图，供宿主按坐标做事件分发。 */
interface WidgetHitRegionSource {
    /**
     * 命中区域列表，按 z 序从前往后排列，重叠时以先出现者为准。
     *
     * 默认返回空列表，未改造的组件因此保持既有行为：宿主只把坐标原样转发，
     * 不做区域判定。
     */
    fun hitRegions(): List<WidgetHitRegion> = emptyList()
}

/**
 * 按坐标查找最靠前的命中区域。
 *
 * @param x 局部坐标横轴，单位像素
 * @param y 局部坐标纵轴，单位像素
 * @return 命中的区域；未命中任何区域时返回 null
 */
fun WidgetHitRegionSource.hitRegionAt(
    x: Float,
    y: Float,
): WidgetHitRegion? = hitRegions().firstOrNull { it.contains(x, y) }

/**
 * 组件内触摸动作，取值与 MotionEvent 的动作对齐。
 *
 * 宿主把按下、移动、抬起、取消四种动作按组件局部坐标转发给组件，
 * 由组件决定是否消费，未被消费的移动交回宿主用于翻页与托盘手势。
 */
enum class WidgetTouchAction {
    /** 手指按下。 */
    DOWN,

    /** 手指移动。 */
    MOVE,

    /** 手指抬起。 */
    UP,

    /** 手势中断。 */
    CANCEL,
}

/**
 * 接收组件内连续手势的视图。
 *
 * 仅点击与长按无法表达纵向滚动、拖动与展开，需要这类交互的组件实现本接口，
 * 宿主在组件区域内把触摸动作按顺序转发。
 */
interface WidgetTouchTarget {
    /**
     * 处理组件内触摸动作。
     *
     * @param action 触摸动作
     * @param x 组件局部横轴坐标，单位像素
     * @param y 组件局部纵轴坐标，单位像素
     * @return 是否消费该动作；返回 false 时宿主继续按桌面手势处理
     */
    fun onTouch(
        action: WidgetTouchAction,
        x: Float,
        y: Float,
    ): Boolean = false
}

/**
 * 组件视图协议：组件只负责在给定矩形内绘制，不持有页面与 Activity。
 *
 * 尺寸由宿主通过 draw 传入，组件在 onSizeChanged 中缓存布局参数；
 * 数据变化后调用 BaseWidgetView.requestDraw 通知宿主重绘。
 * 需要精确分发热区的组件实现 hitRegions，用组件局部像素描述可交互区域。
 */
interface WidgetView :
    WidgetHitRegionSource,
    WidgetTouchTarget {
    /** 首次绘制或尺寸变化时回调，参数为可用宽高像素。 */
    fun onSizeChanged(
        width: Int,
        height: Int,
    ) {}

    /** 桌面配置变化时回调。 */
    fun onConfigChanged(config: LauncherConfig) {}

    /** 数据刷新完成后回调，参数 force 表示忽略缓存。 */
    fun onRefresh(force: Boolean = false) {}

    /** 在指定区域绘制，左上角为 (0,0)。 */
    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
    )

    /** 点击事件，返回 true 表示已消费。 */
    fun onClick(
        x: Float,
        y: Float,
    ): Boolean = false

    /**
     * 是否需要透明底板。
     *
     * 返回 true 时宿主跳过圆角白底与边框绘制，让壁纸透出；
     * 时钟类组件默认开启，其他组件保持纸白底板。
     */
    val transparentBackground: Boolean
        get() = false

    /**
     * 长按事件，无坐标重载。
     *
     * 保留该重载供既有宿主调用；默认转调坐标版本并传入 NaN，
     * 表示按下点坐标未知，组件据此退化为整体长按行为。
     */
    fun onLongPress() {
        onLongPress(Float.NaN, Float.NaN)
    }

    /**
     * 长按事件，坐标版本。
     *
     * 坐标为组件局部像素，与 [hitRegions] 使用同一坐标系；宿主在按下点位于
     * 组件内部时传入实际坐标，坐标未知时由无参重载传入 NaN。
     *
     * @param x 局部坐标横轴，单位像素
     * @param y 局部坐标纵轴，单位像素
     */
    fun onLongPress(
        x: Float,
        y: Float,
    ) {}

    /** 组件被替换或页面销毁时释放资源。 */
    fun onDestroy() {}
}

/** 宿主协议：组件通过它请求重绘与页面跳转。 */
interface WidgetHost {
    /** 请求重绘指定组件所在区域。 */
    fun requestWidgetDraw(widget: WidgetView)

    /** 打开内置页面。 */
    fun openDestination(destination: Destination)
}

/**
 * 组件视图基类：缓存尺寸、提供画笔与重绘入口。
 *
 * 子类实现 onDraw 完成绘制即可；需要定时变化的组件自行启动计时器，
 * 并在 onDestroy 中停止。
 */
abstract class BaseWidgetView(
    protected val context: Context,
) : WidgetView {
    /** 宿主，由桌面在创建组件时挂接。 */
    var host: WidgetHost? = null

    /** 当前绘制宽度。 */
    protected var widthPx: Int = 0
        private set

    /** 当前绘制高度。 */
    protected var heightPx: Int = 0
        private set

    /** 尺寸令牌快捷入口。 */
    protected val tokens get() = DesignRuntime.tokens

    final override fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
    ) {
        if (width != widthPx || height != heightPx) {
            widthPx = width
            heightPx = height
            onSizeChanged(width, height)
        }
        onDraw(canvas)
    }

    /** 子类在此完成绘制。 */
    protected abstract fun onDraw(canvas: Canvas)

    /** 请求宿主重绘，应在主线程调用。 */
    protected fun requestDraw() {
        host?.requestWidgetDraw(this)
    }

    /** 创建文本画笔。 */
    protected fun textPaint(
        size: Float,
        color: Int = InkColors.INK,
    ): Paint = DesignRuntime.textPaint(size, color)

    /** 创建实心画笔。 */
    protected fun fillPaint(color: Int): Paint = DesignRuntime.fillPaint(color)

    /** 创建描边画笔。 */
    protected fun strokePaint(color: Int = InkColors.INK): Paint = DesignRuntime.strokePaint(color, tokens.stroke)
}

/**
 * 组件分类，用于选择页分组展示。
 *
 * 参数 title 为分组标题，直接显示在组件选择页。
 */
enum class WidgetCategory(
    val title: String,
) {
    TIME("时间"),
    LOCAL("本地内容"),
    WEREAD("微信读书"),
    OTHER("其他"),
}

/**
 * 组件刷新策略，声明整分刷新与数据变化时的刷新范围。
 *
 * 组件自行维护定时器的场景使用 [MANUAL]，避免桌面重复触发刷新。
 */
enum class WidgetRefreshPolicy {
    /** 依赖时间推进，整分刷新时重绘或重新取数。 */
    MINUTE,

    /** 依赖内容数据变化，仅在数据通知或页面恢复时刷新。 */
    EVENT,

    /** 由组件内部定时器驱动，桌面整分刷新不处理。 */
    MANUAL,
}

/**
 * 判断该刷新策略是否响应桌面整分刷新。
 *
 * 返回值仅对 [WidgetRefreshPolicy.MINUTE] 为 true，其余策略由数据通知或组件内部定时器驱动。
 * 该判定保持纯函数形式，便于宿主与回归用例共用同一规则。
 */
fun WidgetRefreshPolicy.triggersOnMinute(): Boolean = this == WidgetRefreshPolicy.MINUTE

/**
 * 组件工厂。
 *
 * id 为配置中持久化的标识，发布后不可更改；label 为选择器展示名；
 * category 决定选择页分组；refreshPolicy 决定整分刷新的适用范围；
 * requiredCapabilities 声明组件依赖的系统能力，供渲染层判定展示条件。
 */
interface WidgetProvider {
    val id: String
    val label: String
    val description: String
    val category: WidgetCategory get() = WidgetCategory.OTHER
    val refreshPolicy: WidgetRefreshPolicy get() = WidgetRefreshPolicy.MANUAL
    val requiredCapabilities: Set<Capability> get() = emptySet()

    /** 创建组件实例，每次放入桌面都新建。 */
    fun create(context: Context): WidgetView
}

/** 组件注册表，各功能模块启动时登记自己的组件。 */
object WidgetRegistry {
    private val providers = LinkedHashMap<String, WidgetProvider>()

    /** 注册组件，重复 id 会覆盖旧实现。 */
    fun register(provider: WidgetProvider) {
        providers[provider.id] = provider
    }

    /** 按 id 获取组件工厂。 */
    fun get(id: String): WidgetProvider? = providers[id]

    /** 全部已注册组件，按注册顺序。 */
    fun all(): List<WidgetProvider> = providers.values.toList()

    /** 创建组件实例。 */
    fun create(
        id: String,
        context: Context,
    ): WidgetView? = providers[id]?.create(context)
}

/** 空视图工具：为缺少数据的组件提供统一占位绘制。 */
object WidgetPlaceholder {
    /**
     * 绘制占位文案与虚线框。
     *
     * 参数 width 与 height 为组件可用尺寸；message 为占位说明。
     */
    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        message: String,
    ) {
        val tokens = DesignRuntime.tokens
        val paint = DesignRuntime.textPaint(tokens.textCaption, InkColors.INK_2)
        paint.textAlign = Paint.Align.CENTER
        val cx = width / 2f
        val cy = height / 2f + paint.textSize / 3f
        canvas.drawText(message, cx, cy, paint)
    }
}
