package com.shuiyu.octoberrain.feature.content.ui.shelf

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.feature.content.ui.ContentUi

/** 封面宽高比：宽度为三份、高度为四份。 */
private const val COVER_RATIO_WIDTH = 3
private const val COVER_RATIO_HEIGHT = 4

/** 卡片圆角，取任务规格给出的十二至十六像素区间中值。 */
private const val CARD_CORNER_DP = 14f

/** 腰封与卡片底边的留白。 */
private const val RIBBON_GAP_DP = 8f

/** 封面解码的目标边长，与其余页面的缩略图档位一致。 */
private const val COVER_DECODE_DP = 160f

/** 阅读进度换算百分比所用的基准值。 */
private const val PERCENT_BASE = 100f

/** 书名与作者缺失时的占位文案。 */
private const val UNKNOWN_TITLE = "未知书名"
private const val UNKNOWN_AUTHOR = "未知作者"

private val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
private val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * 书架封面卡片。
 *
 * 卡片以书籍封面作为主体，封面之上叠加两处信息层：位于中下部的书名腰封、
 * 右上角的百分比角标。尺寸全部由设计令牌换算，卡片
 * 高度按封面比例从宽度推导，同一行卡片因此等高。
 */
object ShelfBookCard {
    /**
     * 创建一本书的卡片。
     *
     * @param context 宿主上下文
     * @param title 书名，空值时显示未知书名
     * @param author 作者，空值时显示未知作者
     * @param coverPath 封面地址或本地路径，缺失时显示书名首字
     * @param progress 阅读进度，取值 0 到 1，超出范围按端点截断
     * @param onClick 点击卡片的回调
     * @return 可直接加入双列网格的卡片视图
     */
    fun create(
        context: Context,
        title: String,
        author: String,
        coverPath: String?,
        progress: Float,
        onClick: () -> Unit,
    ): FrameLayout {
        val tokens = DesignRuntime.tokens
        val displayTitle = title.ifBlank { UNKNOWN_TITLE }
        val displayAuthor = author.ifBlank { UNKNOWN_AUTHOR }
        val percent = Math.round(progress.coerceIn(0f, 1f) * PERCENT_BASE)
        val card =
            ShelfCardView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                background =
                    InkWidgets.background(
                        tokens.dp(CARD_CORNER_DP),
                        InkColors.PAPER,
                        tokens.stroke,
                        InkColors.INK_4,
                    )
                clipToOutline = true
                isClickable = true
                isFocusable = true
                contentDescription = "《$displayTitle》，$displayAuthor"
                setOnClickListener { onClick() }
            }
        card.addView(coverView(context, coverPath, displayTitle))
        card.addView(ribbonView(context, displayTitle, displayAuthor))
        card.addView(badgeView(context, percent))
        return card
    }

    /** 封面层：铺满整张卡片，缺少图片时以书名首字占位。 */
    private fun coverView(
        context: Context,
        coverPath: String?,
        title: String,
    ): View {
        val tokens = DesignRuntime.tokens
        return ContentUi.coverView(context, coverPath, title, tokens.dp(COVER_DECODE_DP)).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
    }

    /**
     * 书名腰封：横向贯穿卡片宽度，压在封面中下部。
     *
     * 腰封底色取纸面色，描边取次级色，与封面之间形成清晰边界；
     * 内部书名在上、作者在下，两行均居中且限制单行。
     */
    private fun ribbonView(
        context: Context,
        title: String,
        author: String,
    ): LinearLayout {
        val tokens = DesignRuntime.tokens
        val ribbon =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(tokens.gap / 2, tokens.gap / 2, tokens.gap / 2, tokens.gap / 2)
                background =
                    InkWidgets.background(
                        0,
                        InkColors.PAPER,
                        tokens.stroke,
                        InkColors.INK_2,
                    )
                layoutParams =
                    FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM).apply {
                        bottomMargin = tokens.dp(RIBBON_GAP_DP)
                    }
            }
        ribbon.addView(labelView(context, title, tokens.textCaption, InkColors.INK))
        ribbon.addView(
            labelView(context, author, tokens.textSmall, InkColors.INK_2).apply {
                setPadding(0, tokens.gap / 4, 0, 0)
            },
        )
        return ribbon
    }

    /** 腰封内的单行文本，水平居中，超出可用宽度时以省略号收尾。 */
    private fun labelView(
        context: Context,
        text: String,
        sizePx: Float,
        color: Int,
    ): TextView =
        InkWidgets.textView(context, text, sizePx, color).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }

    /** 右上角百分比角标：反白块底色、加粗居中文字，底边带尖角。 */
    private fun badgeView(
        context: Context,
        percent: Int,
    ): View =
        ShelfProgressBadge(context, percent).apply {
            layoutParams =
                FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END)
        }
}

/**
 * 按封面比例确定高度的卡片容器。
 *
 * 宽度由父容器的均分权重决定，高度按封面的三比四比例换算，保证同一行
 * 卡片高度一致，并避免窄屏下出现横向溢出。
 */
private class ShelfCardView(
    context: Context,
) : FrameLayout(context) {
    /**
     * 以测量宽度推导高度后按精确尺寸重新测量。
     *
     * @param widthMeasureSpec 父容器给出的宽度测量规格
     * @param heightMeasureSpec 父容器给出的高度测量规格，实际高度由宽度比例决定
     */
    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        // 宽度由父容器的权重分配决定，此处只把宽度换算成三比四的高度并强制该高度，
        // 不改写宽度规格，避免卡片按可用宽度排版而在行内溢出。
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val height = width * COVER_RATIO_HEIGHT / COVER_RATIO_WIDTH
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }
}
