package com.shuiyu.octoberrain.core.wereadsync

import com.shuiyu.octoberrain.core.common.InkLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File

/**
 * 微读书架的单个书目。
 *
 * 字段与微读原版 `shelf.json` 的 `books` 数组元素同义，写入时按原版键名落盘；
 * 阅读时长单位为秒，进度为 0 到 100 的整数，负值表示未知。
 */
data class WereadShelfBook(
    val bookId: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val readingSeconds: Int,
    val progress: Int,
    val finished: Boolean,
    val finishTimeMillis: Long,
)

/**
 * 微读阅读资产的单个书目。
 *
 * 阅读时长单位为秒，价格单位为分；progress 为 0 到 100 的整数。
 */
data class WereadAssetBook(
    val bookId: String,
    val title: String,
    val author: String,
    val readingSeconds: Int,
    val priceFen: Int,
    val paidFen: Int,
    val progress: Int,
    val finished: Boolean,
    val finishTimeMillis: Long = 0L,
)

/**
 * 微读笔记的单篇记录。
 *
 * name 为落盘文件名，内容为原版 `nt_<id>.json` 或 `ni_<id>.json` 的原始文本。
 */
data class WereadNoteFile(
    val name: String,
    val rawJson: String,
)

/**
 * 把网关与客户端结果写成与微读原版同构的数据目录。
 *
 * 目录默认是 `filesDir/weread`，文件名与语义对齐 `KernelDataLoader`：`shelf.json`、
 * `shelf_ids.json`、`book.json`、`stats.json`、`bills.json`、`assets.json`、
 * `nt_index.json`、`kernel_prefs.json`。写入采用临时文件替换，避免断电留下半个文件。
 *
 * 写入方法按原盘文件逐个展开，便于把每类数据的键名与内核读取点一一对应，函数数量
 * 随文件类型增长，对函数数量度量局部豁免。
 *
 * @param root 数据目录，不存在时首次写入自动创建
 */
