package com.shuiyu.octoberrain.feature.content.ui.negative

import android.content.Context
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.WereadDeskConfig
import com.shuiyu.octoberrain.core.data.WereadNoteLibrary
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.json.JSONObject
import java.io.File

/** 指纹书目偏好：只有确认选择才写入，空集合表示用户尚未选择或已清空。 */
internal object WereadFingerprintBooks {
    /** 原版选书使用笔记书目索引，避免把未阅读的整份书架当作指纹候选。 */
    fun candidates(context: Context): List<Array<String>> = candidates(File(context.filesDir, "weread"))

    internal fun candidates(root: File): List<Array<String>> =
        WereadNoteLibrary(root)
            .books()
            .mapNotNull { entry ->
                val book = entry["book"] as? JsonObject ?: entry
                val id = text(entry, "bookId").ifBlank { text(book, "bookId") }
                if (id.isBlank()) {
                    null
                } else {
                    val title = text(book, "title").ifBlank { id }
                    val ready = File(root, "nt_$id.json").isFile
                    arrayOf(id, title + if (ready) "" else "（笔记未同步）", text(book, "author"))
                }
            }.distinctBy { it[0] }

    private fun text(
        entry: JsonObject,
        key: String,
    ): String = (entry[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    /** 向内核提供按用户顺序排列的多个书目标识。 */
    fun read(context: Context): String = ids(context).joinToString(",")

    /** 重建对话框和内核时恢复保存的集合。 */
    fun ids(context: Context): List<String> {
        val config =
            runCatching { AppServices.config.state.value.integrations.wereadDesk }
                .getOrDefault(WereadDeskConfig())
        if (config.fpBooksConfigured || config.fpBookIds.isNotEmpty()) return config.effectiveFpBookIds
        val legacy =
            runCatching {
                val root = JSONObject(File(context.filesDir, "weread/kernel_prefs.json").readText())
                root.optJSONObject("string")?.optString("fp_books").orEmpty()
            }.getOrDefault("")
        return WereadDeskConfig(fpBookIds = legacy.split(',')).effectiveFpBookIds
    }

    /** 确认后保存，清空同样是有效选择，不回填索引前几本。 */
    fun save(ids: List<String>) {
        val selected = WereadDeskConfig(fpBookIds = ids).effectiveFpBookIds
        AppServices.config.edit { config ->
            config.copy(
                integrations =
                    config.integrations.copy(
                        wereadDesk =
                            config.integrations.wereadDesk.copy(
                                fpBookIds = selected,
                                fpBooksConfigured = true,
                            ),
                    ),
            )
        }
    }
}
