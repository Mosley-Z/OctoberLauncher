package com.shuiyu.octoberrain.feature.content.data

import android.content.Context
import com.shuiyu.octoberrain.core.common.BookCollection
import com.shuiyu.octoberrain.core.common.BookItem
import com.shuiyu.octoberrain.core.common.InkLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 书架扫描与检索。
 *
 * 扫描书籍根目录下的书籍文件，读取 KOReader 侧边车元数据补全标题、作者与
 * 完成度，解析封面并按最近阅读优先排序。扫描在 IO 线程执行，带数量上限与
 * 截止时间，超时后返回已收集的部分结果。
 */
class BookshelfRepository(
    private val context: Context,
) {
    companion object {
        private const val TAG = "书架"
        const val MAX_DEPTH = 4
        const val MAX_FILES = 400
        private const val MAX_COVERS = 24
        private const val SIDECAR_LIMIT_BYTES = 64L * 1024L
        private const val CACHE_TTL_MS = 60_000L
        private const val MAX_TITLE_LENGTH = 200

        /** 书籍直接放在根目录时归入的合集名。 */
        const val UNCATEGORIZED_NAME = "未分类"

        /** 书架支持的书籍扩展名。 */
        val BOOK_EXTENSIONS = setOf("epub", "mobi", "azw", "azw3", "pdf", "txt", "fb2", "djvu", "cbz", "cbr")

        /** 侧边车元数据中的标题字段。 */
        private val TITLE_PATTERN = Regex("""title\s*=\s*["']([^"']{1,200})["']""")

        /** 侧边车元数据中的作者字段。 */
        private val AUTHOR_PATTERN = Regex("""authors?\s*=\s*["']([^"']{1,200})["']""")
    }

    private val scanning = AtomicBoolean(false)
    private var cachedAt = 0L
    private var cached: List<BookItem> = emptyList()
    private var cachedCollections: List<BookCollection> = emptyList()

    /** 清空扫描缓存，下次访问重新扫描。 */
    fun invalidate() {
        cachedAt = 0L
        cached = emptyList()
        cachedCollections = emptyList()
    }

    /** 返回当前缓存的书架条目，供页面在扫描完成前占位显示。 */
    fun cached(limit: Int): List<BookItem> {
        val snapshot = cached
        return if (limit <= 0) snapshot else snapshot.take(limit)
    }

    /** 返回当前缓存的合集划分，供页面在扫描完成前占位显示。 */
    fun cachedCollections(): List<BookCollection> = cachedCollections

    /**
     * 扫描书架。
     *
     * @param limit 最多返回的条目数，非正数表示不限制
     * @param deadlineMs 截止时间，从调用时刻起算，最低 500 毫秒
     */
    suspend fun scan(
        limit: Int = MAX_FILES,
        deadlineMs: Long = 5000L,
    ): List<BookItem> =
        withContext(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + deadlineMs.coerceAtLeast(500L)
            val root = ContentPaths.booksRoot()
            if (!root.exists()) root.mkdirs()
            if (!root.isDirectory) {
                InkLog.w(TAG, "书籍目录不可用：${root.absolutePath}")
                return@withContext cached(limit)
            }
            val files = collectBooks(root, deadline)
            if (files.isEmpty()) {
                cached = emptyList()
                cachedCollections = buildCollections(root, emptyList())
                cachedAt = System.currentTimeMillis()
                return@withContext emptyList()
            }
            val sorted =
                files.sortedWith(
                    compareByDescending<File> { it.lastModified() }.thenBy { it.name.lowercase() },
                )
            val items = ArrayList<BookItem>(sorted.size)
            var coverCount = 0
            for (file in sorted) {
                val sidecar = readSidecar(file)
                val cover =
                    if (coverCount < MAX_COVERS && System.currentTimeMillis() < deadline) {
                        val path = CoverResolver.resolve(context, file)
                        if (path != null) coverCount += 1
                        path
                    } else {
                        CoverResolver.sidecarCover(file)?.absolutePath
                    }
                items.add(
                    BookItem(
                        path = file.absolutePath,
                        title = sidecar.title ?: file.nameWithoutExtension,
                        author = sidecar.author ?: "",
                        format = file.extension.uppercase(),
                        coverPath = cover,
                        sizeBytes = file.length(),
                        addedAt = file.lastModified(),
                    ),
                )
            }
            cached = items
            cachedCollections = buildCollections(root, items)
            cachedAt = System.currentTimeMillis()
            if (limit <= 0 || limit >= items.size) items else items.take(limit)
        }

    /**
     * 返回按书籍根目录一级子目录划分的合集。
     *
     * 根目录下的书籍归入「未分类」，一级子目录即使尚未放入书籍也会列出，
     * 便于从书架直接进入对应目录添加文件。
     */
    suspend fun collections(deadlineMs: Long = 5000L): List<BookCollection> =
        withContext(Dispatchers.IO) {
            if (cachedCollections.isEmpty() || System.currentTimeMillis() - cachedAt >= CACHE_TTL_MS) {
                scan(MAX_FILES, deadlineMs)
            }
            cachedCollections
        }

    /**
     * 在书架中检索书籍，匹配标题、作者与路径。
     *
     * @param query 检索关键词，空白关键词返回空列表
     * @param limit 最多返回条目数
     * @param deadlineMs 首次扫描的截止时间
     */
    suspend fun search(
        query: String,
        limit: Int = 30,
        deadlineMs: Long = 5000L,
    ): List<BookItem> {
        val keyword = query.trim()
        if (keyword.isEmpty()) return emptyList()
        val all =
            if (cached.isNotEmpty() && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
                cached
            } else {
                scan(MAX_FILES, deadlineMs)
            }
        return all
            .filter { item ->
                item.title.contains(keyword, ignoreCase = true) ||
                    item.author.contains(keyword, ignoreCase = true) ||
                    item.path.contains(keyword, ignoreCase = true)
            }.take(limit)
    }

    /**
     * 依据书籍路径与一级子目录生成合集划分。
     *
     * @param root 书籍根目录
     * @param items 已扫描到的书籍条目，列表顺序即合集内的展示顺序
     */
    private fun buildCollections(
        root: File,
        items: List<BookItem>,
    ): List<BookCollection> {
        val prefix = root.absolutePath.trimEnd(File.separatorChar) + File.separator
        val grouped = LinkedHashMap<String, MutableList<String>>()
        for (item in items) {
            val relative = item.path.removePrefix(prefix)
            val separator = relative.indexOf(File.separatorChar)
            val collection = if (separator > 0) relative.substring(0, separator) else UNCATEGORIZED_NAME
            if (collection != UNCATEGORIZED_NAME && !isCollectionDirectory(collection)) {
                grouped.getOrPut(UNCATEGORIZED_NAME) { ArrayList() }.add(item.path)
            } else {
                grouped.getOrPut(collection) { ArrayList() }.add(item.path)
            }
        }
        val directories =
            runCatching {
                root
                    .listFiles { file -> file.isDirectory && isCollectionDirectory(file.name) }
                    ?.sortedBy { it.name.lowercase() }
                    .orEmpty()
            }.getOrDefault(emptyList())
        val result = ArrayList<BookCollection>(directories.size + 1)
        result.add(
            BookCollection(
                id = root.absolutePath,
                name = UNCATEGORIZED_NAME,
                bookPaths = grouped[UNCATEGORIZED_NAME].orEmpty().toList(),
            ),
        )
        for (directory in directories) {
            result.add(
                BookCollection(
                    id = directory.absolutePath,
                    name = directory.name,
                    bookPaths = grouped[directory.name].orEmpty().toList(),
                ),
            )
        }
        return result
    }

    /** 判断一级子目录是否应作为合集展示，封面目录、侧边车与隐藏目录除外。 */
    private fun isCollectionDirectory(name: String): Boolean {
        if (name.isBlank() || name.startsWith(".")) return false
        if (name.endsWith(".sdr", ignoreCase = true)) return false
        if (name.equals("cover", ignoreCase = true) || name.equals("covers", ignoreCase = true)) return false
        return true
    }

    /** 递归收集书籍文件，达到数量上限或截止时间即停止。 */
    private fun collectBooks(
        root: File,
        deadline: Long,
    ): List<File> {
        val files = ArrayList<File>()
        runCatching {
            for (file in root.walkTopDown().maxDepth(MAX_DEPTH)) {
                if (System.currentTimeMillis() > deadline) break
                if (file.isFile && file.extension.lowercase() in BOOK_EXTENSIONS) {
                    files.add(file)
                    if (files.size >= MAX_FILES) break
                }
            }
        }.onFailure { InkLog.w(TAG, "扫描书籍目录失败：${it.message}", it) }
        return files
    }

    /** 侧边车元数据解析结果。 */
    private data class SidecarInfo(
        val title: String?,
        val author: String?,
    )

    /** 读取 <书名>.sdr/metadata.<扩展名>.lua 中的标题与作者。 */
    private fun readSidecar(book: File): SidecarInfo {
        val sidecar = File(book.absolutePath + ".sdr")
        if (!sidecar.isDirectory) return SidecarInfo(null, null)
        val extension = book.extension.lowercase()
        val candidates =
            listOf(
                File(sidecar, "metadata.$extension.lua"),
                File(sidecar, "metadata.lua"),
            )
        val metadata = candidates.firstOrNull { it.isFile } ?: return SidecarInfo(null, null)
        if (metadata.length() > SIDECAR_LIMIT_BYTES) return SidecarInfo(null, null)
        val text = runCatching { metadata.readText() }.getOrNull() ?: return SidecarInfo(null, null)
        val title =
            TITLE_PATTERN
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { unescape(it) }
        val author =
            AUTHOR_PATTERN
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { unescape(it) }
        return SidecarInfo(
            title = title?.take(MAX_TITLE_LENGTH)?.takeIf { it.isNotBlank() },
            author = author?.take(MAX_TITLE_LENGTH)?.takeIf { it.isNotBlank() },
        )
    }

    /** 还原 Lua 字符串中的转义字符。 */
    private fun unescape(value: String): String = value.replace("\\\"", "\"").replace("\\\\", "\\")
}
