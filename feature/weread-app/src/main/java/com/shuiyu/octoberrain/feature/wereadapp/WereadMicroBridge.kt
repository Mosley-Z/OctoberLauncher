package com.shuiyu.octoberrain.feature.wereadapp

import android.content.Context
import com.inkread.weekread.kernel.PeriodSnapshot
import com.shuiyu.octoberrain.core.common.AppServices
import java.io.File

/**
 * 复用的微读 Java 控件与十雨数据层之间的桥接入口。
 *
 * 复制的微读控件仍保留对自有 SharedPreferences 的调用（API Key 是否配置、墨台是否启用、
 * 本记条数与落盘时间），这些调用会绕过十雨的 [WereadAppData] 与内核偏好。本对象把读取
 * 统一转发到十雨现有数据入口：API Key 与墨台开关取 [AppServices.config]，本记进度与落盘
 * 时刻暂不提供（返回空值，界面按“未知”处理），后续若需要精确进度再补实现。
 */
@Suppress("TooManyFunctions")
object WereadMicroBridge {
    /** 缓存的数据入口，避免卡片每帧重绘时重复读盘。 */
    private var cachedData: WereadAppData? = null

    /** 缓存对应的应用上下文。 */
    private var cachedContext: Context? = null

    /**
     * 返回应用级的数据入口，首次调用时创建并缓存。
     *
     * @param context 任意上下文，内部会取 applicationContext 避免持有页面引用
     */
    private fun data(context: Context): WereadAppData {
        val app = context.applicationContext ?: context
        val current = cachedData
        if (current != null && cachedContext === app) return current
        return WereadAppData(app).also {
            cachedData = it
            cachedContext = app
        }
    }

    /**
     * 返回已配置的微信读书 API Key，未配置时返回空串。
     *
     * 微读卡片用它判断是否画“未配置密钥”的引导文案，十雨侧密钥保存在启动器配置里，
     * 因此直接从 [AppServices.config] 读取。
     *
     * @param context 调用方上下文，当前实现不使用，保留以匹配 Java 调用点
     */
    @JvmStatic
    @Suppress("UnusedParameter")
    fun apiKey(context: Context): String =
        runCatching { AppServices.config.state.value.integrations.wereadApiKey }
            .getOrNull()
            .orEmpty()

    /**
     * 按「只统计书架上的书」开关过滤本月排行条目。
     *
     * 关闭开关或取不到书架时原样返回；开启且书架就绪时只保留书架内的书目。
     *
     * @param context 调用方上下文
     * @param source 统计回包里的排行列表，可为 null
     * @return 过滤后的排行列表，入参为 null 时返回 null
     */
    @JvmStatic
    @Suppress("ReturnCount")
    fun rankItems(
        context: Context,
        source: List<PeriodSnapshot.Longest>?,
    ): List<PeriodSnapshot.Longest>? {
        if (source == null) return null
        val shelfOnly = runCatching { WereadAppPrefs.settings(context).rankShelfOnly }.getOrDefault(false)
        if (!shelfOnly) return source
        val ids = runCatching { data(context).shelf()?.ids }.getOrNull() ?: return source
        return source.filter { it.bookId in ids }
    }

    /**
     * 记录本记池的「第 N / 共 M 条」进度。
     *
     * 复制进来的微读卡片由内核绘制时通过 [noteProgress] 读取进度，而真实池由页面按
     * 筛选条件临时计算；因此页面每次绑定本记卡片时调用本方法，把当前序号与总数写入
     * 十雨的偏好文件（`kernel_prefs.json`），供卡片重绘时读取。
     *
     * @param context 调用方上下文
     * @param slot 本记池槽位（0 默认 / 1 只看想法）
     * @param taken 当前条序号，从 1 起；池为空时传 0
     * @param total 池内总条数；池为空时传 0
     */
    @JvmStatic
    fun recordNoteProgress(
        context: Context,
        slot: Int,
        taken: Int,
        total: Int,
    ) {
        runCatching {
            val store = WereadAppPrefs.store(context)
            store.putInt(KEY_NOTE_TAKEN_PREFIX + slot, taken)
            store.putInt(KEY_NOTE_TOTAL_PREFIX + slot, total)
            store.flush()
        }
    }

