package com.shuiyu.octoberrain.core.common

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 微读墨记外观偏好在十雨内的只读视图。
 *
 * 内置微读墨记页面的设置项写入 `filesDir/weread/kernel_prefs.json` 的 int 组与 bool 组，
 * 桌面卡片与负一屏需要按同一份取值渲染。本类只做读取，写入仍由
 * [com.shuiyu.octoberrain.core.wereadsync.WereadStore] 与内置页面负责。
 *
 * @property paperColor 纸色档位，0 为素白，1 为米色，2 为暖灰
 * @property bodySize 正文字号，单位与内核 noteFullSize 一致
 * @property noteCardSize 卡片内本记字号
 * @property showSignature 是否显示署名行
 * @property monthHeatmap 月历是否使用热力格
 * @property rankShelfOnly 阅读排行是否只取书架书目
 * @property achievementEnabled 是否显示成就
 * @property achievementWeekKind 周目标档位，0 关、1 完美、2 狂暴、3 自定义
 * @property achievementMonthKind 月目标档位
 * @property achievementWeekMin 自定义周目标分钟数，0 表示未设
 * @property achievementMonthMin 自定义月目标分钟数，0 表示未设
 * @property cardPoolMask 形态启用池位掩码，0 表示全开
 */
data class WereadAppearance(
    val paperColor: Int = PAPER_DEFAULT,
    val bodySize: Int = BODY_SIZE_DEFAULT,
    val noteCardSize: Int = NOTE_SIZE_DEFAULT,
    val showSignature: Boolean = true,
    val monthHeatmap: Boolean = false,
    val rankShelfOnly: Boolean = false,
    val achievementEnabled: Boolean = false,
    val achievementWeekKind: Int = WereadAchievement.KIND_OFF,
    val achievementMonthKind: Int = WereadAchievement.KIND_OFF,
    val achievementWeekMin: Int = 0,
    val achievementMonthMin: Int = 0,
    val cardPoolMask: Int = 0,
    val switchMode: Int = 0,
) {
    /** 纸色对应的画布底色，未知档位回落素白。 */
    val paperArgb: Int
        get() =
            when (paperColor) {
                PAPER_CREAM -> PAPER_CREAM_ARGB
                PAPER_WARM -> PAPER_WARM_ARGB
                else -> PAPER_DEFAULT_ARGB
            }

    /** 本记在卡片内显示的字号，落在允许区间内。 */
    val effectiveNoteSize: Int
        get() = noteCardSize.coerceIn(NOTE_SIZE_MIN, NOTE_SIZE_MAX)

    /** 正文字号，落在允许区间内。 */
    val effectiveBodySize: Int
        get() = bodySize.coerceIn(NOTE_SIZE_MIN, NOTE_SIZE_MAX)

    /** 按周目标档位换算分钟数，自定义非正值视为未设。 */
    val effectiveWeekMin: Int
        get() = WereadAchievement.targetMinutes(true, achievementWeekKind, achievementWeekMin)

    /** 按月目标档位换算分钟数，自定义非正值视为未设。 */
    val effectiveMonthMin: Int
        get() = WereadAchievement.targetMinutes(false, achievementMonthKind, achievementMonthMin)

    companion object {
        const val PAPER_DEFAULT: Int = 0
        const val PAPER_CREAM: Int = 1
        const val PAPER_WARM: Int = 2
        const val PAPER_DEFAULT_ARGB: Int = 0xFFFFFFFF.toInt()
        const val PAPER_CREAM_ARGB: Int = 0xFFF7F2E7.toInt()
        const val PAPER_WARM_ARGB: Int = 0xFFEFE9E1.toInt()
        const val BODY_SIZE_DEFAULT: Int = 17
        const val NOTE_SIZE_DEFAULT: Int = 17
        const val NOTE_SIZE_MIN: Int = 12
        const val NOTE_SIZE_MAX: Int = 24
        const val PREF_PAPER_COLOR: String = "app_paper_color"
        const val PREF_BODY_SIZE: String = "app_body_size"
        const val PREF_NOTE_CARD_SIZE: String = "note_card_size"
        const val PREF_SHOW_SIGNATURE: String = "app_show_signature"
        const val PREF_MONTH_STYLE: String = "month_style"
        const val PREF_RANK_SHELF_ONLY: String = "rank_shelf_only"
        const val PREF_ACHIEVEMENT_ENABLED: String = "achv_enabled"
        const val PREF_ACHIEVEMENT_WEEK_MIN: String = "achv_week_min"
        const val PREF_ACHIEVEMENT_MONTH_MIN: String = "achv_month_min"
        const val PREF_CARD_POOL_MASK: String = "card_pool_mask"
        const val PREF_FILE: String = "kernel_prefs.json"

        /** 读取偏好文件后的外观快照，文件缺失或结构异常时返回默认外观。 */
        fun read(root: File): WereadAppearance {
            val file = File(root, PREF_FILE)
            val text = if (file.isFile) runCatching { file.readText() }.getOrNull() else null
            val document = text?.let { parse(it) } ?: return WereadAppearance()
            return of(document)
        }

        /**
         * 从偏好文档构建外观快照。
         *
         * @param document 解析成功的偏好根对象
         * @return 外观快照，缺失键取默认值
         */
        fun of(document: JsonObject): WereadAppearance {
            val ints = group(document, GROUP_INT)
            val booleans = group(document, GROUP_BOOL)
            return WereadAppearance(
                paperColor = ints.intOf(PREF_PAPER_COLOR, PAPER_DEFAULT),
                bodySize = ints.intOf(PREF_BODY_SIZE, BODY_SIZE_DEFAULT),
                noteCardSize = ints.intOf(PREF_NOTE_CARD_SIZE, NOTE_SIZE_DEFAULT),
                showSignature = booleans.boolOf(PREF_SHOW_SIGNATURE, true),
                monthHeatmap = ints.intOf(PREF_MONTH_STYLE, 0) == 1,
                rankShelfOnly = booleans.boolOf(PREF_RANK_SHELF_ONLY, false),
                achievementEnabled = booleans.boolOf("cb_achv", booleans.boolOf(PREF_ACHIEVEMENT_ENABLED, false)),
                achievementWeekKind = achievementKind(ints, "achv_week_kind", PREF_ACHIEVEMENT_WEEK_MIN),
                achievementMonthKind = achievementKind(ints, "achv_month_kind", PREF_ACHIEVEMENT_MONTH_MIN),
                achievementWeekMin = customMinutes(document, "achv_week_custom", ints, PREF_ACHIEVEMENT_WEEK_MIN),
                achievementMonthMin = customMinutes(document, "achv_month_custom", ints, PREF_ACHIEVEMENT_MONTH_MIN),
                cardPoolMask = ints.intOf(PREF_CARD_POOL_MASK, 0),
                switchMode = ints.intOf("switch_mode", 0).coerceIn(0, 1),
            )
        }

        private fun achievementKind(
            ints: JsonObject,
            key: String,
            legacy: String,
        ): Int {
            val fallback =
                if (ints.intOf(legacy, 0) > 0) WereadAchievement.KIND_CUSTOM else WereadAchievement.KIND_OFF
            return ints.intOf(key, fallback)
        }

        private fun customMinutes(
            document: JsonObject,
            key: String,
            ints: JsonObject,
            legacy: String,
        ): Int {
            val strings = group(document, "string")
            val value = strings[key]?.jsonPrimitive?.content
            return if (value != null) WereadAchievement.customMinutes(value) else ints.intOf(legacy, 0)
        }

        private const val GROUP_INT = "int"
        private const val GROUP_BOOL = "bool"

        private fun parse(text: String): JsonObject? =
            runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject

        private fun group(
            document: JsonObject,
            name: String,
        ): JsonObject = document[name] as? JsonObject ?: JsonObject(emptyMap())

        private fun JsonObject.intOf(
            key: String,
            fallback: Int,
        ): Int = this[key]?.jsonPrimitive?.intOrNull ?: fallback

        private fun JsonObject.boolOf(
            key: String,
            fallback: Boolean,
        ): Boolean = this[key]?.jsonPrimitive?.booleanOrNull ?: fallback
    }
}
