package com.shuiyu.octoberrain.feature.files.data

import android.content.Context
import android.provider.MediaStore
import com.shuiyu.octoberrain.core.common.InkLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 相册条目。
 *
 * 索引层只保存展示与排序所需字段，不持有 Cursor 与 Context；所有字段在构造后不可变。
 * 拍摄时间缺失（值为 0）时按加入时间参与排序，两者都缺失的条目排在同日末尾。
 *
 * 参数 id 为 MediaStore 行号，目录扫描产生的条目为 0；path 为绝对路径；
 * name 为显示名称；takenAt 为拍摄时间毫秒；addedAt 为加入媒体库时间毫秒；
 * width、height、orientation 为原始图像属性，目录扫描时补齐为 0；
 * mime 为 MIME 类型，空串表示未知。
 */
internal data class MediaEntry(
    val id: Long,
    val path: String,
    val name: String,
    val takenAt: Long,
    val addedAt: Long,
    val width: Int,
    val height: Int,
    val orientation: Int,
    val mime: String,
) {
    /** 参与排序与分桶的时间，优先拍摄时间，缺失时回退加入时间。 */
    val time: Long get() = if (takenAt > 0L) takenAt else addedAt

    /** 是否为图片条目。 */
    val isImage: Boolean get() = mime.startsWith("image/")
}

/**
 * 单日分组。
 *
 * key 形如 `2026-10-19`；title 为组头显示文本；items 按时间倒序，首张作为封面。
 */
internal data class MediaDay(
    val key: String,
    val title: String,
    val time: Long,
    val items: List<MediaEntry>,
) {
    /** 当日图片数量。 */
    val count: Int get() = items.size

    /** 当日首张图片路径，供组头小图使用。 */
    val coverPath: String get() = items.first().path
}

/**
 * 单月分组，由日分组聚合而来，供后续月度视图使用。
 *
 * key 形如 `2026-10`；count 为当月图片总数；coverPath 为当月首张图片路径。
 */
internal data class MediaMonth(
    val key: String,
    val title: String,
    val time: Long,
    val count: Int,
    val coverPath: String,
)

/**
 * 索引快照。
 *
 * 条目在构造时完成排序与分桶，字段只读；updatedAt 记录本次写入时间，
 * 页面显示该时间用于说明数据新鲜度。
 */
internal data class MediaIndexData(
    val updatedAt: Long,
    val entries: List<MediaEntry>,
) {
    /** 按日分组，组内按时间倒序。 */
    val days: List<MediaDay> = MediaBuckets.days(entries)

    /** 按月分组，供月度统计与后续视图使用。 */
    val months: List<MediaMonth> = MediaBuckets.months(days)

    /** 条目为空时视为空相册。 */
    val isEmpty: Boolean get() = entries.isEmpty()
}

/**
 * 时间与日期换算，时区可注入，便于本地 JVM 用例固定边界。
 */
internal object MediaTime {
    private val DAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val TITLE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")
    private val MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

