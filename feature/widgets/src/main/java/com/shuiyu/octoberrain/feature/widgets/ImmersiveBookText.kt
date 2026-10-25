package com.shuiyu.octoberrain.feature.widgets

/** Display-only title cleanup; the source book title remains unchanged. */
internal fun immersiveWorkTitle(title: String): String {
    val group = Regex("""（[^（）]*）|\([^()]*\)|【[^【】]*】|\[[^\[\]]*\]""")
    val edition = Regex("套装|全[一二三四五六七八九十0-9]+册|文集|全集|修订版|纪念版|珍藏版|插图版")
    val short =
        group
            .replace(title) { if (edition.containsMatchIn(it.value)) "" else it.value }
            .trim()
            .trim('《', '》')
            .trim()
    return short.ifBlank { title.trim() }
}

internal fun immersiveReadingDuration(seconds: Int): String {
    if (seconds <= 0) return "—"
    val minutes = seconds / 60
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}
