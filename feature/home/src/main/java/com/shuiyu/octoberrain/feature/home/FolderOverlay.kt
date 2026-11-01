package com.shuiyu.octoberrain.feature.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.shuiyu.octoberrain.core.common.DesktopFolder
import com.shuiyu.octoberrain.core.common.LauncherEntry
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkWidgets

/** Dialog 的原生内容；窗口、返回与外部点击由 HomeView 的 Dialog 管理。 */
internal class FolderOverlay(
    context: Context,
) : FrameLayout(context) {
    var onMemberLongPress: ((LauncherEntry) -> Unit)? = null
    var onLaunch: ((LauncherEntry) -> Unit)? = null
    var onAddRequested: ((String) -> Unit)? = null
    var onDismissRequested: (() -> Unit)? = null
    var drawIcon: ((Canvas, RectF, LauncherEntry) -> Unit)? = null

    private val tokens get() = DesignRuntime.tokens
    private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val title = InkWidgets.heading(context, "")
    private val scroll = ScrollView(context)
    private val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var boundFolder: DesktopFolder? = null
    private var boundMembers: List<LauncherEntry>? = null
    private var rename: ((String) -> Boolean)? = null
    private var dissolve: (() -> Unit)? = null
    private var unavailableKeys: Set<String> = emptySet()

    init {
        background = InkWidgets.background(tokens.radius, InkColors.PAPER, tokens.stroke, InkColors.INK)
        body.setPadding(tokens.dialogPadding, tokens.dialogPadding, tokens.dialogPadding, tokens.dialogPadding)
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        title.maxLines = 2
        title.ellipsize = TextUtils.TruncateAt.END
        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(
            InkWidgets.button(context, "添加应用") {
                boundFolder?.id?.let { onAddRequested?.invoke(it) }
            },
        )
        header.addView(InkWidgets.button(context, "关闭") { onDismissRequested?.invoke() })
        body.addView(header)
        val actions = LinearLayout(context)
        actions.addView(
            InkWidgets.button(context, "改名") {
                val folder = boundFolder ?: return@button
                InkDialogs.input(
                    context,
                    "改名",
                    initial = folder.name,
                    requestIme = true,
                    submit = { name -> rename?.invoke(name.trim()) ?: false },
                )
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        actions.addView(
            InkWidgets.button(context, "解散") { dissolve?.invoke() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        body.addView(actions)
        scroll.addView(grid)
        body.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun bind(
        folder: DesktopFolder?,
        members: List<LauncherEntry>,
        onRename: (String) -> Boolean,
        onDissolve: () -> Unit,
        unavailableKeys: Set<String>,
    ) {
        if (folder == null) {
            onDismissRequested?.invoke()
            return
        }
        boundFolder = folder
        rename = onRename
        dissolve = onDissolve
        title.text = folder.name.ifBlank { "文件夹" }
        val unchanged = boundMembers == members && this.unavailableKeys == unavailableKeys
        this.unavailableKeys = unavailableKeys
        if (unchanged) {
            invalidateIcons()
            return
        }
        boundMembers = members
        rebuildGrid(members)
    }

    fun invalidateIcons() {
        grid.invalidate()
        for (rowIndex in 0 until grid.childCount) {
            val row = grid.getChildAt(rowIndex) as? LinearLayout ?: continue
            for (index in 0 until row.childCount) {
                val cell = row.getChildAt(index) as? LinearLayout ?: continue
                cell.getChildAt(0)?.invalidate()
            }
        }
    }

    private fun rebuildGrid(members: List<LauncherEntry>) {
        val previousScroll = scroll.scrollY
        grid.removeAllViews()
        if (members.isEmpty()) {
            grid.addView(
                InkWidgets.body(context, "暂无应用").apply {
                    gravity = Gravity.CENTER
                    setPadding(0, tokens.gapLarge, 0, tokens.gapLarge)
                },
            )
            return
        }
        val available = minOf(resources.displayMetrics.widthPixels - tokens.dp(32f), tokens.dp(420f))
        val cellWidth = maxOf(tokens.dp(88f), (tokens.textBody * 4).toInt())
        val columns = ((available - tokens.dialogPadding * 2) / cellWidth).coerceIn(1, 4)
        members.chunked(columns).forEach { group ->
            val row = LinearLayout(context)
            group.forEach { member ->
                row.addView(
                    memberCell(member),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
            }
            repeat(columns - group.size) {
                row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            }
            grid.addView(row)
        }
        scroll.post { scroll.scrollTo(0, previousScroll) }
    }

    private fun memberCell(member: LauncherEntry): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(tokens.gap, tokens.gap, tokens.gap, tokens.gap)
            val icon =
                object : View(context) {
                    override fun onDraw(canvas: Canvas) {
                        super.onDraw(canvas)
                        drawIcon?.invoke(canvas, RectF(0f, 0f, width.toFloat(), height.toFloat()), member)
                    }
                }
            addView(icon, LinearLayout.LayoutParams(tokens.dp(48f), tokens.dp(48f)))
            addView(
                InkWidgets.body(context, member.label).apply {
                    gravity = Gravity.CENTER
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, tokens.gap, 0, 0)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            contentDescription = member.label
            isClickable = true
            isFocusable = true
            setOnClickListener {
                if (member.key !in unavailableKeys) onLaunch?.invoke(member)
            }
            setOnLongClickListener {
                onMemberLongPress?.invoke(member)
                true
            }
        }
}
