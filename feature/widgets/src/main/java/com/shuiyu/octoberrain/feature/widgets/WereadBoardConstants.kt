package com.shuiyu.octoberrain.feature.widgets

/** 阅读器跳转地址前缀，与既有启动方式保持一致。 */
internal const val WEREAD_READING_SCHEME: String = "weread://reading?bId="

/** 书架为空时的提示文案。 */
internal const val BOARD_EMPTY_TEXT: String = "暂无阅读数据"

/** 内核不可用时的提示文案。 */
internal const val PLACEHOLDER_UNAVAILABLE: String = "微读组件不可用"

/** 刷新标记文案。 */
internal const val REFRESH_MARK_LABEL: String = "刷新中"

/** 刷新标记字号。 */
internal const val REFRESH_MARK_SIZE: Float = 12f

/** 刷新标记相对信息行底边的上移比例。 */
internal const val REFRESH_MARK_TOP_RATIO: Float = 2.60f

/** 刷新标记与按钮之间的间距比例。 */
internal const val REFRESH_MARK_GAP_RATIO: Float = 0.80f

/** 刷新标记显示时长，单位毫秒。 */
internal const val REFRESH_MARK_MS: Long = 1200L

/** 落位动画时长，单位毫秒。 */
internal const val SETTLE_DURATION_MS: Float = 240f

/** 动画单帧间隔，单位毫秒。 */
internal const val FRAME_INTERVAL_MS: Long = 16L

/** 整分计时的周期，单位毫秒。 */
internal const val MINUTE_MS: Long = 60_000L

/** 触发换书所需的最小拖动量，单位为「一本」。 */
internal const val SWIPE_TRIGGER: Float = 0.30f

/** 位置差小于该值时直接落位，不再播放动画。 */
internal const val SETTLE_EPS: Float = 0.005f

/** 点击判定允许的位移，单位 dp。 */
internal const val SLOP_DP: Float = 4f

/** 横向位移需超过纵向位移的倍数才判定为拖动。 */
internal const val HORIZONTAL_BIAS: Float = 1.2f

/** 动作：切换形态。 */
internal const val ACTION_FORM_NEXT: String = "form:next"

/** 动作：刷新数据。 */
internal const val ACTION_REFRESH: String = "data:refresh"

/** 动作：打开书目。 */
internal const val ACTION_BOOK_OPEN: String = "book:open"

/** 动作：上一条本记。 */
internal const val ACTION_NOTE_PREV: String = "note:prev"

/** 动作：换一条本记。 */
internal const val ACTION_NOTE_NEXT: String = "note:next"

/** 动作：展开或收起本记正文。 */
internal const val ACTION_NOTE_EXPAND: String = "note:expand"

/** 动作：筛选本记。 */
internal const val ACTION_NOTE_FILTER: String = "note:filter"

/** 动作前缀：待办勾选。 */
internal const val ACTION_TODO_PREFIX: String = "todo:"

/** 动作：周与月正文。 */
internal const val ACTION_CARD_OPEN: String = "card:open"

/** 形态切换按钮的动作标识。 */
internal const val ACTION_LAYOUT: String = "weread_board:layout"

/** 刷新按钮的动作标识。 */
internal const val ACTION_REFRESH_BUTTON: String = "weread_board:refresh"

/** 打开按钮的动作标识。 */
internal const val ACTION_OPEN: String = "weread_board:open"

/** 封面区的动作标识。 */
internal const val ACTION_COVER: String = "weread_board:cover"

/** 逻辑卡片宽度基准，与内核一致。 */
internal const val BOARD_BASE_WIDTH: Float = 368f

/** 画布缩放下限。 */
internal const val BOARD_MIN_SCALE: Float = 0.6f

/** 画布缩放上限。 */
internal const val BOARD_MAX_SCALE: Float = 2.0f

/** 抬头高度，单位为内核逻辑像素。 */
internal const val BOARD_HEADER_H: Float = 34f

/** 抬头形态切换热区宽度，单位为内核逻辑像素。 */
internal const val BOARD_HEADER_TITLE_W: Float = 88f

/** 进入滚动所需的最小纵向位移，单位 dp。 */
internal const val SCROLL_START_DP: Float = 12f

/** 微读原盘数据目录名，位于应用私有 filesDir 之下。 */
internal const val WEREAD_ROOT_DIR: String = "weread"

/**
 * 按槽位宽度计算画布缩放系数，与内核基准宽度 368 对齐。
 *
 * @param width 组件宽度，单位像素
 * @return 缩放系数，宽度无效时为零
 */
internal fun boardScaleOf(width: Int): Float {
    if (width <= 0) return 0f
    return (width / BOARD_BASE_WIDTH).coerceIn(BOARD_MIN_SCALE, BOARD_MAX_SCALE)
}

/** 本书形态打开时定位的书目下标：最近在读的首本。 */
internal const val BOARD_FIRST_BOOK_INDEX: Int = 0
