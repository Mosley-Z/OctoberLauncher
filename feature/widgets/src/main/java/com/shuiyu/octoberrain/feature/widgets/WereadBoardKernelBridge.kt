package com.shuiyu.octoberrain.feature.widgets

import android.content.Context
import android.graphics.Canvas
import com.shuiyu.octoberrain.core.common.ContentSource

/**
 * 整体卡片内核宿主：向微读内核提供上下文与重绘请求。
 *
 * 由组件实现并转交，内核无需直接依赖视图基类。
 */
interface WereadBoardKernelHost {
    /** 组件上下文。 */
    val context: Context

    /** 请求组件重绘。 */
    fun requestDraw()
}

/**
 * 整体卡片内核数据源：向内核转交内容服务。
 *
 * 数据读取保持异步，内核在自身线程内调用内容服务的方法。
 */
interface WereadBoardDataSource {
    /** 内容服务，未就绪时为空。 */
    val content: ContentSource?
}

/**
 * 内核命中区域：逻辑坐标与动作标识。
 *
 * 坐标为内核卡片逻辑坐标，原点位于卡片左上角；组件按画布缩放系数换算为组件内像素。
 *
 * @param x 左边界
 * @param y 上边界
 * @param width 宽度
 * @param height 高度
 * @param action 动作标识，由组件按标识分发
 */
data class WereadBoardZone(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val action: String,
)

/**
 * 书架候选：供本书形态的选书菜单展示。
 *
 * @param bookId 书目编号
 * @param title 书名
 */
data class WereadBookOption(
    val bookId: String,
    val title: String,
)

/**
 * 轮播书目：桌面卡片叠放封面所需的单本书信息。
 *
 * 顺序沿用车架快照的原始顺序，选定书目不前置，保证滑动选书后整条列表位置稳定。
 *
 * @param bookId 书目编号
 * @param title 书名
 * @param author 作者
 * @param coverPath 封面来源，可为远程地址或本地文件路径，为空时绘制占位
 * @param progressPercent 阅读进度百分数，取值 0 到 100
 * @param readingSeconds 累计阅读秒数，零表示未知
 */
data class WereadBookCard(
    val bookId: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val progressPercent: Int,
    val readingSeconds: Int,
)

/**
 * 整体卡片内核接缝：由应用层实现并注入。
 *
 * 组件把画布、尺寸与形态交给内核，绘制、命中判定与页面跳转全部由内核完成；
 * 组件只负责缩放画布、转发触点与维护形态配置。
 */
@Suppress("TooManyFunctions") // 内核能力按绘制、数据与交互分组，接口方法数量由内核公开能力决定。
interface WereadBoardKernel {
    /**
     * 绑定宿主与数据源。
     *
     * @param host 内核宿主，提供上下文与重绘入口
     * @param dataSource 内核数据源，提供内容服务
     */
    fun bind(
        host: WereadBoardKernelHost,
        dataSource: WereadBoardDataSource,
    )

    /**
     * 预取当前形态所需数据并重新绑定内核。
     *
     * 在后台线程调用，把数据读取与占位判定从绘制线程移出；未绑定时返回 false。
     *
     * @return 是否完成预取
     */
    fun preload(): Boolean

    /**
     * 绘制当前形态。
     *
     * @param canvas 组件画布，原点位于组件左上角
     * @param width 逻辑宽度，单位与内核卡片基准一致
     * @param height 逻辑高度，单位与内核卡片基准一致
     * @param formId 当前形态标识，与组件形态枚举一致
     */
    fun render(
        canvas: Canvas,
        width: Int,
        height: Int,
        formId: String,
    )

    /**
     * 切换形态。
     *
     * @param formId 目标形态标识
     */
    fun setForm(formId: String)

    /**
     * 按逻辑坐标判定命中区域。
     *
     * @param x 逻辑横坐标
     * @param y 逻辑纵坐标
     * @return 内核命中区域编号，未命中为零
     */
    fun hitTest(
        x: Float,
        y: Float,
    ): Int

    /**
     * 处理内核命中区域。
     *
     * 页面跳转、展开与收起由内核状态机完成；滚动类区域需要连续手势，由组件返回未消费。
     *
     * @param hit 内核命中区域编号
     * @return 内核已处理时返回 true
     */
    fun handleHit(hit: Int): Boolean