@Suppress("TooManyFunctions")
class WereadStore(
    private val root: File,
) {
    /**
     * 写入书架。
     *
     * @param books 书目列表
     * @param savedAtMillis 抓取时刻，写入 `savedAt` 供内核判断新鲜度
     */
    fun writeShelf(
        books: List<WereadShelfBook>,
        savedAtMillis: Long,
    ) {
        val array =
            buildJsonArray {
                books.forEach { book ->
                    add(
                        buildJsonObject {
                            put("bookId", JsonPrimitive(book.bookId))
                            put("title", JsonPrimitive(book.title))
                            put("author", JsonPrimitive(book.author))
                            put("cover", JsonPrimitive(book.coverUrl))
                            put("readingTime", JsonPrimitive(book.readingSeconds))
                            put("progress", JsonPrimitive(book.progress))
                            put("finished", JsonPrimitive(book.finished))
                            put("finishTime", JsonPrimitive(book.finishTimeMillis))
                        },
                    )
                }
            }
        writeShelfDocument(array, savedAtMillis)
    }

    /**
     * 写入书架标识列表，供内核按用户排序展示。
     *
     * @param ids 书目标识，顺序即展示顺序
     */
    fun writeShelfIds(ids: List<String>) {
        val array = buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } }
        writeText(FILE_SHELF_IDS, buildJsonObject { put("ids", array) }.toString())
    }

    /**
     * 写入当前在读书目，未在阅读时清除该文件。
     *
     * @param book 当前书目，空值表示回到书架
     */
    fun writeCurrentBook(book: WereadShelfBook?) {
        if (book == null) {
            fileOf(FILE_BOOK).delete()
            return
        }
        val document =
            buildJsonObject {
                put("bookId", JsonPrimitive(book.bookId))
                put("title", JsonPrimitive(book.title))
                put("author", JsonPrimitive(book.author))
                put("coverUrl", JsonPrimitive(book.coverUrl))
                put("readingSec", JsonPrimitive(book.readingSeconds))
                put("progress", JsonPrimitive(book.progress))
                put("finished", JsonPrimitive(book.finished))
                put("finishTime", JsonPrimitive(book.finishTimeMillis))
            }
        writeText(FILE_BOOK, document.toString())
    }

    /**
     * 写入某一周期的统计快照。
     *
     * 周与月写入 `d_<mode>:<baseTimeSec>` 与 `t_<mode>:<baseTimeSec>`；年度写入
     * `stats_annual_<year>`；全时段写入 `stats_overall`。原始 JSON 同时写入
     * `stats.json` 作为内核的回退来源。
     *
     * @param mode 周期类型，取 weekly、monthly、annually、overall
     * @param baseTimeSec 周期起点秒数，全时段传 0
     * @param rawJson 网关返回的原始统计 JSON
     * @param fetchedAtMillis 抓取时刻
     */
    fun writeStats(
        mode: String,
        baseTimeSec: Long,
        rawJson: String,
        fetchedAtMillis: Long,
    ) {
        val prefs = readPrefs()
        val strings = mutableMapOf<String, String>()
        val longs = mutableMapOf<String, Long>()
        val statsKey: String
        val timeKey: String
        when (mode) {
            MODE_OVERALL -> {
                statsKey = MODE_OVERALL
                timeKey = KEY_STATS_OVERALL_TIME
            }
            MODE_ANNUALLY -> {
                val year = yearOf(baseTimeSec)
                statsKey = "$KEY_STATS_ANNUAL_PREFIX$year"
                timeKey = "$KEY_STATS_ANNUAL_TIME_PREFIX$year"
            }
            else -> {
                val normalized = if (mode == MODE_MONTHLY) MODE_MONTHLY else MODE_WEEKLY
                statsKey = "d_$normalized:$baseTimeSec"
                timeKey = "t_$normalized:$baseTimeSec"
            }
        }
        strings[statsKey] = rawJson
        longs[timeKey] = fetchedAtMillis
        savePrefs(prefs.merge(strings = strings, longs = longs))
        mergeStatsFile(statsKey, rawJson)
    }

    /**
     * 写入某一期账单。
     *
     * @param mode 周期类型，取 weekly 或 monthly
     * @param periodStartSec 周期起点秒数
     * @param sheetJson 账单快照的原始 JSON
     */
    fun writeBill(
        mode: String,
        periodStartSec: Long,
        sheetJson: String,
    ) {
        val normalized = if (mode == MODE_MONTHLY) MODE_MONTHLY else MODE_WEEKLY
        val key = "bill_${normalized}_$periodStartSec"
        savePrefs(readPrefs().merge(strings = mapOf(key to sheetJson)))
        mergeBillFile(key, sheetJson)
    }

    /**
     * 写入阅读资产书目。
     *
     * @param books 资产条目，按累计时长降序由调用方排好
     */
    fun writeAssets(books: List<WereadAssetBook>) {
        val array =
            buildJsonArray {
                books.forEach { book ->
                    add(
                        buildJsonObject {
                            put("id", JsonPrimitive(book.bookId))
                            put("title", JsonPrimitive(book.title))
                            put("author", JsonPrimitive(book.author))
                            put("sec", JsonPrimitive(book.readingSeconds))
                            put("pct", JsonPrimitive(book.progress))
                            // 读完标记优先于完成时刻：网关常把 finishTime 置零，
                            // 内核只认 fin 是否大于零，故读完时至少写 1。
                            put("fin", JsonPrimitive(if (book.finished) maxOf(book.finishTimeMillis, 1L) else 0L))
                        },
                    )
                }
            }
        writeText(FILE_ASSETS, buildJsonObject { put("books", array) }.toString())
    }

    /**
     * 写入笔记索引与逐篇文件。
     *
     * @param indexJson 笔记索引原始 JSON
     * @param notes 逐篇笔记文件
     */
    fun writeNotes(
        indexJson: String,
        notes: List<WereadNoteFile>,
    ) {
        writeText(FILE_NOTE_INDEX, indexJson)
        notes.forEach { note -> writeText(note.name, note.rawJson) }
    }

    /**
     * 写入一条字符串偏好，用于密钥、菜单与缓存键。
     *
     * @param key 偏好键
     * @param value 偏好值
     */
    fun putString(
        key: String,
        value: String,
    ) {
        savePrefs(readPrefs().merge(strings = mapOf(key to value)))
    }

    /**
     * 写入一条整数偏好。
     *
     * @param key 偏好键
     * @param value 偏好值
     */
    fun putInt(
        key: String,
        value: Int,
    ) {
        savePrefs(readPrefs().merge(ints = mapOf(key to value)))
    }

    /**
     * 写入一条布尔偏好。
     *
     * @param key 偏好键
     * @param value 偏好值
     */
    fun putBool(
        key: String,
        value: Boolean,
    ) {
        savePrefs(readPrefs().merge(booleans = mapOf(key to value)))
    }

    /**
     * 读取一条字符串偏好，缺失时返回空值。
     *
     * @param key 偏好键
     * @return 偏好值或空值
     */
    fun getString(key: String): String? = readPrefs().strings[key]?.jsonPrimitive?.contentOrNull

    /**
     * 写入一条长整数偏好。
     *
     * @param key 偏好键
     * @param value 偏好值
     */
    fun putLong(
        key: String,
        value: Long,
    ) {
        savePrefs(readPrefs().merge(longs = mapOf(key to value)))
    }

    /**
     * 读取一条长整数偏好，缺失时返回空值。
     *
     * @param key 偏好键
     * @return 偏好值或空值
     */
    fun getLong(key: String): Long? = readPrefs().longs[key]?.jsonPrimitive?.longOrNull

    /** 当前数据目录是否已经存在内核可读取的书架数据。 */
    fun hasShelf(): Boolean {
        val books = readJsonObject(fileOf(FILE_SHELF))?.get("books") as? JsonArray
        return books?.isNotEmpty() == true
    }

    private fun writeShelfDocument(
        books: JsonArray,
        savedAtMillis: Long,
    ) {
        val document =
            buildJsonObject {
                put("books", books)
                put("savedAt", JsonPrimitive(savedAtMillis))
            }
        writeText(FILE_SHELF, document.toString())
    }

    private fun mergeStatsFile(
        key: String,
        rawJson: String,
    ) {
        val current = readJsonObject(fileOf(FILE_STATS)) ?: JsonObject(emptyMap())
        val merged = JsonObject(current.toMutableMap().apply { put(key, JsonPrimitive(rawJson)) })
        writeText(FILE_STATS, merged.toString())
    }

    private fun mergeBillFile(
        key: String,
        sheetJson: String,
    ) {
        val current = readJsonObject(fileOf(FILE_BILLS)) ?: JsonObject(emptyMap())
        val merged = JsonObject(current.toMutableMap().apply { put(key, JsonPrimitive(sheetJson)) })
        writeText(FILE_BILLS, merged.toString())
    }

    private fun readPrefs(): PrefsDocument {
        val document = readJsonObject(fileOf(FILE_PREFS)) ?: return PrefsDocument()
        return PrefsDocument(
            strings = readGroup(document, GROUP_STRING),
            longs = readGroup(document, GROUP_LONG),
            ints = readGroup(document, GROUP_INT),
            booleans = readGroup(document, GROUP_BOOL),
        )
    }

    private fun readGroup(
        document: JsonObject,
        group: String,
    ): Map<String, JsonElement> {
        val node = document[group] as? JsonObject ?: return emptyMap()
        return node.toMap()
    }

    private fun savePrefs(prefs: PrefsDocument) {
        val document =
            buildJsonObject {
                put(GROUP_STRING, JsonObject(prefs.strings))
                put(GROUP_LONG, JsonObject(prefs.longs))
                put(GROUP_INT, JsonObject(prefs.ints))
                put(GROUP_BOOL, JsonObject(prefs.booleans))
            }
        writeText(FILE_PREFS, document.toString())
    }

    private fun readJsonObject(file: File): JsonObject? {
        if (!file.isFile) return null
        return runCatching { Json.parseToJsonElement(file.readText()).jsonObject }.getOrNull()
    }

    private fun writeText(
        name: String,
        text: String,
    ) {
        runCatching {
            if (!root.exists() && !root.mkdirs()) {
                InkLog.w(TAG, "微读数据目录创建失败：${root.absolutePath}")
                return@runCatching
            }
            val target = fileOf(name)
            val temp = File(root, "$name.tmp")
            temp.writeText(text)
            if (!temp.renameTo(target)) {
                target.delete()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
            }
        }.onFailure { error -> InkLog.w(TAG, "写入 $name 失败：${error.message}") }
    }

    private fun fileOf(name: String): File = File(root, name)

    /** 解析周期起点所在年份。 */
    private fun yearOf(baseTimeSec: Long): Int {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = baseTimeSec * 1000L
        return calendar.get(java.util.Calendar.YEAR)
    }

    /** 偏好文件的分组内容，写回时保持四组结构。 */
    private data class PrefsDocument(
        val strings: Map<String, JsonElement> = emptyMap(),
        val longs: Map<String, JsonElement> = emptyMap(),
        val ints: Map<String, JsonElement> = emptyMap(),
        val booleans: Map<String, JsonElement> = emptyMap(),
    ) {
        /**
         * 合并新写入的键值，返回新文档。
         *
         * @param strings 新增字符串键值
         * @param longs 新增长整数键值
         * @param ints 新增整数键值
         * @param booleans 新增布尔键值
         * @return 合并后的文档
         */
        fun merge(
            strings: Map<String, String> = emptyMap(),
            longs: Map<String, Long> = emptyMap(),
            ints: Map<String, Int> = emptyMap(),
            booleans: Map<String, Boolean> = emptyMap(),
        ): PrefsDocument =
            copy(
                strings = this.strings + strings.mapValues { JsonPrimitive(it.value) },
                longs = this.longs + longs.mapValues { JsonPrimitive(it.value) },
                ints = this.ints + ints.mapValues { JsonPrimitive(it.value) },
                booleans = this.booleans + booleans.mapValues { JsonPrimitive(it.value) },
            )
    }

    companion object {
        private const val TAG = "WereadStore"

        /** 书架文件。 */
        const val FILE_SHELF: String = "shelf.json"

        /** 书架标识文件。 */
        const val FILE_SHELF_IDS: String = "shelf_ids.json"

        /** 当前书目文件。 */
        const val FILE_BOOK: String = "book.json"

        /** 统计回退文件。 */
        const val FILE_STATS: String = "stats.json"

        /** 账单文件。 */
        const val FILE_BILLS: String = "bills.json"

        /** 阅读资产文件。 */
        const val FILE_ASSETS: String = "assets.json"

        /** 笔记索引文件。 */
        const val FILE_NOTE_INDEX: String = "nt_index.json"

        /** 内核键值文件。 */
        const val FILE_PREFS: String = "kernel_prefs.json"

        /** 键值文件的四个分组名。 */
        const val GROUP_STRING: String = "string"
        const val GROUP_LONG: String = "long"
        const val GROUP_INT: String = "int"
        const val GROUP_BOOL: String = "bool"

        /** 周期类型，与内核 PeriodCalendar 常量一致。 */
        const val MODE_WEEKLY: String = "weekly"
        const val MODE_MONTHLY: String = "monthly"
        const val MODE_ANNUALLY: String = "annually"
        const val MODE_OVERALL: String = "overall"

        private const val KEY_STATS_OVERALL_TIME = "stats_overall_t"
        private const val KEY_STATS_ANNUAL_PREFIX = "stats_annual_"
        private const val KEY_STATS_ANNUAL_TIME_PREFIX = "stats_annual_t_"
    }
}