    /** 取日期键，形如 2026-10-19。 */
    fun dayKey(
        time: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = DAY_FORMATTER.format(instantOf(time).atZone(zone))

    /** 取月份键，形如 2026-10。 */
    fun monthKey(
        time: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = MONTH_FORMATTER.format(instantOf(time).atZone(zone))

    /** 取日分组标题，形如 10月19日。 */
    fun dayTitle(
        time: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = TITLE_FORMATTER.format(instantOf(time).atZone(zone))

    /** 取月份标题，形如 2026年10月。 */
    fun monthTitle(
        time: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val date = instantOf(time).atZone(zone).toLocalDate()
        return "${date.year}年${date.monthValue}月"
    }

    /** 取当日零点毫秒，负时间与零按纪元处理。 */
    fun dayStart(
        time: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val date: LocalDate = instantOf(time).atZone(zone).toLocalDate()
        return date.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** 把时间戳转为 Instant，非正值按纪元起点处理。 */
    private fun instantOf(time: Long): Instant = Instant.ofEpochMilli(time.coerceAtLeast(0L))

    /** 把本地日期时间转为毫秒，用例与月度视图共用。 */
    fun toMillis(
        dateTime: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long = dateTime.atZone(zone).toInstant().toEpochMilli()
}

/**
 * 排序与分桶。
 *
 * 排序键先取时间倒序，再取路径升序，保证同一时间戳的条目顺序稳定；
 * 分桶在排序结果上按日、按月聚合，组内保持排序结果。
 */
internal object MediaBuckets {
    /**
     * 稳定排序。
     *
     * 参数 entries 为待排序条目；返回按时间倒序、同时间按路径升序的新列表，
     * 输入不被修改。
     */
    fun sorted(entries: List<MediaEntry>): List<MediaEntry> =
        entries.sortedWith(
            compareByDescending<MediaEntry> { it.time }
                .thenBy { it.path },
        )

    /**
     * 按日分组。
     *
     * 参数 entries 为任意顺序的条目，内部先排序；日分组按日期倒序排列，
     * time 取当日第一张图片的时间。
     */
    fun days(
        entries: List<MediaEntry>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<MediaDay> {
        val grouped = LinkedHashMap<String, MutableList<MediaEntry>>()
        for (entry in sorted(entries)) {
            val key = MediaTime.dayKey(entry.time, zone)
            grouped.getOrPut(key) { mutableListOf() }.add(entry)
        }
        return grouped.map { (key, items) ->
            MediaDay(
                key = key,
                title = MediaTime.dayTitle(items.first().time, zone),
                time = items.first().time,
                items = items,
            )
        }
    }

    /**
     * 按月分组。
     *
     * 参数 days 为已按日期倒序的日分组；结果按月倒序，count 为当月条目合计，
     * coverPath 取当月首张图片。
     */
    fun months(
        days: List<MediaDay>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<MediaMonth> {
        val grouped = LinkedHashMap<String, MonthAccumulator>()
        for (day in days) {
            val key = MediaTime.monthKey(day.time, zone)
            val accumulator = grouped.getOrPut(key) { MonthAccumulator(day.time, day.coverPath) }
            accumulator.count += day.count
        }
        return grouped.map { (key, accumulator) ->
            MediaMonth(
                key = key,
                title = MediaTime.monthTitle(accumulator.time, zone),
                time = accumulator.time,
                count = accumulator.count,
                coverPath = accumulator.coverPath,
            )
        }
    }

    /** 月度聚合中间量。 */
    private class MonthAccumulator(
        val time: Long,
        val coverPath: String,
    ) {
        var count: Int = 0
    }
}

/**
 * 索引 JSON 编解码。
 *
 * 格式为 `{"version":1,"updatedAt":毫秒,"entries":[{...}]}`；版本不一致、字段缺失、
 * 类型不符或整体无法解析时返回 null，由调用方丢弃并重建索引。
 */
internal object MediaIndexCodec {
    /** 当前索引格式版本，结构变化时递增。 */
    const val VERSION: Int = 1

    private val format: Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = false
        }

    /** 编码索引，字段顺序固定，便于字节数核对。 */
    fun encode(data: MediaIndexData): String =
        buildJsonObject {
            put("version", JsonPrimitive(VERSION))
            put("updatedAt", JsonPrimitive(data.updatedAt))
            put(
                "entries",
                buildJsonArray {
                    data.entries.forEach { entry ->
                        add(
                            buildJsonObject {
                                put("id", JsonPrimitive(entry.id))
                                put("path", JsonPrimitive(entry.path))
                                put("name", JsonPrimitive(entry.name))
                                put("takenAt", JsonPrimitive(entry.takenAt))
                                put("addedAt", JsonPrimitive(entry.addedAt))
                                put("width", JsonPrimitive(entry.width))
                                put("height", JsonPrimitive(entry.height))
                                put("orientation", JsonPrimitive(entry.orientation))
                                put("mime", JsonPrimitive(entry.mime))
                            },
                        )
                    }
                },
            )
        }.toString()

    /**
     * 解码索引。
     *
     * 参数 text 为磁盘内容；解析失败、版本不符或任一条目字段非法时返回 null，
     * 指针由页面转为重建流程。
     */
    fun decode(text: String): MediaIndexData? =
        runCatching {
            val root = format.parseToJsonElement(text).jsonObject
            val version = root["version"]?.jsonPrimitive?.intOrNull ?: return null
            if (version != VERSION) return null
            val updatedAt = root["updatedAt"]?.jsonPrimitive?.longOrNull ?: return null
            val array: JsonArray = root["entries"]?.jsonArray ?: return null
            val entries = ArrayList<MediaEntry>(array.size)
            for (element in array) {
                val item: JsonObject = element.jsonObject
                entries +=
                    MediaEntry(
                        id = item.requiredLong("id"),
                        path = item.requiredString("path"),
                        name = item.requiredString("name"),
                        takenAt = item.requiredLong("takenAt"),
                        addedAt = item.requiredLong("addedAt"),
                        width = item.requiredInt("width"),
                        height = item.requiredInt("height"),
                        orientation = item.requiredInt("orientation"),
                        mime = item.requiredString("mime"),
                    )
            }
            MediaIndexData(updatedAt = updatedAt, entries = entries)
        }.getOrNull()

    /** 取必填长整型字段，缺失或类型不符时抛出异常并被外层捕获。 */
    private fun JsonObject.requiredLong(key: String): Long = this[key]?.jsonPrimitive?.longOrNull ?: error("缺少字段 $key")

    /** 取必填整型字段。 */
    private fun JsonObject.requiredInt(key: String): Int = this[key]?.jsonPrimitive?.intOrNull ?: error("缺少字段 $key")

    /** 取必填字符串字段，空串视为非法。 */
    private fun JsonObject.requiredString(key: String): String {
        val value = this[key]?.jsonPrimitive?.content ?: error("缺少字段 $key")
        if (value.isBlank()) error("字段 $key 为空")
        return value
    }
}

/**
 * 增量合并。
 *
 * 以绝对路径作为条目身份，路径相同视为同一条目并用新记录替换；id 相同但路径变化
 * 视为重命名，同样以新记录为准。合并结果按时间倒序、路径升序排序。
 */
internal object MediaIndexMerge {
    /**
     * 合并旧索引与新查询结果。
     *
     * 参数 existing 为已保存条目；incoming 为本次查询到的条目。返回去重后的列表，
     * 不修改入参。
     */
    fun merge(
        existing: List<MediaEntry>,
        incoming: List<MediaEntry>,
    ): List<MediaEntry> {
        if (incoming.isEmpty()) return MediaBuckets.sorted(existing)
        val byPath = LinkedHashMap<String, MediaEntry>(existing.size + incoming.size)
        existing.forEach { byPath[it.path] = it }
        incoming.forEach { byPath[it.path] = it }
        val withoutId = ArrayList<MediaEntry>()
        val byId = LinkedHashMap<Long, MediaEntry>()
        for (entry in byPath.values) {
            if (entry.id <= 0L) {
                withoutId += entry
                continue
            }
            val previous = byId[entry.id]
            if (previous == null || entry.time >= previous.time) byId[entry.id] = entry
        }
        return MediaBuckets.sorted(withoutId + byId.values)
    }
}

/**
 * 相册索引。
 *
 * 以 MediaStore.Images 为主来源，查询结果为空的场景（权限不足、媒体库未就绪）退回目录扫描；
 * 索引持久化到应用私有目录的 media-index.json，读取失败或版本不符时丢弃并由 [rebuild] 重建。
 * 查询、扫描与读写磁盘均在 IO 线程执行，对外只返回不可变快照。
 *
 * 参数 context 用于取得内容解析器与文件目录，内部只保留应用上下文。
 */
@Suppress("TooManyFunctions") // 索引、查询、扫描与持久化同属一个存储职责，按模块既有做法抑制函数数量告警。
internal class MediaIndex(
    context: Context,
) {
    private val appContext: Context = context.applicationContext ?: context
    private val indexFile: File = File(appContext.filesDir, INDEX_FILE)
    private val storage: StorageAccess = StorageAccess(appContext)

    @Volatile
    private var current: MediaIndexData? = null

    /** 内存中的最近一次索引，尚未加载或重建时为空。 */
    fun peek(): MediaIndexData? = current

    /** 索引文件当前字节数，文件不存在时为 0，供日志与验收输出。 */
    fun indexSize(): Long = if (indexFile.isFile) indexFile.length() else 0L

    /**
     * 按时间倒序取最近的图片条目。
     *
     * 只读查询：不读取磁盘、不触发重建，索引尚未加载时返回空列表。
     * 参数 limit 为返回条数上限，非正值返回空列表，调用方传入卡片容量即可。
     */
    fun recent(limit: Int): List<MediaEntry> =
        if (limit <= 0) {
            emptyList()
        } else {
            MediaBuckets.sorted(current?.entries.orEmpty().filter { it.isImage }).take(limit)
        }

    /**
     * 读取磁盘索引。
     *
     * 文件不存在、内容损坏或版本不符时删除文件并返回 null，调用方转入重建流程。
     */
    suspend fun loadCached(): MediaIndexData? =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!indexFile.isFile) return@runCatching null
                val text = indexFile.readText()
                val decoded = MediaIndexCodec.decode(text)
                if (decoded == null) {
                    indexFile.delete()
                    InkLog.w(TAG, "索引文件损坏或版本不符，丢弃后重建")
                }
                decoded
            }.getOrElse {
                InkLog.w(TAG, "索引读取失败：${it.message}")
                null
            }.also { current = it }
        }

    /**
     * 全量重建索引。
     *
     * 同时查询媒体库与扫描 Pictures、书籍目录。媒体库缺少刚写入且尚未入库的图片，
     * 仅以媒体库结果为准会漏掉这类文件，因此两路结果按路径合并，媒体库条目优先，
     * 以便保留宽高与拍摄时间。两路均为空时返回空索引，空索引同样写入磁盘，
     * 避免每次启动重复扫描。
     */
    suspend fun rebuild(): MediaIndexData =
        withContext(Dispatchers.IO) {
            val fromStore =
                runCatching { queryStore(null) }.getOrElse {
                    InkLog.w(TAG, "媒体库查询失败：${it.message}")
                    emptyList()
                }
            val scanned =
                runCatching { scanDirectories() }.getOrElse {
                    InkLog.w(TAG, "目录扫描失败：${it.message}")
                    emptyList()
                }
            val entries =
                if (scanned.isEmpty()) {
                    MediaBuckets.sorted(fromStore)
                } else {
                    MediaIndexMerge.merge(scanned, fromStore)
                }
            val data = MediaIndexData(updatedAt = System.currentTimeMillis(), entries = entries)
            current = data
            persist(data)
            data
        }

    /**
     * 增量刷新索引。
     *
     * 参数 sinceMillis 为上次索引完成时间，只查询该时刻之后新增或变更的条目；
     * 查询结果为空时直接返回旧索引，不重写磁盘。旧索引缺失时退化为全量重建。
     */
    suspend fun refresh(sinceMillis: Long): MediaIndexData =
        withContext(Dispatchers.IO) {
            val base = current ?: loadCachedSync()
            if (base == null) return@withContext rebuild()
            val stored =
                runCatching { queryStore(sinceMillis.coerceAtLeast(0L)) }.getOrElse {
                    InkLog.w(TAG, "增量查询失败：${it.message}")
                    emptyList()
                }
            val scanned =
                runCatching { scanDirectories() }
                    .getOrElse {
                        InkLog.w(TAG, "增量扫描失败：${it.message}")
                        emptyList()
                    }.filter { it.addedAt >= sinceMillis.coerceAtLeast(0L) }
            if (stored.isEmpty() && scanned.isEmpty()) return@withContext base
            val incoming = if (scanned.isEmpty()) stored else MediaIndexMerge.merge(scanned, stored)
            val merged = MediaIndexMerge.merge(base.entries, incoming)
            val data = MediaIndexData(updatedAt = System.currentTimeMillis(), entries = merged)
            current = data
            persist(data)
            data
        }

    /** 同步读取磁盘索引，供 refresh 在内存为空时使用，仅在 IO 线程调用。 */
    private fun loadCachedSync(): MediaIndexData? =
        runCatching {
            if (!indexFile.isFile) return@runCatching null
            MediaIndexCodec.decode(indexFile.readText())
        }.getOrNull()?.also { current = it }

    /**
     * 查询 MediaStore.Images。
     *
     * 参数 since 非空时按 DATE_ADDED 过滤；排序优先 DATE_TAKEN，缺失时由读取方回退。
     * 查询异常向上抛出，由调用方决定是否退回目录扫描。
     */
    private fun queryStore(since: Long?): List<MediaEntry> {
        val projection =
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.ORIENTATION,
                MediaStore.Images.Media.MIME_TYPE,
            )
        val selection = if (since == null) null else "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val args = if (since == null) null else arrayOf(((since + 999L) / 1000L).toString())
        val order = "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC"
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val entries = ArrayList<MediaEntry>()
        appContext.contentResolver.query(collection, projection, selection, args, order)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val takenIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val widthIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val orientationIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.ORIENTATION)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            while (cursor.moveToNext()) {
                val path = cursor.getString(dataIndex)
                if (path.isNullOrBlank()) continue
                val addedSeconds = cursor.getLong(addedIndex)
                entries +=
                    MediaEntry(
                        id = cursor.getLong(idIndex),
                        path = path,
                        name = cursor.getString(nameIndex) ?: File(path).name,
                        takenAt = cursor.getLong(takenIndex),
                        addedAt = if (addedSeconds > 0L) addedSeconds * 1000L else 0L,
                        width = cursor.getInt(widthIndex),
                        height = cursor.getInt(heightIndex),
                        orientation = cursor.getInt(orientationIndex),
                        mime = cursor.getString(mimeIndex) ?: mimeOf(path),
                    )
            }
        }
        return entries
    }

