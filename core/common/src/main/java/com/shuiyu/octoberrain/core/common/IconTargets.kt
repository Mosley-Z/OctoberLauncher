package com.shuiyu.octoberrain.core.common

/**
 * 图标目标的统一标识与名称解析。
 *
 * 图标覆盖按目标标识保存：应用使用包名，特殊入口使用 `special:` 前缀加入口名。
 * 桌面入口键（`app:` 与 `special:` 前缀）只用于排序与持久化，图标层统一使用
 * 本对象归一后的标识。
 */
object IconTargets {
    /** 特殊入口目标标识的前缀。 */
    const val SPECIAL_PREFIX: String = "special:"

    /** 应用入口键的前缀。 */
    private const val APP_PREFIX: String = "app:"

    /**
     * 把内置特殊入口转换为目标标识。
     *
     * 参数 entry 为内置特殊入口；返回值形如 `special:weread`。
     */
    fun specialKey(entry: SpecialEntry): String = SPECIAL_PREFIX + nameOf(entry)

    /**
     * 把目标标识解析为内置特殊入口。
     *
     * 参数 key 为目标标识或入口键；返回值无法识别时为空。
     */
    fun specialEntryOf(key: String): SpecialEntry? = entryOf(key.removePrefix(SPECIAL_PREFIX).lowercase())

    /**
     * 把目标标识转换为桌面入口键。
     *
     * 参数 targetId 为包名或 `special:` 标识；应用补 `app:` 前缀，特殊入口原样返回。
     */
    fun entryKeyOf(targetId: String): String =
        if (targetId.startsWith(SPECIAL_PREFIX)) targetId else APP_PREFIX + targetId

    /**
     * 归一已保存的图标覆盖键。
     *
     * 参数 storedKey 为历史版本写入的键：可能带 `app:`、`special:` 前缀或为裸包名。
     * 返回值统一为包名或 `special:` 标识；无法识别的特殊名称保持原样。
     */
    fun normalizeStoredKey(storedKey: String): String {
        if (storedKey.startsWith(SPECIAL_PREFIX)) {
            return specialEntryOf(storedKey)?.let(::specialKey) ?: storedKey
        }
        return storedKey.removePrefix(APP_PREFIX)
    }

    /**
     * 读取目标标识的展示名。
     *
     * 参数 targetId 为包名或 `special:` 标识；[appLabel] 为应用包名到展示名的解析。
     * 特殊入口返回内置中文名，其余委托 [appLabel] 处理。
     */
    fun labelOf(
        targetId: String,
        appLabel: (String) -> String,
    ): String = specialEntryOf(targetId)?.let(::specialLabel) ?: appLabel(targetId)

    /**
     * 读取内置特殊入口的展示名。
     *
     * 参数 entry 为内置特殊入口；返回值与桌面入口的默认名称一致。
     */
    fun specialLabel(entry: SpecialEntry): String =
        when (entry) {
            SpecialEntry.WEREAD -> "微信读书"
            SpecialEntry.KOREADER -> "KOReader"
            SpecialEntry.FILES -> "文件"
            SpecialEntry.BOOKSHELF -> "书架"
            SpecialEntry.SETTINGS -> "设置"
            SpecialEntry.SEARCH -> "搜索"
        }

    /** 把内置特殊入口转换为入口名。 */
    private fun nameOf(entry: SpecialEntry): String =
        when (entry) {
            SpecialEntry.WEREAD -> "weread"
            SpecialEntry.KOREADER -> "koreader"
            SpecialEntry.FILES -> "files"
            SpecialEntry.BOOKSHELF -> "bookshelf"
            SpecialEntry.SETTINGS -> "settings"
            SpecialEntry.SEARCH -> "search"
        }

    /** 把入口名解析为内置特殊入口。 */
    private fun entryOf(name: String): SpecialEntry? =
        when (name) {
            "weread" -> SpecialEntry.WEREAD
            "koreader" -> SpecialEntry.KOREADER
            "files" -> SpecialEntry.FILES
            "bookshelf" -> SpecialEntry.BOOKSHELF
            "settings" -> SpecialEntry.SETTINGS
            "search" -> SpecialEntry.SEARCH
            else -> null
        }
}

/**
 * 桌面入口的图标目标标识：应用为包名，特殊入口为 `special:` 名称。
 */
val LauncherEntry.iconTargetId: String
    get() = special?.let(IconTargets::specialKey) ?: (packageName ?: key.removePrefix("app:"))
