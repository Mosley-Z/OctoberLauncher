package com.shuiyu.octoberrain.core.data

import kotlinx.serialization.json.JsonObject
import java.io.File

/** 自动档从完整书架的最近阅读时间选择，不能沿用可能被手选覆盖的 book.json。 */
object WereadBookSelection {
    fun automaticBookId(root: File): String {
        val books = readObject(File(root, "shelf.json")).array("books").mapNotNull { it as? JsonObject }
        return books.maxByOrNull { it.number("readUpdateTime") }?.text("bookId").orEmpty()
    }
}
