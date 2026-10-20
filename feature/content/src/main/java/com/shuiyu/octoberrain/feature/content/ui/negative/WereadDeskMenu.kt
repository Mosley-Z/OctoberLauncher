package com.shuiyu.octoberrain.feature.content.ui.negative

import com.inkread.weekread.kernel.desk.DeskPrefs
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.WereadMenuConfig

/**
 * 墨台分区读写的偏好封装。
 *
 * 把内核使用的偏好键名映射到十雨配置：墨单外观映射到 [WereadMenuConfig]，阅读资产的三个
 * 过滤项与阅读指纹字号映射到负一屏墨台配置的对应字段。配置尚未安装或读取失败时返回默认
 * 值，保证内核构造阶段不因服务缺失而中断。
 *
 * @param fpBooks 阅读指纹默认选书读取器，返回逗号分隔的书目标识
 */
internal class WereadDeskMenu(
    private val fpBooks: () -> String = { "" },
) {
    /**
     * 读取字符串类选项。
     *
     * @param group 选项分组，字符串选项固定为 cfg
     * @param key 选项键名，取值见 `DeskPrefs.K_*`
     * @param default 键名未知或分组不符时的默认值
     * @return 当前配置值或默认值
     */
    fun prefs(
        group: String,
        key: String,
        default: String,
    ): String {
        if (group != DeskPrefs.FILE_CFG) return default
        val menu = config()
        return when (key) {
            DeskPrefs.K_TITLE -> menu.effectiveTitle
            DeskPrefs.K_UNIT -> menu.effectiveUnit
            DeskPrefs.K_NOTE_SRC -> menu.effectiveNoteSource
            DeskPrefs.K_EXCERPT -> menu.effectiveExcerptMode
            DeskPrefs.K_FOOTER -> menu.effectiveFooter
            // 指纹选书不在墨单外观配置里，由已同步的笔记索引给出默认值，
            // 用户仍可在「选择指纹书目」对话框里改选。
            K_FP_BOOKS -> fpBooks().ifBlank { default }
            // 已移出书目以逗号拼接的书目标识保存在负一屏墨台配置中。
            K_HIDDEN_IDS -> hiddenIdsText().ifBlank { default }
            else -> default
        }
    }

    /**
     * 读取开关类选项。
     *
     * @param key 选项键名，取值见 `DeskPrefs.K_*`
     * @param default 键名未知时的默认值
     * @return 当前配置值或默认值
     */
    fun bool(
        key: String,
        default: Boolean,
    ): Boolean {
        val menu = config()
        val desk = deskConfig()
        return when (key) {
            DeskPrefs.K_DROP_OFF_SHELF -> menu.dropOffShelf
            DeskPrefs.K_SHOW_AUTHOR -> menu.showAuthor
            DeskPrefs.K_SHOW_DURATION -> menu.showDuration
            DeskPrefs.K_SHOW_PROGRESS -> menu.showProgress
            // 阅读资产过滤读取负一屏墨台配置，默认关闭表示统计全部有阅读时长的书。
            K_ASSET_FINISHED_ONLY -> desk.assetFinishedOnly
            K_ASSET_SHELF_ONLY -> desk.assetShelfOnly
            else -> default
        }
    }

    /**
     * 读取整数类选项。
     *
     * @param key 选项键名，取值见 `DeskPrefs.K_*`
     * @param default 键名未知时的默认值
     * @return 当前配置值或默认值
     */
    fun int(
        key: String,
        default: Int,
    ): Int {
        val menu = config()
        return when (key) {
            DeskPrefs.K_TOP_N -> menu.effectiveTopN
            DeskPrefs.K_MIN_SEC -> menu.effectiveMinSeconds
            DeskPrefs.K_BLOCKS -> menu.effectiveBlocksMask
            DeskPrefs.K_VIEW -> menu.effectiveView
            K_FP_FONT -> deskConfig().effectiveFpFontPx
            else -> default
        }
    }

    /**
     * 把已移出书目标识拼成逗号分隔文本。
     *
     * @return 逗号分隔的书目标识
     */
    private fun hiddenIdsText(): String {
        val hidden = deskConfig().hiddenIds
        return if (hidden.isEmpty()) "" else hidden.joinToString(",")
    }

    /** 返回当前墨单外观配置，读取失败时返回默认配置。 */
    private fun config(): WereadMenuConfig =
        runCatching { AppServices.config.state.value.integrations.wereadMenu }
            .getOrDefault(WereadMenuConfig())

    /** 返回当前负一屏墨台配置，读取失败时返回默认配置。 */
    private fun deskConfig() =
        runCatching { AppServices.config.state.value.integrations.wereadDesk }
            .getOrDefault(
                com.shuiyu.octoberrain.core.common
                    .WereadDeskConfig(),
            )

    private companion object {
        /** 阅读指纹选书键名，与内核读取的偏好键一致。 */
        const val K_FP_BOOKS = "fp_books"

        /** 阅读资产「只统计读完的书」键名，与内核读取的偏好键一致。 */
        const val K_ASSET_FINISHED_ONLY = "asset_finished_only"

        /** 阅读资产「只统计书架上的书」键名。 */
        const val K_ASSET_SHELF_ONLY = "asset_shelf_only"

        /** 已移出书目键名。 */
        const val K_HIDDEN_IDS = "hidden_ids"

        /** 阅读指纹字号键名。 */
        const val K_FP_FONT = "fp_font_px"
    }
}
