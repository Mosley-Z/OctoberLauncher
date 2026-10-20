package com.shuiyu.octoberrain.feature.widgets.kernel

import android.graphics.Canvas
import android.graphics.RectF
import com.inkread.weekread.kernel.BookProgress
import com.inkread.weekread.kernel.CardKernel
import com.inkread.weekread.kernel.CardState
import com.inkread.weekread.kernel.CardSurface
import com.inkread.weekread.kernel.HitRegions
import com.inkread.weekread.kernel.PeriodSnapshot
import com.shuiyu.octoberrain.core.common.WereadAppearance
import com.shuiyu.octoberrain.feature.widgets.WereadBoardDataSource
import com.shuiyu.octoberrain.feature.widgets.WereadBoardKernel
import com.shuiyu.octoberrain.feature.widgets.WereadBoardKernelHost
import com.shuiyu.octoberrain.feature.widgets.WereadBoardZone
import com.shuiyu.octoberrain.feature.widgets.WereadBookOption
import java.io.File
import kotlin.math.roundToInt

/** 未命中区域编号。 */
private const val HIT_NONE: Int = 0

/** 未渲染形态编号。 */
private const val FORM_NONE: Int = -1

/** 笔记读取条数。 */
private const val NOTE_LIMIT: Int = 1

/** 微读原盘数据目录名，位于应用私有 filesDir 之下。 */
private const val WEREAD_ROOT_DIR: String = "weread"

/** 墨单动作，内核打开负一屏墨单。 */
private const val ACTION_DESK: Int = 1

/** 打开动作，内核按当前形态打开详情页。 */
private const val ACTION_OPEN: Int = 2

/** 打开墨单的组件动作标识。 */
private const val ACTION_MENU_OPEN: String = "menu:open"

/** 打开正文的组件动作标识。 */
private const val ACTION_CARD_OPEN: String = "card:open"

/** 月形态标识。 */
private const val FORM_ID_MONTH: String = "month"

/** 本书形态标识。 */
private const val FORM_ID_BOOK: String = "book"

/** 本记形态标识。 */
private const val FORM_ID_NOTE: String = "note"

/** 待办形态标识。 */
private const val FORM_ID_TODO: String = "todo"

/** 卡片单位：画布缩放由组件完成，内核按逻辑像素绘制。 */
private const val CARD_UNIT: Float = 1.0f

/** 卡片左右留白比例，与原版桌面卡片一致。 */
private const val PAD_X_RATIO: Float = 0.05f

/** 本记条目序号，零为全部笔记。 */
private const val NOTE_SLOT_ALL: Int = 0

/** 未配置密钥时的占位主文案。 */
private const val PLACEHOLDER_NO_KEY: String = "微信读书未配置"

/** 已配置但无数据时的占位主文案。 */
private const val PLACEHOLDER_EMPTY: String = "暂无阅读数据"

/** 待办形态无数据时的占位主文案。 */
private const val PLACEHOLDER_TODO: String = "暂无待办"

/** 未配置密钥时的占位副文案。 */
private const val PLACEHOLDER_SUB: String = "在书架设置中填写密钥后显示"

/** 已配置密钥但无数据时的占位副文案。 */
private const val PLACEHOLDER_SUB_SYNC: String = "下拉刷新或稍后再试"

/** 未配置密钥时的本记提示。 */
private const val NOTE_HINT_NO_KEY: String = "还没填 API Key"

/** 同步失败时的本记提示。 */
private const val NOTE_HINT_SYNC_FAILED: String = "同步失败，请检查网络"

/** 同步完成但无笔记时的本记提示。 */
private const val NOTE_HINT_EMPTY: String = "还没有同步到笔记"

/** 周与月形态取数失败时的错误文案。 */
private const val ERROR_SYNC_FAILED: String = "同步失败，请检查网络"

/**
 * 读取外观偏好，读取失败时取默认外观。
 *
 * @param root 微读原盘根目录
 * @return 外观偏好快照
 */