    /**
     * 当前形态的可点区域，按绘制层级从前往后排列。
     *
     * 涵盖抬头形态切换与更新于、本书形态的打开与选书、本记形态的上一条、换一条、
     * 展开与筛选、待办形态的逐条复选框；周与月形态只提供抬头两块，正文点击由组件补齐。
     *
     * @return 可点区域列表，坐标采用内核逻辑坐标
     */
    fun zones(): List<WereadBoardZone>

    /**
     * 切到上一条本记，数据源条目序号前移。
     *
     * @return 是否发生切换
     */
    fun previousNote(): Boolean

    /**
     * 切到下一条本记，数据源条目序号后移。
     *
     * @return 是否发生切换
     */
    fun nextNote(): Boolean

    /**
     * 当前在读书目编号，供打开微信读书阅读页使用。
     *
     * @return 书目编号，未取到书目时为空
     */
    fun currentBookId(): String?

    /**
     * 书架候选列表，供本书形态的选书菜单展示。
     *
     * @return 书架书目，最多取数据源提供的数量
     */
    fun bookOptions(): List<WereadBookOption>

    /**
     * 轮播书目列表，按书架快照的原始顺序返回。
     *
     * 与 [bookOptions] 的区别在于不对选定书目做前置重排，桌面叠放封面需要稳定顺序，
     * 否则每次换书都会使整条列表位移，滑动过程中的位置与选中项无法对应。
     *
     * @return 轮播书目，无数据时为空
     */
    fun bookCards(): List<WereadBookCard>

    /**
     * 读取指定书目的最近一条有效划线。
     *
     * 用于卡片右上角的划线笔记；该书目没有划线或读取失败时返回空串。
     *
     * @param bookId 书目编号
     * @return 划线正文，可为空串
     */
    fun latestMark(bookId: String): String?

    /**
     * 当前选中的书目编号。
     *
     * @return 选中的书目编号，自动档返回空
     */
    fun selectedBookId(): String?

    /**
     * 按书目编号切换本书形态的目标书目。
     *
     * @param bookId 书目编号，传空表示回到自动档
     * @return 是否完成切换
     */
    fun selectBook(bookId: String?): Boolean

    /**
     * 待办条目的标识列表，顺序与待办命中区域一致。
     *
     * @return 标识列表
     */
    fun todoIds(): List<String>

    /**
     * 当前待办条目数量，供组件判断内核是否绘制了抬头。
     *
     * @return 条目数量，无待办时为零
     */
    fun todoCount(): Int

    /**
     * 清空数据缓存并重新绑定内核，供「更新于」点击后手动刷新。
     *
     * @return 是否完成刷新
     */
    fun refreshData(): Boolean

    /**
     * 翻转本记正文展开状态。
     *
     * @return 是否发生翻转
     */
    fun toggleNoteExpanded(): Boolean

    /**
     * 当前形态是否有可滚动内容。
     *
     * @return 本记或月形态存在滚动上限时返回 true
     */
    fun canScroll(): Boolean

    /**
     * 处理命中区域动作。
     *
     * @param action 动作标识，与命中区域描述一致
     * @return 内核已处理时返回 true，未处理时返回 false
     */
    fun onAction(action: String): Boolean

    /**
     * 按逻辑像素滚动当前形态内容。
     *
     * 本记形态滚动笔记正文，月形态滚动月历整页，其他形态不滚动。
     * 滚动成功返回 true；已到边界或当前形态不滚动时返回 false，由宿主继续处理翻页手势。
     *
     * @param delta 本次滚动量，正数向下，单位逻辑像素
     * @return 是否发生滚动
     */
    fun scrollBy(delta: Int): Boolean

    /**
     * 当前形态可滚动的最大距离，单位逻辑像素；不可滚动时为零。
     *
     * @return 最大滚动距离
     */
    val scrollMax: Int

    /**
     * 当前形态可视区域的滚动高度，单位逻辑像素；不可滚动时为零。
     *
     * @return 可视高度
     */
    val scrollViewHeight: Int

    /** 复位当前形态的滚动位置。 */
    fun resetScroll()

    /** 释放内核持有的资源。 */
    fun release()
}

/**
 * 内核构造入口：由应用层注册工厂。
 *
 * 工厂为空时组件绘制统一占位；注册后下一次绘制由内核接管。
 */
object WereadBoardKernelBridge {
    /** 内核工厂，参数为宿主与数据源，返回内核实例。 */
    @Volatile
    var factory: ((WereadBoardKernelHost, WereadBoardDataSource) -> WereadBoardKernel)? = null
}
