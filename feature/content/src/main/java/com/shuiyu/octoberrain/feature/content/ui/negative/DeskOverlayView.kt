package com.shuiyu.octoberrain.feature.content.ui.negative

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.feature.content.ui.ContentUi
import java.io.File

/**
 * 墨台的页内浮层。
 *
 * 浮层覆盖在墨台列表之上，用于展示书目详情与图片大图。点击浮层空白区域或按返回键关闭
 * 浮层，不发生页面跳转。同一时刻只展示一个浮层。
 *
 * @param context 页面上下文。
 */
internal class DeskOverlayView(
    context: Context,
) : LinearLayout(context) {
    private val tokens = DesignRuntime.tokens

    private val card: LinearLayout

    private val spacerTop: View

    private val spacerBottom: View

    init {
        orientation = VERTICAL
        setBackgroundColor(OVERLAY_BACKGROUND)
        isClickable = true
        isFocusable = true
        setPadding(tokens.gapLarge, tokens.gapLarge, tokens.gapLarge, tokens.gapLarge)
        spacerTop = View(context).apply { setOnClickListener { hide() } }
        spacerBottom = spacerTop
        addView(spacerTop, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        card = LinearLayout(context).apply { orientation = VERTICAL }
        addView(card, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        visibility = View.GONE
    }

    /** 关闭浮层并清空内容。 */
    fun hide() {
        card.removeAllViews()
        visibility = View.GONE
    }

    /** 当前是否处于展示状态。 */
    fun isShowing(): Boolean = visibility == View.VISIBLE

    /**
     * 展示书目详情。
     *
     * @param title 书名。
     * @param author 作者。
     * @param progress 阅读进度，取值 0 到 1。
     * @param coverPath 封面临时文件路径。
     * @param onOpenWeread 打开微信读书的回调，为空时不显示该按钮。
     */
    fun showBook(
        title: String,
        author: String,
        progress: Float,
        coverPath: String?,
        onOpenWeread: (() -> Unit)?,
    ) {
        prepareCard()
        card.addView(InkWidgets.title(context, title).apply { maxLines = 3 })
        card.addView(
            InkWidgets.caption(context, author).apply { setTextColor(InkColors.INK_2) },
        )
        card.addView(
            ContentUi.coverView(context, coverPath, title, tokens.listIcon * 6),
            LayoutParams(tokens.listIcon * 6, (tokens.listIcon * 6 * 4) / 3).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = tokens.gap
                bottomMargin = tokens.gap
            },
        )
        card.addView(InkWidgets.body(context, progressText(progress)))
        if (onOpenWeread != null) {
            card.addView(InkWidgets.spacer(context, tokens.gap))
            card.addView(
                InkWidgets.button(context, ACTION_OPEN_WEREAD, filled = true) {
                    hide()
                    onOpenWeread()
                },
            )
        }
        visibility = View.VISIBLE
    }

    /**
     * 展示图片大图。
     *
     * @param file 图片文件。
     */
    fun showImage(file: File) {
        prepareCard()
        card.addView(InkWidgets.body(context, file.name).apply { maxLines = 2 })
        val image =
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(InkColors.INK_4)
                setImageBitmap(decodeImage(file))
            }
        card.addView(
            image,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, tokens.listIcon * 12).apply {
                topMargin = tokens.gap
            },
        )
        card.addView(
            InkWidgets.caption(context, ContentUi.formatDateTime(file.lastModified())).apply {
                setTextColor(InkColors.INK_3)
            },
        )
        visibility = View.VISIBLE
    }

    /** 重置卡片外观与内容，供下一次展示使用。 */
    private fun prepareCard() {
        card.removeAllViews()
        card.setPadding(tokens.gap, tokens.gap, tokens.gap, tokens.gap)
        card.background =
            InkWidgets.background(tokens.radius, InkColors.PAPER, tokens.stroke, InkColors.INK)
    }

    /**
     * 按目标长边采样解码图片，避免整张原图占用内存。
     *
     * @param file 源图片文件。
     * @return 解码结果，失败返回空。
     */
    private fun decodeImage(file: File): Bitmap? =
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / sample > MAX_IMAGE_SIDE_PX && bounds.outHeight / sample > MAX_IMAGE_SIDE_PX) {
                sample *= 2
            }
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()

    /**
     * 把阅读进度转成展示文案。
     *
     * @param progress 进度，取值 0 到 1。
     * @return 进度文案。
     */
    private fun progressText(progress: Float): String {
        if (progress <= 0f) return PROGRESS_NONE
        return PROGRESS_PREFIX + (progress * 100).toInt() + "%"
    }

    private companion object {
        /** 浮层遮罩颜色，半透明黑。 */
        const val OVERLAY_BACKGROUND: Int = 0x66000000

        /** 未开始阅读的进度文案。 */
        const val PROGRESS_NONE: String = "未开始阅读"

        /** 阅读进度文案前缀。 */
        const val PROGRESS_PREFIX: String = "已读 "

        /** 打开微信读书按钮文案。 */
        const val ACTION_OPEN_WEREAD: String = "打开微信读书"

        /** 大图解码的目标长边，单位为像素。 */
        const val MAX_IMAGE_SIDE_PX: Int = 1080
    }
}
