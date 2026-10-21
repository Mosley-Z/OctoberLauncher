package com.shuiyu.octoberrain.feature.wereadapp

import android.content.Context
import com.inkread.weekread.kernel.KernelStore
import java.io.File

/**
 * 设置内微读墨记页面的本地偏好。
 *
 * 偏好与内核共用 `filesDir/weread/kernel_prefs.json`，读写经 [KernelStore] 完成，
 * 保证设置页写入的值与桌面卡片、负一屏读取的位置一致。
 */
@Suppress("TooManyFunctions")
internal object WereadAppPrefs {
    /** 微读数据目录名，与内核宿主保持一致。 */
    const val DATA_DIRECTORY: String = "weread"

    /** 纸色档位偏好键，取值 0 白、1 米、2 灰。 */
    const val KEY_PAPER_COLOR: String = "paper"

    /** 正文字号档位偏好键，取值 0 小、1 中、2 大。 */
    const val KEY_BODY_SIZE: String = "nsize"

    /** 是否显示署名与日期。 */
    const val KEY_SHOW_SIGNATURE: String = "note_sign"

    /** 是否显示笔记日期，与署名开关分开控制。 */
    const val KEY_SHOW_DATE: String = "note_date"

    /** 仅一页模式，翻页不再隐藏卡片。 */
    const val KEY_SINGLE_PAGE_MODE: String = "single_page_mode"

    /** 启用墨台。 */
    const val KEY_DESK_ENABLED: String = "cb_desk_enabled"

    /** 刷新绑定：点「更新于…」一并刷新本周。 */
    const val KEY_BIND_WEEK: String = "bind_week"

    /** 刷新绑定：点「更新于…」一并刷新本月。 */
    const val KEY_BIND_MONTH: String = "bind_month"

    /** 刷新绑定：点「更新于…」一并刷新本书。 */
    const val KEY_BIND_BOOK: String = "bind_book"

    /** 阅读成就提示开关。 */
    const val KEY_ACHV: String = "cb_achv"

    /** 成就周目标档位，0 关、1 完美、2 狂暴、3 自定义。 */
    const val KEY_ACHV_WEEK_KIND: String = "achv_week_kind"

    /** 成就周目标自定义值，单位为小时。 */
    const val KEY_ACHV_WEEK_CUSTOM: String = "achv_week_custom"

    /** 成就月目标档位，0 关、1 完美、2 狂暴、3 自定义。 */
    const val KEY_ACHV_MONTH_KIND: String = "achv_month_kind"

    /** 成就月目标自定义值，单位为小时。 */
    const val KEY_ACHV_MONTH_CUSTOM: String = "achv_month_custom"

    /** 本记导出图纸张底纹档位，0 素白、1 手账、2 卡纸。 */
    const val KEY_EXPORT_TEMPLATE: String = "export_tpl"

    /** 本记导出图纸张纹理，与模板档位同步写入。 */
    const val KEY_EXPORT_PAPER: String = "paper"

    /** 榜单是否只取书架书目，键名与微读墨记偏好一致。 */
    const val KEY_RANK_SHELF_ONLY: String = "rank_shelf_only"

    /** 月历是否使用热力图样式，false 为打卡标记。 */
    const val KEY_MONTH_STYLE: String = "month_style"

    /** 卡片切换方式，0 循环模式、1 列表模式；键名与微读墨记偏好一致。 */
    const val KEY_SWITCH_MODE: String = "switch_mode"

    /** 卡片档本记正文字号，单位为号。 */
    const val KEY_NOTE_CARD_SIZE: String = "note_card_size"

    /** 更新渠道，0 稳定、1 测试。 */
    const val KEY_UPDATE_CHANNEL: String = "update_channel"

    /** 云端回退开关。 */
    const val KEY_UPD_CLOUD_FALLBACK: String = "upd_cloud_fallback"

    /** 安装角色，0 阅读器、1 手机。 */
    const val KEY_INSTALL_ROLE: String = "install_role"

    /** 全屏档本记正文字号三档，按档位索引取值。 */
    private val NOTE_FULL_SIZES: IntArray = intArrayOf(15, 17, 19)

    /** 读取微读数据目录，首次使用时创建。 */
    fun dataDir(context: Context): File =
        File(context.applicationContext.filesDir, DATA_DIRECTORY).apply {
            if (!exists()) mkdirs()
        }

