package com.shuiyu.octoberrain.feature.content.ui

import com.shuiyu.octoberrain.core.common.WereadNoteSnapshot
import com.shuiyu.octoberrain.core.common.WereadSection

/** 笔记分组：同一本书的划线聚合在一起。 */
internal data class NoteGroup(
    val bookTitle: String,
    val notes: List<WereadNoteSnapshot>,
)

/**
 * 微信读书页面的纯逻辑。
 *
 * 提供分段与周期名称、可见性判断、空态文案、笔记分组、热力等级与月历排布，
 * 供页面与测试共用；不依赖 Android 上下文。
 */
internal object WereadSectionLogic {
    /** 分段顺序，与顶部页签一致。 */
    val SECTIONS: List<WereadSection> =
        listOf(
            WereadSection.OVERVIEW,
            WereadSection.NOTES,
            WereadSection.BILL,
            WereadSection.RANK,
        )

    /**
     * 返回分段的中文名称。
     *
     * @param section 目标分段
     */
    fun label(section: WereadSection): String =
        when (section) {
            WereadSection.OVERVIEW -> "总览"
            WereadSection.NOTES -> "笔记"
            WereadSection.BILL -> "账单"
            WereadSection.RANK -> "排行"
        }

    /**
     * 返回可见分段；未配置密钥或来源关闭时不显示页签。
     *
     * @param configured 密钥是否已配置
     * @param enabled 微信读书来源是否启用
     */
    fun visibleSections(
        configured: Boolean,
        enabled: Boolean,
    ): List<WereadSection> = if (configured && enabled) SECTIONS else emptyList()

    /**
     * 返回分段在无数据时的提示文案。
     *
     * @param section 当前分段
     */
    fun emptyMessage(section: WereadSection): String =
        when (section) {
            WereadSection.OVERVIEW -> "暂无月度统计与书架数据，可稍后刷新重试。"
            WereadSection.NOTES -> "暂无笔记，划线同步后显示在此处。"
            WereadSection.BILL -> "暂无账单数据，本月阅读后生成明细。"
            WereadSection.RANK -> "暂无排行，书架有阅读进度后生成。"
        }

    /**
     * 按书名分组笔记，组内保持输入顺序。
     *
     * 书名缺失的笔记归入「未知书籍」，分组顺序按首次出现的书名排列。
     *
     * @param notes 笔记列表
     */
    fun groupNotes(notes: List<WereadNoteSnapshot>): List<NoteGroup> {
        val order = LinkedHashMap<String, MutableList<WereadNoteSnapshot>>()
        notes.forEach { note ->
            val title = note.bookTitle.trim().ifEmpty { "未知书籍" }
            order.getOrPut(title) { mutableListOf() }.add(note)
        }
        return order.map { (title, items) -> NoteGroup(title, items) }
    }

    /**
     * 返回与上期相比的说明文案。
     *
     * @param current 本期分钟数
     * @param last 上期分钟数
     */
    fun deltaText(
        current: Int,
        last: Int,
    ): String {
        val delta = current - last
        return when {
            delta > 0 -> "比上月多 ${ContentUi.formatMinutes(delta)}"
            delta < 0 -> "比上月少 ${ContentUi.formatMinutes(-delta)}"
            else -> "与上月持平"
        }
    }

    /**
     * 把阅读进度转换为百分比文案。
     *
     * @param progress 进度，取值 0 到 1，超出范围时夹取
     */
    fun progressText(progress: Float): String {
        val percent = (progress.coerceIn(0f, 1f) * 100f).toInt()
        return "已读 $percent%"
    }
}