private fun readAppearance(root: File): WereadAppearance = WereadAppearanceCache.read(root)

/**
 * 把组件形态标识映射为内核形态编号，未知标识按周形态处理。
 *
 * @param formId 组件形态标识
 * @return 内核形态编号
 */
private fun formOf(formId: String): Int =
    when (formId) {
        FORM_ID_MONTH -> CardKernel.FORM_MONTH
        FORM_ID_BOOK -> CardKernel.FORM_BOOK
        FORM_ID_NOTE -> CardKernel.FORM_NOTE
        FORM_ID_TODO -> CardKernel.FORM_TODO
        else -> CardKernel.FORM_WEEK
    }

/** 命中区域动作：切换形态。 */
private const val ZONE_FORM_NEXT: String = "form:next"

/** 命中区域动作：刷新数据。 */
private const val ZONE_REFRESH: String = "data:refresh"

/** 命中区域动作：打开当前书目。 */
private const val ZONE_BOOK_OPEN: String = "book:open"

/** 命中区域动作：弹出选书菜单。 */
private const val ZONE_BOOK_PICK: String = "book:pick"

/** 命中区域动作：上一条本记。 */
private const val ZONE_NOTE_PREV: String = "note:prev"

/** 命中区域动作：换一条本记。 */
private const val ZONE_NOTE_NEXT: String = "note:next"

/** 命中区域动作：展开或收起本记正文。 */
private const val ZONE_NOTE_EXPAND: String = "note:expand"

/** 命中区域动作：筛选本记。 */
private const val ZONE_NOTE_FILTER: String = "note:filter"

/** 待办条目的动作前缀，后缀为条目序号。 */
private const val ZONE_TODO_PREFIX: String = "todo:"

/** 抬头形态切换热区宽度，单位为内核逻辑像素。 */
private const val HEADER_TITLE_W: Float = 88f

/** 抬头更新于热区宽度，单位为内核逻辑像素。 */
private const val HEADER_REFRESH_W: Float = 128f

/** 抬头热区高度，单位为内核逻辑像素。 */
private const val HEADER_H: Float = 34f

/**
 * 整体卡片内核实现：组件与 k3 内核之间的适配层。
 *
 * 负责装配内核宿主、数据源与卡片状态，转发绘制、命中与滚动，并按组件宿主接口
 * 暴露命中区域与交互动作。
 */
@Suppress("TooManyFunctions") // 内核接缝的方法与内核接口一一对应，拆分后共享内核状态成本更高。
internal class KernelWereadBoard : WereadBoardKernel {
    /** 内核宿主。 */
    private var host: KernelHostImpl? = null

    /** 渲染进行中标记，用于屏蔽内核在渲染期间发起的重绘请求。 */
    private var rendering: Boolean = false

    /** 内核数据源。 */
    private var source: KernelDataSourceImpl? = null

    /** 内核实例。 */
    private var kernel: CardKernel? = null

    /** 内核卡片状态，承载绘制数据与命中区域。 */
    private var state: CardState? = null

    /** 卡片状态装配对象。 */
    private var filler: KernelCardFiller? = null

    /** 画布适配对象。 */
    private val surface = SurfaceAdapter()

    /** 最近一次渲染的形态编号，用于按形态选择滚动目标。 */
    private var lastForm: Int = FORM_NONE

    /** 最近一帧渲染回写的本记最大滚动距离。 */
    private var noteScrollMaxCache: Float = 0f

    /** 最近一帧渲染回写的本记可视高度。 */
    private var noteViewHCache: Float = 0f

    /** 最近一帧渲染回写的月历最大滚动距离。 */
    private var monthScrollMaxCache: Float = 0f

    /** 最近一帧的逻辑高度，用于月形态可视高度。 */
    private var frameHeightCache: Int = 0

    /**
     * 构造命中区域。
     *
     * @param x 左边界
     * @param y 上边界
     * @param width 宽度
     * @param height 高度
     * @param action 动作标识
     * @return 命中区域
     */
    private fun zoneOf(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        action: String,
    ): WereadBoardZone = WereadBoardZone(x, y, width, height, action)

