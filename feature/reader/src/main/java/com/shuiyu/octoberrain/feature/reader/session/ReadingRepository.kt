package com.shuiyu.octoberrain.feature.reader.session

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.shuiyu.octoberrain.core.common.AppJson
import com.shuiyu.octoberrain.core.common.DocumentMetadata
import com.shuiyu.octoberrain.core.common.DocumentRef
import com.shuiyu.octoberrain.core.common.ReaderLocator
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.net.URLDecoder

/** 阅读数据库结构版本，升级约定见 [ReadingRepository.onUpgrade]。 */
private const val DATABASE_VERSION = 1

internal data class ReadingState(
    val locator: ReaderLocator?,
    val fontSp: Int = 20,
)

/**
 * 一条书签记录。
 *
 * @property id 数据库主键，改名与删除都以它定位。
 * @property label 规范化后的标题。
 * @property locator 书签指向的阅读位置。
 */
internal data class BookmarkRow(
    val id: Long,
    val label: String,
    val locator: ReaderLocator,
)

/**
 * 最近阅读的一条记录。
 *
 * @property ref 已解析的来源，可直接交给阅读器打开。
 * @property locator 最近一次保存的阅读位置。
 * @property updated 最近一次保存的毫秒时间戳。
 * @property persistent 该文档是否持有持久目录授权。
 */
internal data class RecentReading(
    val ref: DocumentRef,
    val locator: ReaderLocator,
    val updated: Long,
    val persistent: Boolean,
)

/**
 * 阅读记录的纯逻辑集合：标题规范化、最近阅读排序与来源显示名。
 *
 * 这里不引用 Android 框架与数据库，可在本地 JVM 单元测试中直接调用。
 */
internal object ReadingStateRules {
    /** 书签标题的码点上限。 */
    const val BOOKMARK_LABEL_LIMIT = 80

    /** 新增书签时标题为空的占位标题。 */
    const val BOOKMARK_LABEL_FALLBACK = "书签"

    /** 单份文档一次读取的书签条数上限。 */
    const val BOOKMARK_LIMIT = 100

    /** 最近阅读的默认条数。 */
    const val RECENT_LIMIT = 20

    /** 最近阅读允许的最大条数。 */
    const val RECENT_LIMIT_MAX = 100

    /**
     * 规范化书签标题。
     *
     * 去除首尾空白；结果为空时返回 null 表示拒绝；超过字符上限时按 Unicode
     * 码点截断，避免拆开代理对。
     *
     * @param raw 用户输入的原始标题。
     * @return 可写入数据库的标题，null 表示应当拒绝。
     */
    fun normalizeLabel(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return truncate(trimmed, BOOKMARK_LABEL_LIMIT)
    }

    /**
     * 返回可写入的标题，规范化失败时退回占位标题。
     *
     * @param raw 用户输入或界面状态带来的原始标题。
     */
    fun labelOrFallback(raw: String): String = normalizeLabel(raw) ?: BOOKMARK_LABEL_FALLBACK

    /**
     * 把请求条数限制在 0 到上限之间。
     *
     * @param limit 调用方请求的条数。
     * @param max 允许的最大条数。
     * @return 有效条数，0 表示不需要查询。
     */
    fun clampLimit(
        limit: Int,
        max: Int = RECENT_LIMIT_MAX,
    ): Int =
        when {
            limit <= 0 -> 0
            limit > max -> max
            else -> limit
        }

    /**
     * 按最近更新时间倒序排列并截断。
     *
     * 时间戳相同时按 [refKey] 升序排列，同一份数据每次输出顺序一致。
     *
     * @param items 已解析的最近阅读记录。
     * @param limit 输出条数。
     */
    fun orderRecent(
        items: List<RecentReading>,
        limit: Int,
    ): List<RecentReading> {
        val bounded = clampLimit(limit)
        if (bounded == 0) return emptyList()
        return items
            .sortedWith(compareByDescending<RecentReading> { it.updated }.thenBy { refKey(it.ref) })
            .take(bounded)
    }

