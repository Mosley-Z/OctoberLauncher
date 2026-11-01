package com.shuiyu.octoberrain.feature.content.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkGlyphs
import com.shuiyu.octoberrain.core.design.InkWidgets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
private val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * 内容页共用构件。
 *
 * 统一卡片、空状态、键值行、封面缩略图与文本格式化，保证五个内容页面的视觉
 * 语言一致，尺寸全部取自 DesignRuntime.tokens。
 */
object ContentUi {
    private const val MAX_BITMAP_SIDE = 320

    /**
     * 添加加载提示并返回该视图。
     *
     * 调用方在内容渲染结束后需要自行移除返回的视图，避免加载文案与正文
     * 同时停留在页面上。
     */
    fun loading(container: LinearLayout): View {
        val view = InkWidgets.caption(container.context, "正在读取…")
        container.addView(view)
        return view
    }

    /** 从父容器移除加载提示，视图尚未加入或已被移除时忽略。 */
    fun removeLoading(loading: View?) {
        if (loading == null) return
        (loading.parent as? ViewGroup)?.removeView(loading)
    }

    /** 失败提示卡片。 */
    fun errorCard(
        container: LinearLayout,
        message: String,
    ) {
        val context = container.context
        val card = card(context)
        card.addView(InkWidgets.body(context, message))
        container.addView(card)
    }

