package com.shuiyu.octoberrain.feature.files.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 文件页界面偏好。
 *
 * 只保存视图状态，不保存业务数据；偏好位于模块独立的 [FILE_NAME] 文件，
 * 不并入桌面配置，避免配置迁移与备份流程承担界面状态。
 *
 * 参数 context 为任意上下文，内部使用应用上下文访问偏好文件。
 */
class FilePrefs(
    context: Context,
) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** 是否使用网格视图，默认列表。 */
    var grid: Boolean
        get() = preferences.getBoolean(KEY_GRID, false)
        set(value) {
            preferences.edit().putBoolean(KEY_GRID, value).apply()
        }

    /** 是否显示隐藏文件，默认隐藏。 */
    var showHidden: Boolean
        get() = preferences.getBoolean(KEY_HIDDEN, false)
        set(value) {
            preferences.edit().putBoolean(KEY_HIDDEN, value).apply()
        }

    /** 排序依据，默认按名称。 */
    var sortKey: FileSortKey
        get() = FileSortKey.fromOrdinal(preferences.getInt(KEY_SORT_BY, FileSortKey.NAME.ordinal))
        set(value) {
            preferences.edit().putInt(KEY_SORT_BY, value.ordinal).apply()
        }

    /** 是否升序，默认升序。 */
    var sortAscending: Boolean
        get() = preferences.getBoolean(KEY_SORT_ASC, true)
        set(value) {
            preferences.edit().putBoolean(KEY_SORT_ASC, value).apply()
        }

    /** 上次浏览目录，用于下次进入时回到现场；空串表示未记录。 */
    var lastPath: String
        get() = preferences.getString(KEY_LAST_PATH, "").orEmpty()
        set(value) {
            preferences.edit().putString(KEY_LAST_PATH, value).apply()
        }

    companion object {
        /** 偏好文件名，模块内独立维护。 */
        const val FILE_NAME = "files_prefs"

        private const val KEY_GRID = "view_grid"
        private const val KEY_HIDDEN = "show_hidden"
        private const val KEY_SORT_BY = "sort_by"
        private const val KEY_SORT_ASC = "sort_asc"
        private const val KEY_LAST_PATH = "last_path"
    }
}