    /**
     * 来源的稳定标识，用于排序与去重比较。
     *
     * @param ref 文档来源。
     */
    fun refKey(ref: DocumentRef): String =
        when (ref) {
            is DocumentRef.LocalPath -> "path:${ref.path}"
            is DocumentRef.ContentUri -> "uri:${ref.uri}"
            is DocumentRef.ArchiveEntry -> "archive:${refKey(ref.archive)}!/${ref.entry}"
        }

    /**
     * 来源在列表中的显示名。
     *
     * 本地路径取最后一段目录名；内容 URI 去掉查询串与片段后取最后一段并解码
     * 百分号转义；压缩包内条目组合外层来源名与条目最后一段。
     *
     * @param ref 文档来源。
     */
    fun displayName(ref: DocumentRef): String =
        when (ref) {
            is DocumentRef.LocalPath -> tailSegment(ref.path) ?: ref.path
            is DocumentRef.ContentUri -> tailSegment(stripQueryAndFragment(ref.uri))?.let(::decodePercent) ?: ref.uri
            is DocumentRef.ArchiveEntry -> "${displayName(ref.archive)}/${tailSegment(ref.entry) ?: ref.entry}"
        }

    /** 按 Unicode 码点截断，代理对不会被切开。 */
    private fun truncate(
        text: String,
        limit: Int,
    ): String {
        if (text.codePointCount(0, text.length) <= limit) return text
        return text.substring(0, text.offsetByCodePoints(0, limit))
    }

    /** 取以 / 或 \ 分隔的最后一段非空名称。 */
    private fun tailSegment(value: String): String? = value.split('/', '\\').lastOrNull { it.isNotBlank() }

    /** 去掉 URI 的查询串与片段，只保留路径部分。 */
    private fun stripQueryAndFragment(uri: String): String {
        var end = uri.length
        val query = uri.indexOf('?')
        if (query >= 0) end = minOf(end, query)
        val fragment = uri.indexOf('#')
        if (fragment >= 0) end = minOf(end, fragment)
        return uri.substring(0, end)
    }

    /** 解码 %XX 转义，字面量加号原样保留，解码失败退回原文本。 */
    private fun decodePercent(segment: String): String {
        if (segment.indexOf('%') < 0) return segment
        return runCatching { URLDecoder.decode(segment.replace("+", "%2B"), "UTF-8") }.getOrDefault(segment)
    }
}

/**
 * 读取一行书签并按标题规则回填，位置编码损坏的行由调用方跳过。
 *
 * @param rows 已定位到结果集的游标。
 */
private fun readBookmarkRows(rows: Cursor): List<BookmarkRow> {
    val result = mutableListOf<BookmarkRow>()
    rows.use { cursor ->
        while (cursor.moveToNext()) {
            val locator =
                runCatching { AppJson.format.decodeFromString<ReaderLocator>(cursor.getString(2)) }.getOrNull()
            if (locator == null) continue
            val label =
                ReadingStateRules.normalizeLabel(cursor.getString(1)) ?: ReadingStateRules.BOOKMARK_LABEL_FALLBACK
            result += BookmarkRow(cursor.getLong(0), label, locator)
        }
    }
    return result
}

/**
 * 读取一行最近阅读，来源或位置编码损坏的行由调用方跳过。
 *
 * @param rows 已定位到结果集的游标。
 */
private fun readRecentRows(rows: Cursor): List<RecentReading> {
    val result = mutableListOf<RecentReading>()
    rows.use { cursor ->
        while (cursor.moveToNext()) {
            val ref = runCatching { AppJson.format.decodeFromString<DocumentRef>(cursor.getString(0)) }.getOrNull()
            val locator =
                runCatching { AppJson.format.decodeFromString<ReaderLocator>(cursor.getString(1)) }.getOrNull()
            if (ref == null || locator == null) continue
            result += RecentReading(ref, locator, cursor.getLong(2), cursor.getInt(3) != 0)
        }
    }
    return result
}

