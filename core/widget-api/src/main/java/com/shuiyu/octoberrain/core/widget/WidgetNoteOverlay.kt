package com.shuiyu.octoberrain.core.widget

import android.graphics.Canvas

/** 只在内存传递的笔记快照；正文和身份不得写日志或持久配置。 */
class WidgetNoteContent(
    val identity: String,
    val title: String,
    val chapter: String,
    val body: String,
    val textSizePx: Float,
    val presentation: WidgetNotePresentation? = null,
    val needsExpansion: Boolean = true,
) {
    val displayChapter: String get() = noteChapterLabel(title, chapter)
}

/** 宿主只读取已准备缓存，换条由组件自己的后台队列执行。 */
interface WidgetNoteOverlaySource {
    fun currentNoteContent(): WidgetNoteContent?

    fun moveOverlayNote(previous: Boolean): Boolean

    /** Draw the complete landscape card using its original typography scale. */
    fun drawExpandedNoteCard(
        canvas: Canvas,
        width: Int,
        height: Int,
    ): Boolean = false
}

/** Widget-relative native body layout, retained while the host extends its clip. */
class WidgetNotePresentation(
    val textX: Float,
    val firstBaseline: Float,
    val lineHeight: Float,
    val quote: List<String>,
    val idea: List<String>,
    val scale: Float = 1f,
    val ideaTagBlock: Float = lineHeight,
    val landscape: WidgetLandscapeNotePresentation? = null,
    val buttonTextSize: Float = 17f,
)

/** Current landscape C body geometry and raw passages, in widget logical coordinates. */
data class WidgetLandscapeNotePresentation(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val quote: String,
    val idea: String,
    val quoteSize: Float,
    val unit: Float,
)

/** Some imported chapter records repeat the book title; show that label only once. */
fun noteChapterLabel(
    title: String?,
    chapter: String?,
): String =
    chapter
        .orEmpty()
        .trim()
        .takeUnless { it == title.orEmpty().trim() }
        .orEmpty()