    /** 创建带描边的白底卡片容器。 */
    fun card(context: Context): LinearLayout {
        val tokens = DesignRuntime.tokens
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background =
                InkWidgets.background(
                    tokens.radiusSmall,
                    InkColors.PAPER,
                    tokens.stroke,
                    InkColors.INK_4,
                )
            setPadding(tokens.gap, tokens.gap, tokens.gap, tokens.gap)
            layoutParams =
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                    topMargin = tokens.gap
                }
        }
    }

    /** 卡片内的标题行。 */
    fun cardTitle(
        context: Context,
        text: CharSequence,
    ): TextView =
        InkWidgets.body(context, text).apply {
            setPadding(0, 0, 0, DesignRuntime.tokens.gap)
        }

    /** 卡片内的说明文字。 */
    fun cardCaption(
        context: Context,
        text: CharSequence,
    ): TextView =
        InkWidgets.caption(context, text).apply {
            setPadding(0, 0, 0, DesignRuntime.tokens.gap)
        }

    /** 区块标题，带上方留白。 */
    fun sectionTitle(
        container: LinearLayout,
        text: CharSequence,
    ) {
        val context = container.context
        val tokens = DesignRuntime.tokens
        val heading = InkWidgets.heading(context, text)
        heading.setPadding(0, tokens.gapLarge, 0, tokens.gap)
        container.addView(heading)
    }

    /** 键值行：左侧灰色标签，右侧黑色数值。 */
    fun keyValue(
        context: Context,
        label: CharSequence,
        value: CharSequence,
    ): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            addView(
                InkWidgets.caption(context, label).apply {
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                },
            )
            addView(InkWidgets.body(context, value))
        }

    /** 空状态卡片，可选带一个操作按钮。 */
    fun emptyCard(
        context: Context,
        title: CharSequence,
        message: CharSequence,
        actionLabel: CharSequence? = null,
        onAction: (() -> Unit)? = null,
    ): LinearLayout {
        val card = card(context)
        card.addView(InkWidgets.body(context, title))
        card.addView(
            InkWidgets.caption(context, message).apply {
                setPadding(0, DesignRuntime.tokens.gap, 0, 0)
            },
        )
        if (actionLabel != null && onAction != null) {
            card.addView(
                InkWidgets.button(context, actionLabel, onClick = onAction).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(WRAP_CONTENT, DesignRuntime.tokens.buttonHeight).apply {
                            topMargin = DesignRuntime.tokens.gap
                        }
                },
            )
        }
        return card
    }

    /** 带操作按钮的提示卡片。 */
    fun noticeCard(
        context: Context,
        message: CharSequence,
        actionLabel: CharSequence? = null,
        onAction: (() -> Unit)? = null,
    ): LinearLayout {
        val card = card(context)
        card.addView(InkWidgets.caption(context, message))
        if (actionLabel != null && onAction != null) {
            card.addView(
                InkWidgets.button(context, actionLabel, onClick = onAction).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(WRAP_CONTENT, DesignRuntime.tokens.buttonHeight).apply {
                            topMargin = DesignRuntime.tokens.gap
                        }
                },
            )
        }
        return card
    }

    /** 可点击的文字操作，用于行内次要操作。 */
    fun actionText(
        context: Context,
        label: CharSequence,
        onClick: () -> Unit,
    ): TextView {
        val tokens = DesignRuntime.tokens
        return InkWidgets.body(context, label).apply {
            setPadding(tokens.gap, tokens.gap / 2, tokens.gap, tokens.gap / 2)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    /** 文件与书籍条目行：图标、名称、右侧说明与箭头。 */
    fun entryRow(
        context: Context,
        icon: InkGlyphs.Kind,
        title: CharSequence,
        detail: CharSequence?,
        onClick: (() -> Unit)? = null,
    ): LinearLayout {
        val tokens = DesignRuntime.tokens
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = tokens.listRowHeight
            setPadding(0, tokens.gap / 2, 0, tokens.gap / 2)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            addView(InkGlyphs.view(context, icon, tokens.listIcon))
            addView(
                InkWidgets.body(context, title).apply {
                    layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(tokens.gap, 0, 0, 0)
                },
            )
            if (detail != null) {
                addView(InkWidgets.caption(context, detail))
            }
            if (onClick != null) {
                addView(InkGlyphs.view(context, InkGlyphs.Kind.CHEVRON, tokens.settingsIcon))
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
        }
    }

    /** 封面缩略图：优先显示图片，缺失时以首字占位。 */
    fun coverView(
        context: Context,
        coverPath: String?,
        title: String,
        sidePx: Int,
    ): View {
        val tokens = DesignRuntime.tokens
        val frame =
            FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(sidePx, sidePx)
                background =
                    InkWidgets.background(
                        tokens.radiusSmall,
                        InkColors.PAPER,
                        tokens.stroke,
                        InkColors.INK_4,
                    )
                clipToOutline = true
            }
        val initial = title.trim().firstOrNull()?.toString() ?: "书"
        val placeholder =
            InkWidgets.textView(context, initial, tokens.textTitle, InkColors.INK_3).apply {
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            }
        frame.addView(placeholder)
        if (!coverPath.isNullOrBlank()) {
            ContentCoverLoader.bind(frame, coverPath, sidePx) { bitmap ->
                frame.removeAllViews()
                frame.addView(
                    ImageView(context).apply {
                        setImageBitmap(bitmap)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    },
                )
            }
        }
        return frame
    }

    /**
     * 统一书籍卡片：3:4 封面、最多两行书名、单行作者与阅读进度。
     *
     * 卡片以父容器权重二等分宽度，列间距取 tokens.gap；封面白底细黑框，
     * 图片按矩形中心裁剪，缺图时显示书名首字。书名使用正文级别字号，作者
     * 用浅色小字，进度用深色小字单独成行，形成标题、作者、进度的层级。
     * 本书页的两列网格使用本函数，书架页改用封面主导的独立卡片。
     */
    fun bookCard(
        context: Context,
        title: String,
        author: String,
        coverPath: String?,
        progress: Float,
        onClick: () -> Unit,
    ): LinearLayout {
        val tokens = DesignRuntime.tokens
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            layoutParams =
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                    marginEnd = tokens.gap
                }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            addView(BookCover(context).apply { bind(coverPath, title) })
            addView(
                InkWidgets.textView(context, title, tokens.textBody, InkColors.INK).apply {
                    minLines = 2
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, tokens.gap / 2, 0, 0)
                },
            )
            addView(
                InkWidgets.caption(context, author.ifBlank { "未知作者" }).apply {
                    setTextColor(InkColors.INK_3)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, tokens.gap / 4, 0, 0)
                },
            )
            addView(
                InkWidgets.textView(context, progressLabel(progress), tokens.textCaption, InkColors.INK).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, tokens.gap / 4, 0, 0)
                },
            )
        }
    }

    /** 阅读进度文案：0 表示尚未开始，其余按百分比取整。 */
    private fun progressLabel(progress: Float): String {
        if (progress < 0f) return "进度未知"
        val percent = (progress.coerceIn(0f, 1f) * 100f).toInt()
        return if (percent > 0) "已读 $percent%" else "未开始"
    }

    /**
     * 3:4 封面视图。
     *
     * 宽度取父容器按三列权重分配的单元格宽度，高度固定为宽度的四分之三，
     * 因此同一行封面等高；图片以中心裁剪填满矩形，缺图时显示白底细黑框
     * 与书名首字占位。
     */
    private class BookCover(
        context: Context,
    ) : FrameLayout(context) {
        /** 绑定序号，用于丢弃视图复用后到达的旧解码结果。 */
        private var bindToken = 0

        private val image =
            ImageView(context).apply {
                layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

        private val placeholder =
            TextView(context).apply {
                layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, DesignRuntime.tokens.textTitle)
                setTextColor(InkColors.INK_3)
                typeface = DesignRuntime.typeface()
            }

        init {
            val tokens = DesignRuntime.tokens
            background =
                InkWidgets.background(
                    tokens.radiusSmall,
                    InkColors.PAPER,
                    tokens.stroke,
                    InkColors.INK,
                )
            addView(image)
            addView(placeholder)
        }

        /** 绑定封面：先显示书名首字占位，后台解码完成后替换为图片。 */
        fun bind(
            coverPath: String?,
            title: String,
        ) {
            contentDescription = title
            val token = ++bindToken
            image.setImageDrawable(null)
            image.visibility = GONE
            placeholder.text = title.trim().take(1)
            placeholder.visibility = VISIBLE
            if (coverPath.isNullOrBlank()) return
            val sidePx = DesignRuntime.tokens.dp(160f)
            ContentCoverLoader.bind(this, coverPath, sidePx) { bitmap ->
                if (token == bindToken) {
                    image.setImageBitmap(bitmap)
                    image.visibility = VISIBLE
                    placeholder.visibility = GONE
                }
            }
        }

        /** 宽度取父容器分配值，高度固定为宽度的四分之三。 */
        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
            val height = width * 4 / 3
            image.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
            )
            placeholder.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST),
            )
            setMeasuredDimension(width, height)
        }
    }

    /** 进度条：外框加内部填充，比例限制在 0 到 1。 */
    fun progressBar(
        context: Context,
        progress: Float,
        widthPx: Int,
    ): View {
        val tokens = DesignRuntime.tokens
        val ratio = progress.coerceIn(0f, 1f)
        val track =
            FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(widthPx, tokens.progressHeight)
                background =
                    InkWidgets.background(
                        tokens.progressHeight / 2,
                        InkColors.PAPER,
                        tokens.stroke,
                        InkColors.INK_4,
                    )
            }
        track.addView(
            View(context).apply {
                background = InkWidgets.background(tokens.progressHeight / 2, InkColors.INK)
                layoutParams =
                    FrameLayout.LayoutParams(
                        (widthPx * ratio).toInt().coerceAtLeast(0),
                        tokens.progressHeight,
                    )
            },
        )
        return track
    }

    /** 分隔线，带上下留白。 */
    fun divider(container: LinearLayout) {
        val context = container.context
        val tokens = DesignRuntime.tokens
        val divider = InkWidgets.divider(context)
        divider.setPadding(0, tokens.gap / 2, 0, tokens.gap / 2)
        container.addView(divider)
    }

    /** 字节数格式化，使用 1024 进制。 */
    fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index += 1
        }
        return if (index == 0) {
            "${value.toLong()} ${units[index]}"
        } else {
            String.format(Locale.US, "%.1f %s", value, units[index])
        }
    }

    /** 容量格式化，按十亿字节为一 GB 显示。 */
    fun formatCapacity(bytes: Long): String {
        if (bytes <= 0L) return "0 GB"
        return String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
    }

    /** 阅读时长格式化。 */
    fun formatMinutes(minutes: Int): String {
        if (minutes <= 0) return "0 分钟"
        val hours = minutes / 60
        val rest = minutes % 60
        return if (hours > 0) "$hours 小时 $rest 分钟" else "$rest 分钟"
    }

    /** 时间戳格式化。 */
    fun formatDateTime(millis: Long): String {
        if (millis <= 0L) return "未知"
        return runCatching {
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
        }.getOrElse { "未知" }
    }

    /** 创建带边框的圆角背景，供自绘热力图使用。 */
    fun borderedBackground(
        radiusPx: Int,
        strokeWidthPx: Int,
        strokeColor: Int,
    ): GradientDrawable = InkWidgets.background(radiusPx, InkColors.PAPER, strokeWidthPx, strokeColor)
}
