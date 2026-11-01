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
     * 构造组件级图标目标标识。
     *
     * 参数 packageName 为包名；参数 activityName 为启动 Activity 完整类名。
     * 返回值形如 `com.example.app/com.example.app.MainActivity`，与入口键去掉
     * `app:` 前缀后的形式一致，因此 [normalizeStoredKey] 对组件入口键可直接复用。
     */
    fun componentTarget(
        packageName: String,
        activityName: String,
    ): String = "$packageName/$activityName"

    /** 组件级目标的解析结果。 */
    data class ComponentTarget(
        /** 包名。 */
        val packageName: String,
        /** 启动 Activity 完整类名。 */
        val activityName: String,
    )

    /**
     * 解析组件级图标目标。
     *
     * 参数 targetId 为归一后的目标标识；形如 `包名/Activity` 时返回包名与类名，
     * 包名与 `special:` 标识返回空。调用方据此区分按包名、按组件与按特殊入口取图标。
     */
    fun parseComponentTarget(targetId: String): ComponentTarget? {
        val separator = if (targetId.startsWith(SPECIAL_PREFIX)) -1 else targetId.indexOf('/')
        if (separator <= 0 || separator == targetId.lastIndex) return null
        return ComponentTarget(
            packageName = targetId.substring(0, separator),
            activityName = targetId.substring(separator + 1),
        )
    }

    /**
     * 归一已保存的图标覆盖键。
     *
     * 参数 storedKey 为历史版本写入的键：可能带 `app:`、`special:` 前缀或为裸包名。
     * 返回值统一为包名、组件目标或 `special:` 标识；无法识别的特殊名称保持原样。
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
            SpecialEntry.FILES -> "文件"
            SpecialEntry.BOOKSHELF -> "书架"
            SpecialEntry.SETTINGS -> "十雨设置"
            SpecialEntry.SEARCH -> "搜索"
        }

    /** 把内置特殊入口转换为入口名。 */
    private fun nameOf(entry: SpecialEntry): String =
        when (entry) {
            SpecialEntry.WEREAD -> "weread"
            SpecialEntry.FILES -> "files"
            SpecialEntry.BOOKSHELF -> "bookshelf"
            SpecialEntry.SETTINGS -> "settings"
            SpecialEntry.SEARCH -> "search"
        }

    /** 把入口名解析为内置特殊入口。 */
    private fun entryOf(name: String): SpecialEntry? =
        when (name) {
            "weread" -> SpecialEntry.WEREAD
            "files" -> SpecialEntry.FILES
            "bookshelf" -> SpecialEntry.BOOKSHELF
            "settings" -> SpecialEntry.SETTINGS
            "search" -> SpecialEntry.SEARCH
            else -> null
        }
}

/**
 * 桌面入口的图标目标标识。
 *
 * 应用入口优先使用组件目标（包名与 Activity 类名），缺少 Activity 信息时退回包名；
 * 特殊入口为 `special:` 名称。包级旧键因此仍命中历史图标覆盖。
 */
val LauncherEntry.iconTargetId: String
    get() =
        special?.let(IconTargets::specialKey)
            ?: run {
                val packageName = packageName ?: key.removePrefix("app:").substringBefore('/')
                activityName
                    ?.takeIf { it.isNotBlank() && packageName.isNotBlank() }
                    ?.let { IconTargets.componentTarget(packageName, it) }
                    ?: packageName
            }