/** 独立阅读记录，不读写Launcher配置或KOReader统计；所有数据库操作由宿主切到IO。 */
internal class ReadingRepository(
    context: Context,
) : SQLiteOpenHelper(context.applicationContext, "reader.db", null, DATABASE_VERSION) {
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

    /**
     * 当前版本不需要结构升级：documents 表已保存 ref、locator、updated 与
     * persistent，bookmarks 表已带自增 id，最近阅读与书签编辑都可直接复用。
     *
     * 后续版本若新增列，必须在此按 oldVersion 递增执行 ALTER TABLE ADD COLUMN，
     * 禁止重建 bookmarks 表，以保留用户已有书签。
     */
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

    /**
     * 读取一份文档的书签行，按创建时间倒序，最多 [ReadingStateRules.BOOKMARK_LIMIT] 条。
     *
     * 位置编码损坏的行直接跳过，原始记录保留在数据库中。
     *
     * @param id 文档标识。
     */
    fun bookmarkRows(id: String): List<BookmarkRow> =
        readableDatabase
            .query(
                "bookmarks",
                arrayOf("id", "label", "locator"),
                "document=?",
                arrayOf(id),
                null,
                null,
                "created DESC, id DESC",
                ReadingStateRules.BOOKMARK_LIMIT.toString(),
            ).use(::readBookmarkRows)

    /**
     * 兼容既有界面的书签读取，只返回标题与位置。
     *
     * 编辑与删除需要的书签主键由 [bookmarkRows] 提供。
     *
     * @param id 文档标识。
     */
    fun bookmarks(id: String): List<Pair<String, ReaderLocator>> = bookmarkRows(id).map { it.label to it.locator }

    fun bookmark(
        id: String,
        locator: ReaderLocator,
        label: String,
    ) {
        val values =
            ContentValues().apply {
                put("document", id)
                put("locator", AppJson.format.encodeToString(locator))
                put("label", ReadingStateRules.labelOrFallback(label))
                put("created", System.currentTimeMillis())
            }
        check(writableDatabase.insertOrThrow("bookmarks", null, values) != -1L)
    }

    /**
     * 修改书签标题。
     *
     * 标题先按 [ReadingStateRules.normalizeLabel] 处理：首尾空白丢弃、超长按码点
     * 截断；空白标题直接拒绝且不写库。返回 false 同时覆盖标题被拒绝与主键未命中。
     *
     * @param id 书签主键。
     * @param label 新标题。
     * @return 是否真的更新了一行。
     */
    fun updateBookmark(
        id: Long,
        label: String,
    ): Boolean {
        val normalized = ReadingStateRules.normalizeLabel(label) ?: return false
        val values = ContentValues().apply { put("label", normalized) }
        return writableDatabase.update("bookmarks", values, "id=?", arrayOf(id.toString())) > 0
    }

    /**
     * 删除书签。
     *
     * @param id 书签主键。
     * @return 是否真的删除了一行。
     */
    fun deleteBookmark(id: Long): Boolean = writableDatabase.delete("bookmarks", "id=?", arrayOf(id.toString())) > 0

    /**
     * 读取最近阅读，按更新时间倒序。
     *
     * ref 或 locator 编码损坏的行跳过，原始记录保留；limit 小于 1 时返回空列表。
     *
     * @param limit 最多返回的条数。
     */
    fun recent(limit: Int): List<RecentReading> {
        val bounded = ReadingStateRules.clampLimit(limit)
        if (bounded == 0) return emptyList()
        return ReadingStateRules.orderRecent(
            readableDatabase
                .query(
                    "documents",
                    arrayOf("ref", "locator", "updated", "persistent"),
                    null,
                    null,
                    null,
                    null,
                    "updated DESC, id DESC",
                    bounded.toString(),
                ).use(::readRecentRows),
            bounded,
        )
    }

    companion object {
        /**
         * 一次性查询最近阅读，供不属于阅读会话的入口使用。
         *
         * 只关闭本次新建的连接，用户数据仍保存在数据库文件中；正在阅读的会话
         * 持有自己的连接，不受影响。
         *
         * @param context 任意上下文，内部取 applicationContext。
         * @param limit 最多返回的条数。
         */
        fun recent(
            context: Context,
            limit: Int = ReadingStateRules.RECENT_LIMIT,
        ): List<RecentReading> {
            val repository = ReadingRepository(context)
            return try {
                repository.recent(limit)
            } finally {
                repository.close()
            }
        }
    }
}