    /**
     * 目录扫描兜底。
     *
     * 依次扫描 Pictures 与书籍目录，跳过隐藏目录、`.nomedia` 与超过深度上限的层级；
     * 条目时间取文件修改时间，媒体库不可用时仍能展示缩略图。
     */
    private fun scanDirectories(): List<MediaEntry> {
        val roots = listOf(storage.picturesRoot(), storage.booksRoot()).filter { it.isDirectory }
        val entries = ArrayList<MediaEntry>()
        for (root in roots) {
            walk(root, depth = 0, output = entries)
            if (entries.size >= MAX_SCAN_FILES) break
        }
        return entries
    }

    /** 递归扫描目录，参数 depth 为当前层级，超过上限时停止。 */
    private fun walk(
        directory: File,
        depth: Int,
        output: MutableList<MediaEntry>,
    ) {
        val children = runCatching { directory.listFiles() }.getOrNull()
        val blocked =
            depth > MAX_SCAN_DEPTH ||
                output.size >= MAX_SCAN_FILES ||
                directory.name.startsWith(".") ||
                children == null ||
                children.any { it.name.equals(NOMEDIA, ignoreCase = true) }
        if (blocked) return
        for (child in children) {
            if (output.size >= MAX_SCAN_FILES) break
            if (child.isDirectory) {
                walk(child, depth + 1, output)
            } else if (isScanImage(child.name)) {
                val mtime = child.lastModified()
                output +=
                    MediaEntry(
                        id = 0L,
                        path = child.absolutePath,
                        name = child.name,
                        takenAt = mtime,
                        addedAt = mtime,
                        width = 0,
                        height = 0,
                        orientation = 0,
                        mime = mimeOf(child.name),
                    )
            }
        }
    }

