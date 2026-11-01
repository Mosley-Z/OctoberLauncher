package com.shuiyu.octoberrain.core.common

/**
 * 桌面入口键的构造与解析。
 *
 * 应用入口键采用 `app:<包名>/<Activity 类名>`，用于区分同一包名下声明了多个
 * 启动 Activity 的应用；旧版本写入的 `app:<包名>` 视为包级入口键，只作为迁移输入，
 * 由数据层的组件键解析器在应用目录就绪后解析为组件键。
 *
 * 特殊入口键（`special:` 前缀）与文件夹键（`folder:` 前缀）沿用原格式，不受本对象影响。
 */
object EntryKeys {
    /** 应用入口键的前缀。 */
    const val APP_PREFIX: String = "app:"

    /** 特殊入口键的前缀。 */
    const val SPECIAL_PREFIX: String = "special:"

    /** 文件夹入口键的前缀。 */
    const val FOLDER_PREFIX: String = "folder:"

    /**
     * 构造应用入口的组件键。
     *
     * 参数 packageName 为包名；参数 activityName 为启动 Activity 的完整类名。
     * 返回值形如 `app:com.example.app/com.example.app.MainActivity`。
     */
    fun appKey(
        packageName: String,
        activityName: String,
    ): String = APP_PREFIX + packageName + "/" + activityName

    /**
     * 判断入口键是否为组件键。
     *
     * 参数 key 为入口键；返回值为真表示形如 `app:<包名>/<Activity>`，
     * 包级旧键 `app:<包名>`、特殊入口与文件夹键均返回假。
     */
    fun isComponentKey(key: String): Boolean = key.startsWith(APP_PREFIX) && key.removePrefix(APP_PREFIX).contains('/')

    /**
     * 读取包级旧键记录的包名。
     *
     * 参数 key 为入口键；键形如 `app:<包名>` 且不含斜线时返回包名，
     * 组件键、特殊入口键与文件夹键返回空。
     */
    fun legacyPackageOf(key: String): String? {
        if (!key.startsWith(APP_PREFIX) || isComponentKey(key)) return null
        return key.removePrefix(APP_PREFIX).takeIf { it.isNotBlank() }
    }

    /**
     * 读取应用入口键记录的包名。
     *
     * 参数 key 为组件键或包级旧键；返回值为斜线之前的包名，特殊入口键与文件夹键返回空。
     */
    fun packageOf(key: String): String? {
        if (!key.startsWith(APP_PREFIX)) return null
        val body = key.removePrefix(APP_PREFIX)
        return body.substringBefore('/').takeIf { it.isNotBlank() }
    }

    /**
     * 读取组件键记录的 Activity 类名。
     *
     * 参数 key 为组件键；返回值为斜线之后的部分，包级旧键、特殊入口键与文件夹键返回空。
     */
    fun activityOf(key: String): String? {
        if (!isComponentKey(key)) return null
        return key.removePrefix(APP_PREFIX).substringAfter('/').takeIf { it.isNotBlank() }
    }

    /** 判断入口键是否属于应用入口（组件键或包级旧键）。 */
    fun isAppKey(key: String): Boolean = packageOf(key) != null

    /**
     * 构造旧包级入口键。
     *
     * 参数 packageName 为包名。该形式只用于读取迁移期尚未解析的历史配置，
     * 新写入的入口键使用 [appKey] 的组件形式。
     */
    fun packageKey(packageName: String): String = APP_PREFIX + packageName

    /**
     * 读取应用目录条目的入口键。
     *
     * 参数 app 为应用目录条目；存在启动 Activity 时返回组件键，
     * 缺少 Activity 信息时退回包级旧键，避免条目丢失。
     */
    fun appEntryKey(app: InstalledApp): String =
        app.activityName
            ?.takeIf { it.isNotBlank() }
            ?.let { appKey(app.packageName, it) }
            ?: packageKey(app.packageName)
}
