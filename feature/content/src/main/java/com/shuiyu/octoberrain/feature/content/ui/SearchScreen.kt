package com.shuiyu.octoberrain.feature.content.ui

import android.content.Context
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.BookItem
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkGlyphs
import com.shuiyu.octoberrain.core.design.InkWidgets
import java.io.File

private val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
private val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * 书架搜索页面。
 *
 * 顶部为关键词输入框，支持键盘搜索键与按钮触发；结果按封面、书名、作者与
 * 格式展示，点击用系统已安装的阅读应用打开。
 */
class SearchScreen : ContentScreen("搜索") {
    private companion object {
        private const val MAX_RESULTS = 30
    }

    private var input: EditText? = null
    private var resultsContainer: LinearLayout? = null
    private var query: String = ""

    override fun createView(context: Context): View {
        val view = super.createView(context)
        buildUi()
        return view
    }

    override fun onResume() {
        if (input == null) return
        if (query.isBlank()) {
            renderHint()
        } else {
            performSearch(query)
        }
    }

    /** 重建字号和尺寸，同时保留尚未提交的搜索文字与光标。 */
    override fun onDimensionsChanged() {
        val draft = input?.text?.toString().orEmpty()
        val cursor = input?.selectionStart ?: draft.length
        buildUi()
        onResume()
        input?.setText(draft)
        input?.setSelection(cursor.coerceIn(0, draft.length))
    }

    /** 构建固定输入区与结果容器。 */
    private fun buildUi() {
        val tokens = tokens
        val container = clearContent()

        val searchCard = ContentUi.card(context)
        val edit =
            EditText(context).apply {
                hint = "书名或作者"
                inputType = InputType.TYPE_CLASS_TEXT
                setSingleLine(true)
                setTextColor(InkColors.INK)
                setHintTextColor(InkColors.INK_3)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, tokens.textBody)
                typeface = DesignRuntime.typeface()
                background =
                    InkWidgets.background(
                        tokens.radiusSmall,
                        InkColors.PAPER,
                        tokens.stroke,
                        InkColors.INK_4,
                    )
                setPadding(tokens.gap, tokens.gap / 2, tokens.gap, tokens.gap / 2)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, tokens.listRowHeight)
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        triggerSearch()
                        true
                    } else {
                        false
                    }
                }
            }
        searchCard.addView(edit)
        val actions =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams =
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                        topMargin = tokens.gap
                    }
            }
        actions.addView(
            InkWidgets.button(context, "搜索", onClick = { triggerSearch() }).apply {
                layoutParams = LinearLayout.LayoutParams(0, tokens.buttonHeight, 1f)
            },
        )
        actions.addView(
            InkWidgets.button(context, "清空", onClick = { clearSearch() }).apply {
                layoutParams =
                    LinearLayout.LayoutParams(0, tokens.buttonHeight, 1f).apply {
                        marginStart = tokens.gap
                    }
            },
        )
        searchCard.addView(actions)
        container.addView(searchCard)

        val results =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            }
        container.addView(results)
        input = edit
        resultsContainer = results
        renderHint()
    }

    /** 触发搜索。 */
    private fun triggerSearch() {
        val keyword =
            input
                ?.text
                ?.toString()
                ?.trim()
                .orEmpty()
        if (keyword.isEmpty()) {
            toast("请输入书名或作者关键词")
            return
        }
        query = keyword
        hideKeyboard()
        performSearch(keyword)
    }

    /** 清空关键词与结果。 */
    private fun clearSearch() {
        input?.setText("")
        query = ""
        hideKeyboard()
        renderHint()
    }

    /** 执行检索并填充结果。 */
    private fun performSearch(keyword: String) {
        val results = resultsContainer ?: return
        val source = source()
        if (source == null) {
            results.removeAllViews()
            results.addView(
                ContentUi.emptyCard(
                    context = context,
                    title = "搜索尚未就绪",
                    message = "桌面服务尚未完成初始化，请稍后重新进入。",
                ),
            )
            return
        }
        runTask {
            results.removeAllViews()
            val loading = ContentUi.loading(results)
            try {
                val books = source.searchBooks(keyword).take(MAX_RESULTS)
                results.removeAllViews()
                if (books.isEmpty()) {
                    results.addView(
                        ContentUi.emptyCard(
                            context = context,
                            title = "没有匹配的书籍",
                            message = "关键词「$keyword」在书架中没有结果，可更换关键词或确认书籍已放入书籍目录。",
                        ),
                    )
                    return@runTask
                }
                ContentUi.sectionTitle(results, "找到 ${books.size} 本")
                val card = ContentUi.card(context)
                books.forEachIndexed { index, book ->
                    if (index > 0) card.addView(InkWidgets.divider(context))
                    card.addView(resultRow(book))
                }
                results.addView(card)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Throwable) {
                // 检索失败时用错误卡片替换加载文案，页面不保留半成品内容
                results.removeAllViews()
                results.addView(
                    ContentUi.emptyCard(
                        context = context,
                        title = "检索失败",
                        message = "读取书架时出错：${error.message ?: "未知错误"}",
                        actionLabel = "重试",
                        onAction = { performSearch(keyword) },
                    ),
                )
            } finally {
                ContentUi.removeLoading(loading)
            }
        }
    }

    /** 单条检索结果。 */
    private fun resultRow(book: BookItem): View {
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = tokens.listRowHeight
                setPadding(0, tokens.gap / 2, 0, tokens.gap / 2)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                isClickable = true
                isFocusable = true
                setOnClickListener { openFile(File(book.path)) }
            }
        row.addView(ContentUi.coverView(context, book.coverPath, book.title, tokens.dp(44f)))
        val column =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                        marginStart = tokens.gap
                    }
            }
        column.addView(
            InkWidgets.body(context, book.title).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            },
        )
        val author = book.author.ifBlank { "未知作者" }
        column.addView(
            InkWidgets.caption(
                context,
                "$author · ${book.format.uppercase()} · ${ContentUi.formatSize(book.sizeBytes)}",
            ),
        )
        row.addView(column)
        row.addView(InkGlyphs.view(context, InkGlyphs.Kind.CHEVRON, tokens.settingsIcon))
        return row
    }

    /** 初始提示。 */
    private fun renderHint() {
        val results = resultsContainer ?: return
        results.removeAllViews()
        results.addView(
            ContentUi.emptyCard(
                context = context,
                title = "搜索书架",
                message = "输入书名或作者关键词后按键盘搜索键，结果按最近修改排序。",
            ),
        )
    }

    /** 收起软键盘。 */
    private fun hideKeyboard() {
        val current = input ?: return
        runCatching {
            val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            manager?.hideSoftInputFromWindow(current.windowToken, 0)
            current.clearFocus()
        }
    }
}