    /**
     * 返回本记池的「第 N / 共 M 条」进度。
     *
     * 数据来自 [recordNoteProgress] 写入的偏好键；池为空或尚未记录时返回 null，
     * 渲染层据此不画进度行。返回值第一项已夹到 1..M，避免历史记录越界。
     *
     * @param context 调用方上下文
     * @param slot 本记池槽位（0 默认 / 1 只看想法 / 2 选书）
     * @return 长度为 2 的数组（当前序号、总数），无记录返回 null
     */
    @JvmStatic
    fun noteProgress(
        context: Context,
        slot: Int,
    ): IntArray? {
        val total =
            runCatching { WereadAppPrefs.store(context).getInt(KEY_NOTE_TOTAL_PREFIX + slot, 0) }
                .getOrDefault(0)
        if (total <= 0) return null
        val taken =
            runCatching { WereadAppPrefs.store(context).getInt(KEY_NOTE_TAKEN_PREFIX + slot, 1) }
                .getOrDefault(1)
        return intArrayOf(taken.coerceIn(1, total), total)
    }

    /**
     * 返回指定书目笔记的落盘时刻（毫秒），未知返回 0。
     *
     * 十雨同步服务把每本书的笔记整体写入 `files/weread/nt_<bookId>.json`，文件修改时间
     * 即最近一次真正拉回数据的时刻；文件不存在或时间不可读时返回 0，界面据此不画
     * 「更新于」。与微读「划线、想法两路缓存取较新者」的口径一致。
     *
     * @param context 调用方上下文
     * @param bookId 书目编号
     * @return 落盘时刻毫秒值，无文件返回 0
     */
    @JvmStatic
    fun noteFetchedAt(
        context: Context,
        bookId: String,
    ): Long {
        if (bookId.isEmpty()) return 0L
        return runCatching {
            val file = File(WereadAppPrefs.dataDir(context), "$NOTE_FILE_PREFIX$bookId$NOTE_FILE_SUFFIX")
            if (file.isFile) file.lastModified() else 0L
        }.getOrDefault(0L)
    }

    /**
     * 返回本记导出图的纸张底纹档位（0 素白 / 1 手账 / 2 卡纸）。
     *
     * 微读导出实现从自有偏好读取；十雨把同一组选择保存在 `kernel_prefs.json`，
     * 由设置页写入，这里转发给复制的 [com.inkread.weekread.feature.NoteExport]。
     *
     * @param context 调用方上下文
     */
    @JvmStatic
    fun exportPaper(context: Context): Int =
        runCatching { WereadAppPrefs.settings(context).exportTemplate }.getOrDefault(1).coerceIn(0, 2)

    /**
     * 返回本记导出图与本记页共用的字号档位（0 小 / 1 中 / 2 大）。
     *
     * @param context 调用方上下文
     */
    @JvmStatic
    fun exportSizeTier(context: Context): Int =
        runCatching { WereadAppPrefs.settings(context).exportSize }.getOrDefault(1).coerceIn(0, 2)

    /**
     * 返回导出图是否绘制署名（书名 · 作者）。
     *
     * @param context 调用方上下文
     */
    @JvmStatic
    fun exportShowSign(context: Context): Boolean =
        runCatching { WereadAppPrefs.settings(context).noteSign }.getOrDefault(true)

    /**
     * 返回导出图是否绘制章节与划线日期。
     *
     * @param context 调用方上下文
     */
    @JvmStatic
    fun exportShowDate(context: Context): Boolean =
        runCatching { WereadAppPrefs.settings(context).noteDate }.getOrDefault(true)

    /** 本记进度写入的偏好键前缀，后接槽位编号。 */
    private const val KEY_NOTE_TAKEN_PREFIX: String = "nt_taken_slot"

    /** 本记总数写入的偏好键前缀，后接槽位编号。 */
    private const val KEY_NOTE_TOTAL_PREFIX: String = "nt_total_slot"

    /** 单书笔记文件名前缀，后接书目编号。 */
    private const val NOTE_FILE_PREFIX: String = "nt_"

    /** 单书笔记文件名后缀。 */
    private const val NOTE_FILE_SUFFIX: String = ".json"
}