    /**
     * 追加渲染器回写的矩形区域，空矩形忽略。
     *
     * @param target 目标列表
     * @param rect 渲染器回写的矩形，可为空
     * @param action 动作标识
     */
    private fun addZone(
        target: MutableList<WereadBoardZone>,
        rect: RectF?,
        action: String,
    ) {
        if (rect == null || rect.width() <= 0f || rect.height() <= 0f) return
        target += WereadBoardZone(rect.left, rect.top, rect.width(), rect.height(), action)
    }

    /**
     * 画布适配：把组件传入的画布与尺寸转交内核，命中与动作回落到卡片状态。
     */
    private inner class SurfaceAdapter : CardSurface {
        /** 当前帧画布。 */
        var target: Canvas? = null

        /** 当前帧宽度。 */
        var frameWidth: Int = 0

        /** 当前帧高度。 */
        var frameHeight: Int = 0

        /** 返回当前帧画布。 */
        override fun canvas(): Canvas? = target

        /** 返回当前帧宽度。 */
        override fun width(): Int = frameWidth

        /** 返回当前帧高度。 */
        override fun height(): Int = frameHeight

        /** 转交内核命中判定，未绑定状态时返回未命中。 */
        override fun hitTest(
            x: Float,
            y: Float,
        ): Int = state?.hitTest(x, y) ?: HIT_NONE

        /** 转交内核动作处理。 */
        override fun onAction(action: Int) {
            state?.onAction(action)
        }
    }

    /**
     * 组装宿主与数据源并绑定内核。
     *
     * 重复调用会重建宿主、数据源与卡片状态，旧内核资源由调用方先行释放。
     *
     * @param host 组件转交的宿主接口
     * @param dataSource 组件转交的数据源接口
     */
    override fun bind(
        host: WereadBoardKernelHost,
        dataSource: WereadBoardDataSource,
    ) {
        val hostImpl =
            KernelHostImpl(host) {
                if (!rendering) host.requestDraw()
            }
        val root = File(host.context.filesDir, WEREAD_ROOT_DIR)
        val sourceImpl = KernelDataSourceImpl({ dataSource.content }, { root })
        val cardState = CardState(surface)
        cardState.setHost(hostImpl)
        val cardKernel = CardKernel(hostImpl, sourceImpl)
        this.host = hostImpl
        this.source = sourceImpl
        this.state = cardState
        this.kernel = cardKernel
        this.filler = KernelCardFiller(sourceImpl, hostImpl)
        cardKernel.bind()
    }

    /**
     * 预取当前形态数据并重新绑定内核，清空上一次读取的缓存与失败标记。
     *
     * 由组件在后台线程调用，避免绘制线程承担数据读取。
     *
     * @return 是否完成预取
     */
    override fun preload(): Boolean {
        val cardKernel = kernel ?: return false
        source?.invalidate()
        cardKernel.bind()
        return true
    }

    /**
     * 按形态编号渲染卡片。
     *
     * @param canvas 组件提供的画布
     * @param width 逻辑宽度
     * @param height 逻辑高度
     * @param formId 组件形态标识
     */
    override fun render(
        canvas: Canvas,
        width: Int,
        height: Int,
        formId: String,
    ) {
        val cardKernel = kernel ?: return
        val cardState = state ?: return
        surface.target = canvas
        surface.frameWidth = width
        surface.frameHeight = height
        val form = formOf(formId)
        val appearance = host?.filesDir()?.let(::readAppearance) ?: WereadAppearance()
        rendering = true
        try {
            filler?.apply(cardState, form, appearance)
            cardKernel.render(cardState, form)
        } finally {
            rendering = false
        }
        lastForm = form
        frameHeightCache = height
        noteScrollMaxCache = cardState.noteScrollMax()
        noteViewHCache = cardState.noteViewH()
        monthScrollMaxCache = cardState.monthScrollMax()
    }

