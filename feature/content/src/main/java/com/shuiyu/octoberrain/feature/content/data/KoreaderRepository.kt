package com.shuiyu.octoberrain.feature.content.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.shuiyu.octoberrain.core.common.BookItem
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.ReadingSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

/** KOReader 书目条目，附带完成度。 */
data class KoreaderEntry(
    val book: BookItem,
    val progress: Float,
)

/** KOReader 阅读统计，包含当日、当月与最近若干天的分钟数。 */
data class KoreaderStats(
    val todayMinutes: Int,
    val monthMinutes: Int,
    val days: List<Int>,
    val maxMinutes: Int,
)

/**
 * KOReader 数据读取。
 *
 * 从 koreader 目录解析最近阅读书目、侧边车元数据与 statistics.sqlite3 阅读时长。
 * 数据库先复制到应用缓存再以只读方式查询，避免占用上游数据库文件；Lua 文件用
 * 正则解析，不引入脚本解释器。
 */
class KoreaderRepository(
    private val context: Context,
) {
    companion object {
        private const val TAG = "KOReader"
        private const val LUA_LIMIT_BYTES = 64L * 1024L
        private const val HEAT_DAYS = 28
        private const val COVER_LIMIT = 12
        private const val CACHE_TTL_MS = 60_000L
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val DATABASE_NAME = "statistics.sqlite3"

        private val LAST_FILE_PATTERN = Regex("""lastfile\s*=\s*["']([^"']+)["']""")
        private val HISTORY_FILE_PATTERN = Regex("""file\s*=\s*["']([^"']+)["']""")
        private val TITLE_PATTERN = Regex("""title\s*=\s*["']([^"']{1,200})["']""")
        private val AUTHOR_PATTERN = Regex("""authors?\s*=\s*["']([^"']{1,200})["']""")
        private val PERCENT_PATTERN = Regex("""percent_finished\s*=\s*([0-9]+(?:\.[0-9]+)?)""")
    }

    private var cachedAt = 0L
    private var cachedEntries: List<KoreaderEntry> = emptyList()

    /** 清空书目缓存，下次访问重新解析。 */
    fun invalidate() {
        cachedAt = 0L
        cachedEntries = emptyList()
    }

    /**
     * 读取最近阅读书目。
     *
     * @param limit 最多返回条目数
     * @param deadlineMs 解析截止时间，从调用时刻起算
     */
    suspend fun recentEntries(
        limit: Int = 20,
        deadlineMs: Long = 5000L,
    ): List<KoreaderEntry> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (cachedEntries.isNotEmpty() && now - cachedAt < CACHE_TTL_MS) {
                return@withContext cachedEntries.take(limit)
            }
            val deadline = now + deadlineMs.coerceAtLeast(500L)
            val root = ContentPaths.koreaderRoot() ?: return@withContext emptyList()
            val paths = LinkedHashSet<String>()
            readLastFile(root)?.let { paths.add(it) }
            paths.addAll(readHistory(root))
            val entries = ArrayList<KoreaderEntry>()
            for (path in paths) {
                if (System.currentTimeMillis() > deadline) break
                if (entries.size >= limit.coerceAtLeast(1)) break
                val file = toLocalFile(path) ?: continue
                if (!file.isFile) continue
                val metadata = readMetadata(file)
                val cover =
                    if (entries.size < COVER_LIMIT) {
                        CoverResolver.resolve(context, file)
                    } else {
                        CoverResolver.sidecarCover(file)?.absolutePath
                    }
                entries.add(
                    KoreaderEntry(
                        book =
                            BookItem(
                                path = file.absolutePath,
                                title = metadata.title ?: file.nameWithoutExtension,
                                author = metadata.author ?: "",
                                format = file.extension.uppercase(),
                                coverPath = cover,
                                sizeBytes = file.length(),
                                addedAt = file.lastModified(),
                            ),
                        progress = metadata.progress,
                    ),
                )
            }
            cachedEntries = entries
            cachedAt = System.currentTimeMillis()
            entries.take(limit)
        }

    /** 由最近阅读书目与阅读统计组合出全局阅读快照。 */
    suspend fun readingSnapshot(): ReadingSnapshot? {
        val entry = recentEntries(1).firstOrNull() ?: return null
        val statistics = stats()
        return ReadingSnapshot(
            source = "koreader",
            title = entry.book.title,
            author = entry.book.author,
            coverPath = entry.book.coverPath,
            progress = entry.progress,
            todayMinutes = statistics?.todayMinutes ?: 0,
            monthMinutes = statistics?.monthMinutes ?: 0,
            lastReadAt = File(entry.book.path).lastModified(),
        )
    }

    /** 读取阅读统计，数据目录或数据库缺失时返回 null。 */
    suspend fun stats(): KoreaderStats? =
        withContext(Dispatchers.IO) {
            val root = ContentPaths.koreaderRoot() ?: return@withContext null
            val database = prepareDatabase(root) ?: return@withContext null
            try {
                val todayStart = startOfToday()
                val tomorrowStart = todayStart + DAY_MS
                val heatStart = shiftDays(todayStart, -(HEAT_DAYS - 1))
                val monthStart = startOfMonth(todayStart)
                val monthEnd = startOfNextMonth(todayStart)
                val recentSeconds = queryDailySeconds(database, heatStart, tomorrowStart)
                val monthSeconds = queryDailySeconds(database, monthStart, monthEnd)
                val days = ArrayList<Int>(HEAT_DAYS)
                var maxMinutes = 0
                for (offset in HEAT_DAYS - 1 downTo 0) {
                    val dayStart = shiftDays(todayStart, -offset)
                    val minutes = ((recentSeconds[dayStart] ?: 0L) / 60L).toInt()
                    days.add(minutes)
                    if (minutes > maxMinutes) maxMinutes = minutes
                }
                val todayMinutes = ((recentSeconds[todayStart] ?: 0L) / 60L).toInt()
                val monthMinutes = (monthSeconds.values.sum() / 60L).toInt()
                KoreaderStats(
                    todayMinutes = todayMinutes,
                    monthMinutes = monthMinutes,
                    days = days,
                    maxMinutes = maxMinutes,
                )
            } finally {
                runCatching { database.close() }
            }
        }

    /** 读取 settings.reader.lua 中的最近阅读文件路径。 */
    private fun readLastFile(root: File): String? {
        val candidates =
            listOf(
                File(root, "settings.reader.lua"),
                File(root, "settings/settings.reader.lua"),
            )
        val file = candidates.firstOrNull { it.isFile } ?: return null
        val text = readLimitedText(file) ?: return null
        return LAST_FILE_PATTERN.find(text)?.groupValues?.getOrNull(1)
    }

    /** 读取 history.lua 中记录的文件路径序列，保持原始顺序。 */
    private fun readHistory(root: File): List<String> {
        val file = File(root, "history.lua")
        if (!file.isFile) return emptyList()
        val text = readLimitedText(file) ?: return emptyList()
        return HISTORY_FILE_PATTERN
            .findAll(text)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .take(40)
            .toList()
    }

    /** 读取不超过上限的 Lua 文本，超限或失败返回 null。 */
    private fun readLimitedText(file: File): String? {
        if (file.length() <= 0L || file.length() > LUA_LIMIT_BYTES) return null
        return runCatching { file.readText() }.getOrElse {
            InkLog.w(TAG, "读取 ${file.name} 失败：${it.message}")
            null
        }
    }

    /** KOReader 侧边车元数据。 */
    private data class BookMetadata(
        val title: String?,
        val author: String?,
        val progress: Float,
    )

    /** 读取 <书籍>.sdr/metadata.<扩展名>.lua 中的标题、作者与完成度。 */
    private fun readMetadata(book: File): BookMetadata {
        val sidecar = File(book.absolutePath + ".sdr")
        if (!sidecar.isDirectory) return BookMetadata(null, null, 0f)
        val candidates =
            listOf(
                File(sidecar, "metadata.${book.extension.lowercase()}.lua"),
                File(sidecar, "metadata.lua"),
            )
        val file = candidates.firstOrNull { it.isFile } ?: return BookMetadata(null, null, 0f)
        val text = readLimitedText(file) ?: return BookMetadata(null, null, 0f)
        val title =
            TITLE_PATTERN
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { unescape(it) }
                ?.takeIf { it.isNotBlank() }
        val author =
            AUTHOR_PATTERN
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.let { unescape(it) }
                ?.takeIf { it.isNotBlank() }
        val progress =
            PERCENT_PATTERN
                .find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.toFloatOrNull()
                ?.coerceIn(0f, 1f) ?: 0f
        return BookMetadata(title, author, progress)
    }

    /** 把设备绝对路径转换为可访问的本地文件，兼容 /sdcard 与 /storage/emulated/0 前缀。 */
    private fun toLocalFile(raw: String): File? {
        val path = raw.trim()
        if (path.isEmpty()) return null
        val external = ContentPaths.externalRoot().absolutePath
        val candidates = mutableListOf<File>()
        if (path.startsWith("/sdcard/")) candidates.add(File(external, path.removePrefix("/sdcard/")))
        if (path.startsWith("/storage/emulated/0/")) {
            candidates.add(File(external, path.removePrefix("/storage/emulated/0/")))
        }
        candidates.add(File(path))
        return candidates.firstOrNull { it.isFile }
    }

    /** 把上游数据库复制到缓存目录并打开只读连接，失败返回 null。 */
    private fun prepareDatabase(root: File): SQLiteDatabase? {
        val source = File(File(root, "settings"), DATABASE_NAME)
        if (!source.isFile || source.length() <= 0L) return null
        return runCatching {
            val target = File(context.cacheDir, "koreader_$DATABASE_NAME")
            val stale =
                !target.isFile ||
                    target.length() != source.length() ||
                    source.lastModified() > target.lastModified()
            if (stale) {
                copyDatabase(source, target)
                copyDatabase(File(source.absolutePath + "-wal"), File(target.absolutePath + "-wal"))
                copyDatabase(File(source.absolutePath + "-shm"), File(target.absolutePath + "-shm"))
            }
            SQLiteDatabase.openDatabase(target.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        }.getOrElse {
            InkLog.w(TAG, "打开阅读统计数据库失败：${it.message}", it)
            null
        }
    }

    /** 复制数据库文件，来源缺失时删除旧副本避免读到过期数据。 */
    private fun copyDatabase(
        source: File,
        target: File,
    ) {
        if (!source.isFile) {
            target.delete()
            return
        }
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target.setLastModified(source.lastModified())
    }

    /** 按日累加 reading seconds，兼容秒与毫秒两种 start_time 记录。 */
    private fun queryDailySeconds(
        database: SQLiteDatabase,
        rangeStart: Long,
        rangeEnd: Long,
    ): Map<Long, Long> {
        val result = HashMap<Long, Long>()
        val sql = "SELECT start_time, duration FROM page_stat_data WHERE start_time >= ? AND start_time < ?"
        val arguments =
            arrayOf(
                (rangeStart / 1000L).toString(),
                (rangeEnd / 1000L).toString(),
            )
        runCatching {
            database.rawQuery(sql, arguments).use { cursor ->
                while (cursor.moveToNext()) {
                    val startTime = cursor.getLong(0)
                    val duration = cursor.getLong(1)
                    if (startTime <= 0L || duration <= 0L) continue
                    val day = dayStart(normalizeTime(startTime))
                    result[day] = (result[day] ?: 0L) + duration
                }
            }
        }.onFailure { InkLog.w(TAG, "查询阅读统计失败：${it.message}", it) }
        return result
    }

    /** 统一时间戳单位，十位数按秒、更大数值按毫秒处理。 */
    private fun normalizeTime(value: Long): Long = if (value < 100_000_000_000L) value * 1000L else value

    /** 计算指定时刻所在自然日的零点。 */
    private fun dayStart(millis: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = millis
        zeroTime(calendar)
        return calendar.timeInMillis
    }

    /** 今天零点。 */
    private fun startOfToday(): Long {
        val calendar = Calendar.getInstance()
        zeroTime(calendar)
        return calendar.timeInMillis
    }

    /** 以某日零点为基准前后平移天数。 */
    private fun shiftDays(
        baseMillis: Long,
        days: Int,
    ): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = baseMillis
        calendar.add(Calendar.DAY_OF_MONTH, days)
        zeroTime(calendar)
        return calendar.timeInMillis
    }

    /** 指定时刻所在月份的月初零点。 */
    private fun startOfMonth(millis: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = millis
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        zeroTime(calendar)
        return calendar.timeInMillis
    }

    /** 指定时刻所在月份的下月月初零点。 */
    private fun startOfNextMonth(millis: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = millis
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        zeroTime(calendar)
        calendar.add(Calendar.MONTH, 1)
        return calendar.timeInMillis
    }

    /** 将日历时刻归零到时分秒均为零。 */
    private fun zeroTime(calendar: Calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }

    /** 还原 Lua 字符串中的转义字符。 */
    private fun unescape(value: String): String = value.replace("\\\"", "\"").replace("\\\\", "\\")
}
