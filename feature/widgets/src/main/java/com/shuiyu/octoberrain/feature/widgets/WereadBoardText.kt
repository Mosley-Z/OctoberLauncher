package com.shuiyu.octoberrain.feature.widgets

import android.graphics.Paint

/** 笔记正文截断时使用的省略号。 */
private const val NOTE_ELLIPSIS: String = "…"

/**
 * 把笔记正文按宽度折行并限制行数，超出部分在末行加省略号。
 *
 * 折行宽度由当前画笔字号决定，调用方须先设置好字号与字体。
 *
 * @param text 正文，空串返回空列表
 * @param paint 已设置字号的画笔
 * @param maxWidth 折行宽度，单位为像素
 * @param maxLines 最多保留的行数
 * @return 处理后的行列表
 */
internal fun clipNoteLines(
    text: String,
    paint: Paint,
    maxWidth: Float,
    maxLines: Int,
): List<String> {
    val wrapped = InkDraw.wrap(text, paint, maxWidth, maxLines + 1)
    return limitNoteLines(wrapped, maxLines) { line -> InkDraw.ellipsize("$line$NOTE_ELLIPSIS", paint, maxWidth) }
}

/**
 * 把折行结果限制在指定行数内。
 *
 * 行数未超限时原样返回；超限时只保留前若干行，并把末行交给调用方追加省略号，
 * 由此控制笔记占用的高度，字号不随文本长度变化。
 *
 * @param wrapped 已折行的文本
 * @param maxLines 最多保留的行数
 * @param ellipsize 末行处理函数，负责在行尾追加省略号并按宽度截断
 * @return 行数受限的文本行
 */
internal inline fun limitNoteLines(
    wrapped: List<String>,
    maxLines: Int,
    ellipsize: (String) -> String,
): List<String> =
    when {
        wrapped.size <= maxLines -> wrapped
        wrapped.isEmpty() -> wrapped
        else ->
            wrapped.take(maxLines).toMutableList().also { kept ->
                kept[kept.lastIndex] = ellipsize(kept.last())
            }
    }