    /**
     * 切换内核形态。
     *
     * @param formId 组件形态标识
     */
    override fun setForm(formId: String) {
        kernel?.setForm(formOf(formId))
    }

    /**
     * 按逻辑坐标判定内核命中区域。
     *
     * @param x 逻辑横坐标
     * @param y 逻辑纵坐标
     * @return 内核命中区域编号，未命中为零
     */
    override fun hitTest(
        x: Float,
        y: Float,
    ): Int = state?.hitTest(x, y) ?: HIT_NONE

    /**
     * 处理内核命中区域。
     *
     * 展开与收起在内核状态中翻转；页面跳转类区域交给卡片状态机；
     * 滚动与待办行区域需要连续手势与列表位置，交由组件按未消费处理。
     *
     * @param hit 内核命中区域编号
     * @return 内核已处理时返回 true
     */
    override fun handleHit(hit: Int): Boolean {
        val cardState = state ?: return false
        return when {
            hit == HitRegions.NONE -> false
            hit == HitRegions.NOTE_SCROLL || hit == HitRegions.MONTH_SCROLL -> false
            todoItemIndex(hit) >= 0 -> false
            hit == HitRegions.EXPAND -> {
                cardState.toggleNoteExpanded()
                host?.requestRedraw()
                true
            }

            else -> {
                cardState.onAction(hit)
                true
            }
        }
    }

    /**
     * 把内核待办条目编号换算为条目下标。
     *
     * @param hit 内核命中区域编号
     * @return 条目下标，非待办条目返回负一
     */
    private fun todoItemIndex(hit: Int): Int {
        val offset = hit - HitRegions.TODO_ITEM_BASE
        return offset.takeIf { it in 0 until HitRegions.TODO_MAX } ?: -1
    }

    /**
     * 处理组件动作标识。
     *
     * 内核以整型编号表达动作：一为打开墨单，二为打开当前形态的详情页。
     * 隐藏、刷新与形态切换由组件自身处理，此处返回 false 交由组件继续处理。
     *
     * @param action 组件命中区域的动作标识
     * @return 是否已由内核消费
     */
    override fun onAction(action: String): Boolean {
        val cardState = state ?: return false
        return when (action) {
            ACTION_MENU_OPEN -> {
                cardState.onAction(ACTION_DESK)
                true
            }

            ACTION_CARD_OPEN -> filler?.openBody(cardState) ?: false
            else -> false
        }
    }

    /**
     * 当前形态的可点区域。
     *
     * 抬头两块固定存在；本书、本记与待办形态追加渲染器每帧回写的按钮与条目矩形，
     * 周与月形态不带正文按钮，正文点击由组件按形态补齐。
     *
     * @return 可点区域列表，坐标采用内核逻辑坐标
     */
    override fun zones(): List<WereadBoardZone> {
        val cardState = state ?: return emptyList()
        val zones = mutableListOf<WereadBoardZone>()
        zones += zoneOf(0f, 0f, HEADER_TITLE_W, HEADER_H, ZONE_FORM_NEXT)
        zones += zoneOf(HEADER_TITLE_W, 0f, HEADER_REFRESH_W, HEADER_H, ZONE_REFRESH)
        when (lastForm) {
            CardKernel.FORM_BOOK -> {
                addZone(zones, cardState.pickBox(), ZONE_BOOK_PICK)
                addZone(zones, cardState.openBox(), ZONE_BOOK_OPEN)
            }

            CardKernel.FORM_NOTE -> {
                addZone(zones, cardState.prevBox(), ZONE_NOTE_PREV)
                addZone(zones, cardState.openBox(), ZONE_NOTE_NEXT)
                addZone(zones, cardState.expandBox(), ZONE_NOTE_EXPAND)
                addZone(zones, cardState.filterBox(), ZONE_NOTE_FILTER)
            }

            CardKernel.FORM_TODO -> {
                cardState.todoBoxes().forEachIndexed { index, rect ->
                    addZone(zones, rect, ZONE_TODO_PREFIX + index)
                }
            }

            else -> Unit
        }
        return zones
    }

