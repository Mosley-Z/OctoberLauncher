package com.shuiyu.octoberrain.feature.reader.text

import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan

/** 墨水屏高亮底色：不透明浅灰，避免彩色底纹在灰度面板上呈现为深色噪点。 */
internal const val TEXT_HIGHLIGHT_COLOR = 0xFFD8D8D8.toInt()

/**
 * 为当前页文本应用高亮区间。
 *
 * 返回的 [CharSequence] 只覆盖当前页可见文本，区间越界时截断、空区间跳过；
 * 不构造整文件字符序列，也不保留跨页 span。
 */
internal fun highlightRanges(
    text: String,
    ranges: List<TextRange>,
): CharSequence {
    if (ranges.isEmpty()) return text
    val spanned = SpannableString(text)
    for (range in ranges) {
        val start = range.start.coerceIn(0, text.length)
        val end = range.end.coerceIn(start, text.length)
        if (end > start) {
            spanned.setSpan(
                BackgroundColorSpan(TEXT_HIGHLIGHT_COLOR),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }
    return spanned
}
