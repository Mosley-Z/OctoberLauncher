package com.shuiyu.octoberrain.feature.content.ui.negative

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * 阅读指纹分区默认选书的读取器。
 *
 * 指纹分区要求宿主通过 `menuPrefs("cfg", "fp_books", …)` 给出书目标识列表，
 * 原版由用户在墨台里逐本勾选后落到偏好。独立同步场景下用户尚未选书，
 * 本对象从已同步的笔记索引里取前若干本有笔记的书目作为默认值，
 * 使分区首次进入即有内容，用户仍可在「选书」对话框里改选。
 */
internal object WereadFingerprintBooks {
    /** 微读数据目录名，与 [WereadDeskHostImpl] 使用的目录一致。 */
    private const val WEREAD_DIRECTORY = "weread"

    /** 笔记索引文件名，与内核读取的索引一致。 */
    private const val NOTE_INDEX = "nt_index.json"

    /** 默认选书上限，与原版墨台一次可选的目数量级一致。 */
    private const val DEFAULT_LIMIT = 6

    /**
     * 读取默认选书列表。
     *
     * @param context 应用上下文，用于定位微读数据目录
     * @return 逗号分隔的书目标识，索引缺失或不可解析时返回空串
     */
    fun read(context: Context): String = ids(context).joinToString(",")

    /**
     * 读取笔记索引里的书目标识。
     *
     * @param context 应用上下文
     * @return 至多 [DEFAULT_LIMIT] 个标识，失败时为空表
     */
    private fun ids(context: Context): List<String> {
        val file = File(File(context.filesDir, WEREAD_DIRECTORY), NOTE_INDEX)
        val text = runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull().orEmpty()
        return runCatching {
            JSONObject(text).optJSONArray("books")?.let { books ->
                buildList {
                    for (index in 0 until books.length()) {
                        val bookId = books.optJSONObject(index)?.optString("bookId").orEmpty()
                        if (bookId.isNotBlank()) add(bookId)
                        if (size >= DEFAULT_LIMIT) break
                    }
                }
            }
        }.getOrNull().orEmpty()
    }
}