    /**
     * 切到上一条本记：数据源条目序号前移后重新绑定内核。
     *
     * @return 是否发生切换
     */
    override fun previousNote(): Boolean {
        val source = source ?: return false
        val changed = source.previousNote()
        if (changed) {
            kernel?.bind()
            host?.requestRedraw()
        }
        return changed
    }

    /**
     * 切到下一条本记：数据源条目序号后移后重新绑定内核。
     *
     * @return 是否发生切换
     */
    override fun nextNote(): Boolean {
        val source = source ?: return false
        val changed = source.nextNote()
        if (changed) {
            kernel?.bind()
            host?.requestRedraw()
        }
        return changed
    }

    /**
     * 当前在读书目编号。
     *
     * @return 书目编号，未取到书目时为空
     */
    override fun currentBookId(): String? {
        val current = source?.currentBook()?.bookId
        return current
            ?: source
                ?.shelf()
                ?.books
                ?.firstOrNull()
                ?.bookId
    }

    /**
     * 书架候选列表。
     *
     * @return 候选书目
     */
    override fun bookOptions(): List<WereadBookOption> {
        val shelf = source?.shelf() ?: return emptyList()
        return shelf.books.map { WereadBookOption(it.bookId, it.title) }
    }

    /**
     * 当前选中的书目编号。
     *
     * @return 选中的书目编号，自动档为空
     */
    override fun selectedBookId(): String? = source?.selectedBookId()

    /**
     * 切换本书形态的目标书目。
     *
     * @param bookId 书目编号，传空表示回到自动档
     * @return 是否完成切换
     */
    override fun selectBook(bookId: String?): Boolean {
        val dataSource = source ?: return false
        dataSource.selectBook(bookId)
        kernel?.bind()
        host?.requestRedraw()
        return true
    }

    /**
     * 待办条目标识列表。
     *
     * @return 标识列表，与待办命中区域顺序一致
     */
    override fun todoIds(): List<String> = source?.todos()?.map { it.id.toString() }.orEmpty()

    /**
     * 清空数据缓存并重新绑定内核。
     *
     * @return 是否完成刷新
     */
    override fun refreshData(): Boolean {
        val dataSource = source ?: return false
        dataSource.invalidate()
        kernel?.bind()
        return true
    }

    /**
     * 当前待办条目数量。
     *
     * @return 条目数量，未绑定时为零
     */
    override fun todoCount(): Int = state?.todoCount() ?: 0

    /**
     * 翻转本记正文展开状态。
     *
     * @return 是否发生翻转
     */
    override fun toggleNoteExpanded(): Boolean {
        val cardState = state ?: return false
        cardState.toggleNoteExpanded()
        host?.requestRedraw()
        return true
    }

    /**
     * 当前形态是否有可滚动内容。
     *
     * @return 是否存在滚动上限
     */
    override fun canScroll(): Boolean = scrollMax > 0

    /** 当前形态可滚动的最大距离。 */
    override val scrollMax: Int
        get() =
            when (lastForm) {
                CardKernel.FORM_NOTE -> noteScrollMaxCache.toInt()
                CardKernel.FORM_MONTH -> monthScrollMaxCache.toInt()
                else -> 0
            }

    /** 当前形态可视区域的滚动高度。 */
    override val scrollViewHeight: Int
        get() =
            when (lastForm) {
                CardKernel.FORM_NOTE -> noteViewHCache.toInt()
                CardKernel.FORM_MONTH -> frameHeightCache
                else -> 0
            }

