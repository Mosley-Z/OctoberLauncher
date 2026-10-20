package com.shuiyu.octoberrain.core.data

import android.content.Context

/**
 * 配置持久化接口。
 *
 * 只负责按字符串读写整份配置，序列化与状态发布由 [JsonConfigStore] 承担。
 * 接口抽离后，写盘失败与迁移回写分支可以在纯 JVM 测试中通过内存实现注入。
 */
interface ConfigPersistence {
    /**
     * 读取整份配置字符串。
     *
     * @return 磁盘上保存的 JSON 字符串；尚未写入时返回 null。
     */
    fun read(): String?

    /**
     * 写入整份配置字符串。
     *
     * 返回布尔值而非抛出异常，便于调用方在同一位置区分成功与失败。
     *
     * @param value 待写入的 JSON 字符串。
     * @return 确认写入成功返回 true，否则返回 false。
     */
    fun write(value: String): Boolean
}

/**
 * 基于 SharedPreferences 的配置持久化实现。
 *
 * 使用 commit 同步写入并读取返回值，使写入结果可以被调用方观察；配置体积为数十 KB，
 * 写入频率由设置操作决定，同步写入的耗时可接受。apply 只能在后台异步落盘，
 * 无法把失败反馈给调用方，故不采用。
 */
class SharedPreferencesConfigPersistence(
    context: Context,
) : ConfigPersistence {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(): String? = preferences.getString(KEY_CONFIG, null)

    override fun write(value: String): Boolean =
        preferences
            .edit()
            .putString(KEY_CONFIG, value)
            .commit()

    private companion object {
        const val PREFS_NAME = "octoberrain_config"
        const val KEY_CONFIG = "launcher_config"
    }
}
