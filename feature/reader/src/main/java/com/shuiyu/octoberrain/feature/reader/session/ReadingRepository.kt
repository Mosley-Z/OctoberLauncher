package com.shuiyu.octoberrain.feature.reader.session

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.shuiyu.octoberrain.core.common.AppJson
import com.shuiyu.octoberrain.core.common.DocumentMetadata
import com.shuiyu.octoberrain.core.common.DocumentRef
import com.shuiyu.octoberrain.core.common.ReaderLocator
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

internal data class ReadingState(
    val locator: ReaderLocator?,
    val fontSp: Int = 20,
)

/** 独立阅读记录，不读写Launcher配置或KOReader统计；所有数据库操作由宿主切到IO。 */
internal class ReadingRepository(
    context: Context,
) : SQLiteOpenHelper(context.applicationContext, "reader.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE documents(id TEXT PRIMARY KEY,ref TEXT NOT NULL,size INTEGER,modified INTEGER," +
                "locator TEXT NOT NULL,font INTEGER NOT NULL,updated INTEGER NOT NULL,persistent INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE bookmarks(id INTEGER PRIMARY KEY AUTOINCREMENT,document TEXT NOT NULL," +
                "locator TEXT NOT NULL,label TEXT NOT NULL,created INTEGER NOT NULL)",
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) = Unit

    fun load(metadata: DocumentMetadata): ReadingState {
        readableDatabase
            .query(
                "documents",
                arrayOf("size", "modified", "locator", "font"),
                "id=?",
                arrayOf(metadata.id),
                null,
                null,
                null,
            ).use { row ->
                if (!row.moveToFirst()) return ReadingState(null)
                val sameSize = metadata.size == null || row.isNull(0) || row.getLong(0) == metadata.size
                val sameTime = metadata.modified == null || row.isNull(1) || row.getLong(1) == metadata.modified
                val locator =
                    if (sameSize &&
                        sameTime
                    ) {
                        runCatching { AppJson.format.decodeFromString<ReaderLocator>(row.getString(2)) }.getOrNull()
                    } else {
                        null
                    }
                return ReadingState(locator, row.getInt(3).coerceIn(14, 36))
            }
    }

    fun save(
        metadata: DocumentMetadata,
        source: DocumentRef,
        locator: ReaderLocator,
        font: Int,
    ) {
        val values =
            ContentValues().apply {
                put("id", metadata.id)
                put("persistent", if (metadata.persistentAccess) 1 else 0)
                put("ref", AppJson.format.encodeToString(source))
                put("size", metadata.size)
                put("modified", metadata.modified)
                put("locator", AppJson.format.encodeToString(locator))
                put("font", font)
                put("updated", System.currentTimeMillis())
            }
        check(writableDatabase.insertWithOnConflict("documents", null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
    }

    fun bookmarks(id: String): List<Pair<String, ReaderLocator>> {
        val result = mutableListOf<Pair<String, ReaderLocator>>()
        readableDatabase
            .query(
                "bookmarks",
                arrayOf("label", "locator"),
                "document=?",
                arrayOf(id),
                null,
                null,
                "created DESC",
                "100",
            ).use { rows ->
                while (rows.moveToNext()) {
                    val locator =
                        runCatching {
                            AppJson.format.decodeFromString<ReaderLocator>(
                                rows.getString(1),
                            )
                        }.getOrNull()
                    if (locator != null) result.add(rows.getString(0) to locator)
                }
            }
        return result
    }

    fun bookmark(
        id: String,
        locator: ReaderLocator,
        label: String,
    ) {
        val values =
            ContentValues().apply {
                put("document", id)
                put("locator", AppJson.format.encodeToString(locator))
                put("label", label)
                put("created", System.currentTimeMillis())
            }
        check(writableDatabase.insertOrThrow("bookmarks", null, values) != -1L)
    }
}