    /**
     * 按逻辑像素滚动当前形态。
     *
     * 本记形态滚动笔记正文并按行高吸附；月形态滚动月历整页。滚动量已到边界时返回 false，
     * 由宿主继续处理翻页手势。滚动位置分别经卡片状态的公开设置方法写入。
     *
     * @param delta 本次滚动量，正数向下
     * @return 是否发生滚动
     */
    override fun scrollBy(delta: Int): Boolean {
        val cardState = state ?: return false
        val applied =
            when (lastForm) {
                CardKernel.FORM_NOTE -> scrollNote(cardState, delta)
                CardKernel.FORM_MONTH -> scrollMonth(cardState, delta)
                else -> false
            }
        if (applied) host?.requestRedraw()
        return applied
    }

    /** 复位当前形态的滚动位置。 */
    override fun resetScroll() {
        state?.resetScroll()
        host?.requestRedraw()
    }

    /**
     * 滚动本记正文，滚动量按行高吸附。
     *
     * @param cardState 内核卡片状态
     * @param delta 本次滚动量，正数向下
     * @return 是否发生滚动
     */
    private fun scrollNote(
        cardState: CardState,
        delta: Int,
    ): Boolean {
        val max = cardState.noteScrollMax()
        val current = cardState.noteScrollY()
        val lineHeight = cardState.noteLineH()
        val target = (current + delta).coerceIn(0f, max)
        val snapped = if (lineHeight > 0f) (target / lineHeight).roundToInt() * lineHeight else target
        val clamped = snapped.coerceIn(0f, max)
        val applied = max > 0f && clamped != current
        if (applied) cardState.setNoteScrollY(clamped)
        return applied
    }

    /**
     * 滚动月历整页，滚动量不做行吸附。
     *
     * @param cardState 内核卡片状态
     * @param delta 本次滚动量，正数向下
     * @return 是否发生滚动
     */
    private fun scrollMonth(
        cardState: CardState,
        delta: Int,
    ): Boolean {
        val max = monthScrollMaxCache
        val current = cardState.monthScrollY()
        val target = (current + delta).coerceIn(0f, max)
        val applied = max > 0f && target != current
        if (applied) cardState.setMonthScrollY(target)
        return applied
    }

    /** 释放内核资源并清空绑定。 */
    override fun release() {
        kernel?.release()
        kernel = null
        state = null
        host = null
        source = null
        filler = null
        lastForm = FORM_NONE
        surface.target = null
    }
}

/**
 * 卡片状态装配：按形态把数据源结果写入内核卡片状态，并补齐内核绑定未覆盖的外观与提示字段。
 *
 * 内核绑定阶段自行读取过一次数据，宿主每帧重新写入，保证形态切换与数据刷新后绘图取到最新值；
 * 滚动位置与滚动上限由内核渲染器回写，本类只负责旁路状态与数据。
 *
 * @param source 内核数据源
 * @param host 内核宿主
 */