    /** 创建偏好存储，构造时读取磁盘内容。 */
    fun store(context: Context): KernelStore = KernelStore(dataDir(context))

    /** 读取本应用版本名，读取失败时返回空串。 */
    fun versionName(context: Context): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        }.getOrDefault("")

    /** 读取本应用版本号，读取失败时返回 0。 */
    fun versionCode(context: Context): Long =
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        }.getOrDefault(0L)

    /** 月历是否使用热力图样式。 */
    fun monthHeatmap(context: Context): Boolean = store(context).getInt(KEY_MONTH_STYLE, 0) == 1

    /** 全屏档本记正文的号值，按字号档位换算。 */
    fun noteFullSize(context: Context): Int {
        val level = store(context).getInt(KEY_BODY_SIZE, BODY_SIZE_DEFAULT).coerceIn(0, NOTE_FULL_SIZES.lastIndex)
        return NOTE_FULL_SIZES[level]
    }

    /** 纸色档位，缺省米色。 */
    fun paperColor(context: Context): Int = store(context).getInt(KEY_PAPER_COLOR, EXPORT_TEMPLATE_DEFAULT)

    /** 读取设置页表单的全部取值。 */
    fun settings(context: Context): WereadAppSettings {
        val store = store(context)
        return WereadAppSettings(
            installRole = store.getInt(KEY_INSTALL_ROLE, INSTALL_ROLE_DEFAULT),
            singlePage = store.getBool(KEY_SINGLE_PAGE_MODE, false),
            deskEnabled = store.getBool(KEY_DESK_ENABLED, true),
            bindWeek = store.getBool(KEY_BIND_WEEK, store.getInt("bind_targets", 5) and 1 != 0),
            bindMonth = store.getBool(KEY_BIND_MONTH, store.getInt("bind_targets", 5) and 2 != 0),
            bindBook = store.getBool(KEY_BIND_BOOK, store.getInt("bind_targets", 5) and 4 != 0),
            achievementEnabled = store.getBool(KEY_ACHV, false),
            weekKind = store.getInt(KEY_ACHV_WEEK_KIND, 0).coerceIn(0, 3),
            weekCustom = store.getString(KEY_ACHV_WEEK_CUSTOM, ""),
            monthKind = store.getInt(KEY_ACHV_MONTH_KIND, 0).coerceIn(0, 3),
            monthCustom = store.getString(KEY_ACHV_MONTH_CUSTOM, ""),
            exportTemplate = store.getInt(KEY_EXPORT_TEMPLATE, EXPORT_TEMPLATE_DEFAULT).coerceIn(0, 2),
            exportSize = store.getInt(KEY_BODY_SIZE, BODY_SIZE_DEFAULT).coerceIn(0, 2),
            noteSign = store.getBool(KEY_SHOW_SIGNATURE, true),
            noteDate = store.getBool(KEY_SHOW_DATE, true),
            rankShelfOnly = store.getBool(KEY_RANK_SHELF_ONLY, false),
            monthHeatmap = store.getInt(KEY_MONTH_STYLE, 0) == 1,
            noteCardSize = store.getInt(KEY_NOTE_CARD_SIZE, NOTE_CARD_SIZE_DEFAULT),
            switchMode = store.getInt(KEY_SWITCH_MODE, 0).coerceIn(0, 1),
            updateChannel = store.getInt(KEY_UPDATE_CHANNEL, 0).coerceIn(0, 1),
            cloudFallback = store.getBool(KEY_UPD_CLOUD_FALLBACK, true),
        )
    }

    /**
     * 修改设置后立即落盘。
     *
     * 参数 transform 接收当前值并返回新值，写入同一份偏好存储后刷新磁盘。
     */
    fun update(
        context: Context,
        transform: (WereadAppSettings) -> WereadAppSettings,
    ) {
        val next = transform(settings(context))
        val store = store(context)
        store.putInt(KEY_INSTALL_ROLE, next.installRole)
        store.putBool(KEY_SINGLE_PAGE_MODE, next.singlePage)
        store.putBool(KEY_DESK_ENABLED, next.deskEnabled)
        store.putBool(KEY_BIND_WEEK, next.bindWeek)
        store.putBool(KEY_BIND_MONTH, next.bindMonth)
        store.putBool(KEY_BIND_BOOK, next.bindBook)
        store.putInt(
            "bind_targets",
            (if (next.bindWeek) 1 else 0) or (if (next.bindMonth) 2 else 0) or (if (next.bindBook) 4 else 0),
        )
        store.putBool(KEY_ACHV, next.achievementEnabled)
        store.putInt(KEY_ACHV_WEEK_KIND, next.weekKind)
        store.putString(KEY_ACHV_WEEK_CUSTOM, next.weekCustom)
        store.putInt(KEY_ACHV_MONTH_KIND, next.monthKind)
        store.putString(KEY_ACHV_MONTH_CUSTOM, next.monthCustom)
        store.putInt(KEY_EXPORT_TEMPLATE, next.exportTemplate)
        store.putInt(KEY_EXPORT_PAPER, next.exportTemplate)
        store.putInt(KEY_BODY_SIZE, next.exportSize)
        store.putBool(KEY_SHOW_SIGNATURE, next.noteSign)
        store.putBool(KEY_SHOW_DATE, next.noteDate)
        store.putBool(KEY_RANK_SHELF_ONLY, next.rankShelfOnly)
        store.putInt(KEY_MONTH_STYLE, if (next.monthHeatmap) 1 else 0)
        store.putInt(KEY_NOTE_CARD_SIZE, next.noteCardSize)
        store.putInt(KEY_SWITCH_MODE, next.switchMode)
        store.putInt(KEY_UPDATE_CHANNEL, next.updateChannel)
        store.putBool(KEY_UPD_CLOUD_FALLBACK, next.cloudFallback)
        store.flush()
    }

    /** 安装角色缺省值，阅读器端。 */
    private const val INSTALL_ROLE_DEFAULT: Int = 0

    /** 导出图纸张底纹缺省档位，手账。 */
    private const val EXPORT_TEMPLATE_DEFAULT: Int = 1

    /** 正文字号缺省档位，中号。 */
    private const val BODY_SIZE_DEFAULT: Int = 1

    /** 卡片档本记字号缺省值。 */
    private const val NOTE_CARD_SIZE_DEFAULT: Int = 17
}

