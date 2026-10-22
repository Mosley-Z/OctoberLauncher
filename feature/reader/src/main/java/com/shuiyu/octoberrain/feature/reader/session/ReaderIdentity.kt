package com.shuiyu.octoberrain.feature.reader.session

import android.content.ContentValues
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import com.shuiyu.octoberrain.core.common.DocumentMetadata

/** 已确认同源的本应用URI合入规范路径，保留较新进度和所有书签；不按文件名猜测。 */
internal fun ReadingRepository.mergeAliases(metadata: DocumentMetadata) {
    val aliases = metadata.aliases.filter { it != metadata.id }.distinct()
    if (aliases.isEmpty()) return
    val db = writableDatabase
    db.beginTransaction()
    try {
        val ids = listOf(metadata.id) + aliases
        val placeholders = ids.joinToString(",") { "?" }
        db
            .query(
                "documents",
                null,
                "id IN ($placeholders)",
                ids.toTypedArray(),
                null,
                null,
                "updated DESC",
                "1",
            ).use { row ->
                if (row.moveToFirst() && row.getString(row.getColumnIndexOrThrow("id")) != metadata.id) {
                    val values = ContentValues()
                    DatabaseUtils.cursorRowToContentValues(row, values)
                    values.put("id", metadata.id)
                    check(db.insertWithOnConflict("documents", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
                }
            }
        val bookmark = ContentValues().apply { put("document", metadata.id) }
        aliases.forEach { id ->
            db.update("bookmarks", bookmark, "document=?", arrayOf(id))
            db.delete("documents", "id=?", arrayOf(id))
        }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}
