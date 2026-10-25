package com.shuiyu.octoberrain.feature.wereadapp.ui

import android.app.Dialog
import android.content.Context
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.Window
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.design.InkWidgets

/** 离线使用说明：目录与章节共用原生纸白页面，不加载WebView或网络。 */
internal class LauncherHelpDialog(
    context: Context,
) : Dialog(context) {
    private val chapters =
        context.assets.open(ASSET).bufferedReader().use { reader ->
            reader.readText().split(Regex("^## ", RegexOption.MULTILINE)).drop(1).map { section ->
                Chapter(section.substringBefore('\n').trim(), section.substringAfter('\n').trim())
            }
        }
    private var chapterOpen = false

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setCanceledOnTouchOutside(false)
        setOnKeyListener { _, key, event ->
            val toContents = key == KeyEvent.KEYCODE_BACK && chapterOpen
            if (toContents && event.action == KeyEvent.ACTION_UP) showContents()
            toContents
        }
        showContents()
    }

    override fun show() {
        super.show()
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window?.setWindowAnimations(0)
    }

    private fun showContents() {
        chapterOpen = false
        val page = InkPage(context, "十雨桌面使用说明", onBack = { dismiss() })
        page.content.addView(InkWidgets.body(context, "离线说明 · 点击章节阅读，章节内上下滚动；返回先回目录。"))
        chapters.forEach { chapter ->
            page.content.addView(InkWidgets.spacer(context, DesignRuntime.tokens.gap))
            page.content.addView(InkWidgets.button(context, chapter.title, false) { showChapter(chapter) })
        }
        setContentView(page)
    }

    private fun showChapter(chapter: Chapter) {
        chapterOpen = true
        val page = InkPage(context, chapter.title, onBack = { showContents() })
        chapter.body.split(Regex("""\n\s*\n""")).forEach { paragraph ->
            page.content.addView(InkWidgets.body(context, paragraph).apply { setTextIsSelectable(true) })
            page.content.addView(InkWidgets.spacer(context, DesignRuntime.tokens.gapLarge))
        }
        page.content.addView(InkWidgets.button(context, "返回目录", false) { showContents() })
        setContentView(page)
    }

    private data class Chapter(
        val title: String,
        val body: String,
    )

    private companion object {
        const val ASSET = "help/launcher-guide.md"
    }
}