    /** 写入索引文件，先写临时文件再替换，避免中断留下半个文件。 */
    private fun persist(data: MediaIndexData) {
        runCatching {
            val text = MediaIndexCodec.encode(data)
            val temp = File(indexFile.parentFile, "$INDEX_FILE.tmp")
            temp.writeText(text)
            if (indexFile.exists() && !indexFile.delete()) {
                InkLog.w(TAG, "旧索引文件删除失败，继续替换")
            }
            if (!temp.renameTo(indexFile)) {
                temp.copyTo(indexFile, overwrite = true)
                temp.delete()
            }
        }.onFailure {
            InkLog.w(TAG, "索引写入失败：${it.message}")
        }
    }

    /** 按扩展名判断是否为可索引图片。 */
    private fun isScanImage(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    /** 按扩展名推断 MIME，无法识别时返回 image 前缀以保证进入查看器。 */
    private fun mimeOf(path: String): String =
        when (path.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "gif" -> "image/gif"
            "heic", "heif" -> "image/heif"
            else -> "image/*"
        }

    private companion object {
        private const val TAG = "相册索引"
        private const val INDEX_FILE = "media-index.json"
        private const val NOMEDIA = ".nomedia"
        private const val MAX_SCAN_DEPTH = 6
        private const val MAX_SCAN_FILES = 5000
        private val IMAGE_EXTENSIONS =
            setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif")
    }
}