@Suppress("TooManyFunctions") // 逐形态装配、外观写入与提示文案同属卡片状态装配职责，拆分后共享参数更多。
private class KernelCardFiller(
    private val source: KernelDataSourceImpl,
    private val host: KernelHostImpl,
) {
    /** 最近一次渲染的形态编号，用于切换形态时重置滚动。 */
    private var renderedForm: Int = FORM_NONE

    /**
     * 渲染前同步卡片状态：宿主信息、外观参数、形态与数据。
     *
     * 形态切换时重置滚动位置与展开状态，避免上一形态的偏移延续到新形态。
     *
     * @param cardState 内核卡片状态
     * @param form 内核形态编号
     * @param appearance 组件外观偏好
     */
    fun apply(
        cardState: CardState,
        form: Int,
        appearance: WereadAppearance,
    ) {
        applyAppearance(cardState, appearance)
        if (form != renderedForm) {
            cardState.setNoteExpanded(false)
            cardState.setFullscreen(false)
            cardState.resetScroll()
            renderedForm = form
        }
        cardState.setMode(CardKernel.modeOf(form))
        val filled = fillData(cardState, form)
        // 待办形态保留抬头，空列表由内核绘制「暂无待办」，占位文案只用于其余形态。
        val placeholder = !filled && form != CardKernel.FORM_TODO
        cardState.setPhMain(if (placeholder) placeholderMain(host.apiKey().isEmpty(), form) else null)
        cardState.setPhSub(if (placeholder) placeholderSub(host.apiKey().isEmpty()) else null)
        cardState.setAchievementText(achievementText(appearance, form))
        applyNoteState(cardState, form, filled)
    }

    /**
     * 按外观偏好推导成就行文案。
     *
     * 成就关闭或两类目标都未设置时返回空，由内核不绘制成就行；周形态取周目标，
     * 月形态取月目标，另一形态不输出。
     *
     * @param appearance 组件外观偏好
     * @param form 内核形态编号
     * @return 成就行文案，不显示时为空
     */
    private fun achievementText(
        appearance: WereadAppearance,
        form: Int,
    ): String? {
        val minutes =
            if (!appearance.achievementEnabled) {
                0
            } else {
                when (form) {
                    CardKernel.FORM_WEEK -> appearance.effectiveWeekMin
                    CardKernel.FORM_MONTH -> appearance.effectiveMonthMin
                    else -> 0
                }
            }
        val label = if (form == CardKernel.FORM_MONTH) "本月目标" else "本周目标"
        return minutes.takeIf { it > 0 }?.let { "$label $it 分钟" }
    }

    /**
     * 写入内核绑定未覆盖的外观参数，取值来自只读外观偏好。
     *
     * @param cardState 内核卡片状态
     * @param appearance 组件外观偏好
     */
    private fun applyAppearance(
        cardState: CardState,
        appearance: WereadAppearance,
    ) {
        cardState.setApiKey(host.apiKey())
        cardState.setRefreshing(false)
        cardState.setUnit(CARD_UNIT)
        cardState.setPadXRatio(PAD_X_RATIO)
        cardState.setFullscreen(false)
        cardState.setDeskEnabled(host.deskEnabled())
        cardState.setDeskOpenLabel(host.deskOpenLabel())
        cardState.setNoteCardSize(appearance.effectiveNoteSize)
        cardState.setNoteFullSize(appearance.effectiveBodySize)
        cardState.setNoteSlot(NOTE_SLOT_ALL)
        cardState.setMonthHeatmap(appearance.monthHeatmap)
        cardState.setEmptyNote(null)
    }

    /**
     * 写入本记提示与取数失败文案。
     *
     * 本记无内容时按缺密钥、同步失败与尚无笔记三种情况给出提示；
     * 周与月形态取数失败时写入错误文案，由内核绘制「获取失败」。
     *
     * @param cardState 内核卡片状态
     * @param form 内核形态编号
     * @param filled 当前形态是否取得可绘制数据
     */
    private fun applyNoteState(
        cardState: CardState,
        form: Int,
        filled: Boolean,
    ) {
        val syncFailed = source.hasFailure()
        cardState.setErrorText(if (!filled && syncFailed) ERROR_SYNC_FAILED else null)
        cardState.setNoteHint(if (form == CardKernel.FORM_NOTE && !filled) noteHint(syncFailed) else null)
    }

    /**
     * 取本记提示文案。
     *
     * @param syncFailed 最近一次读取是否失败
     * @return 本记提示文案
     */
    private fun noteHint(syncFailed: Boolean): String =
        when {
            host.apiKey().isEmpty() -> NOTE_HINT_NO_KEY
            syncFailed -> NOTE_HINT_SYNC_FAILED
            else -> NOTE_HINT_EMPTY
        }

    /**
     * 在书籍、笔记与待办形态下把正文点击转交内核打开动作。
     *
     * 周与月形态的正文点击由组件打开微信读书统计页，此处不消费。
     *
     * @param cardState 内核卡片状态
     * @return 是否已由内核消费
     */
    fun openBody(cardState: CardState): Boolean {
        val openable =
            renderedForm == CardKernel.FORM_BOOK ||
                renderedForm == CardKernel.FORM_NOTE ||
                renderedForm == CardKernel.FORM_TODO
        if (openable) {
            cardState.onAction(ACTION_OPEN)
        }
        return openable
    }

    /**
     * 按形态把数据写入卡片状态。
     *
     * @param cardState 内核卡片状态
     * @param form 内核形态编号
     * @return 是否取得可用于绘制的数据
     */
    private fun fillData(
        cardState: CardState,
        form: Int,
    ): Boolean =
        when (form) {
            CardKernel.FORM_WEEK, CardKernel.FORM_MONTH -> fillStats(cardState, form)
            CardKernel.FORM_BOOK -> fillBook(cardState)
            CardKernel.FORM_NOTE -> fillNote(cardState)
            CardKernel.FORM_TODO -> fillTodo(cardState)
            else -> false
        }

    /**
     * 写入周期统计，无数据时清空状态中的统计，避免沿用上一形态的数值。
     *
     * @param cardState 内核卡片状态
     * @param form 内核形态编号
     * @return 是否存在可绘制数据
     */
    private fun fillStats(
        cardState: CardState,
        form: Int,
    ): Boolean {
        val stats: PeriodSnapshot? = source.period(CardKernel.modeOf(form))
        cardState.setStats(stats)
        val usable = stats != null && (stats.totalSec > 0 || stats.readDays > 0)
        return usable
    }

    /**
     * 写入当前在读书目与封面。
     *
     * 原盘 book.json 携带阅读进度与累计阅读秒数，优先使用；缺失时回退到书架首本，
     * 此时进度与时长由内核按未知处理。
     *
     * @param cardState 内核卡片状态
     * @return 是否存在可绘制数据
     */
    private fun fillBook(cardState: CardState): Boolean {
        val shelf = source.shelf()
        val book = source.currentBook() ?: shelf?.books?.firstOrNull()
        if (book == null) {
            cardState.setBook(null)
            cardState.setCoverBitmap(null, null)
            return false
        }
        val progress = BookProgress()
        progress.bookId = book.bookId
        progress.title = book.title
        progress.author = book.author
        progress.coverUrl = book.coverUrl
        progress.progress = book.progress
        progress.readingSec = book.readingSec
        progress.finished = book.finished
        progress.fetchedAt = shelf?.fetchedAt ?: 0L
        cardState.setBook(progress)
        cardState.setCoverBitmap(host.coverBitmap(book.bookId, book.coverUrl), book.bookId)
        return true
    }

    /**
     * 写入最新一条笔记，无笔记时清空状态并返回 false。
     *
     * @param cardState 内核卡片状态
     * @return 是否存在可绘制数据
     */
    private fun fillNote(cardState: CardState): Boolean {
        val note = source.notes(NOTE_LIMIT).firstOrNull()
        if (note == null) {
            cardState.setNote(null)
            return false
        }
        cardState.setNote(KernelDataMapper.contentOf(note))
        return true
    }

    /**
     * 写入待办列表，无条目时返回 false。
     *
     * @param cardState 内核卡片状态
     * @return 是否存在可绘制数据
     */
    private fun fillTodo(cardState: CardState): Boolean {
        val todos = source.todos()
        cardState.setTodo(todos)
        return todos.isNotEmpty()
    }
}

/**
 * 取占位主文案，按形态与密钥状态选择提示语。
 *
 * @param missingKey 是否缺少密钥
 * @param form 内核形态编号
 * @return 占位主文案
 */
private fun placeholderMain(
    missingKey: Boolean,
    form: Int,
): String =
    when {
        form == CardKernel.FORM_TODO -> PLACEHOLDER_TODO
        missingKey -> PLACEHOLDER_NO_KEY
        else -> PLACEHOLDER_EMPTY
    }

/**
 * 取占位副文案，密钥缺失时引导配置，已配置时提示刷新。
 *
 * @param missingKey 是否缺少密钥
 * @return 占位副文案
 */
private fun placeholderSub(missingKey: Boolean): String = if (missingKey) PLACEHOLDER_SUB else PLACEHOLDER_SUB_SYNC