/**
 * 设置页两页签表单的取值快照。
 *
 * 字段与内核偏好键一一对应；写入时由 [WereadAppPrefs.update] 统一落盘。
 */
internal data class WereadAppSettings(
    /** 安装角色，0 阅读器端、1 手机端。 */
    val installRole: Int = 0,
    /** 仅一页模式，翻页不再隐藏卡片。 */
    val singlePage: Boolean = false,
    /** 启用墨台。 */
    val deskEnabled: Boolean = true,
    /** 刷新绑定本周。 */
    val bindWeek: Boolean = true,
    /** 刷新绑定本月。 */
    val bindMonth: Boolean = false,
    /** 刷新绑定本书。 */
    val bindBook: Boolean = true,
    /** 阅读成就提示开关。 */
    val achievementEnabled: Boolean = false,
    /** 成就周目标档位，0 关、1 完美、2 狂暴、3 自定义。 */
    val weekKind: Int = 0,
    /** 成就周目标自定义值，单位为小时。 */
    val weekCustom: String = "",
    /** 成就月目标档位，0 关、1 完美、2 狂暴、3 自定义。 */
    val monthKind: Int = 0,
    /** 成就月目标自定义值，单位为小时。 */
    val monthCustom: String = "",
    /** 本记导出图纸张底纹档位，0 素白、1 手账、2 卡纸。 */
    val exportTemplate: Int = 1,
    /** 本记导出图字号档位，0 小、1 中、2 大。 */
    val exportSize: Int = 1,
    /** 导出图是否显示署名。 */
    val noteSign: Boolean = true,
    /** 导出图是否显示章节与划线日期。 */
    val noteDate: Boolean = true,
    /** 榜单是否只统计书架上的书。 */
    val rankShelfOnly: Boolean = false,
    /** 月历是否使用热力图。 */
    val monthHeatmap: Boolean = false,
    /** 卡片档本记正文字号。 */
    val noteCardSize: Int = 17,
    /** 卡片切换方式，0 循环模式、1 列表模式；由 App 层读取。 */
    val switchMode: Int = 0,
    /** 更新渠道，0 稳定、1 测试；由十雨桌面更新逻辑读取。 */
    val updateChannel: Int = 0,
    /** 云端备用更新源开关；由十雨桌面更新逻辑读取。 */
    val cloudFallback: Boolean = true,
)
