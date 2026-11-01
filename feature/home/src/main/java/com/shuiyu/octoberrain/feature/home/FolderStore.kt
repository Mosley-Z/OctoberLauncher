package com.shuiyu.octoberrain.feature.home

import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.DesktopConfig
import com.shuiyu.octoberrain.core.common.FolderFailure
import com.shuiyu.octoberrain.core.common.FolderResult
import com.shuiyu.octoberrain.core.common.SortMode

/** 通过一次配置事务保存文件夹操作，确认状态发布后才报告成功。 */
internal object FolderStore {
    /**
     * 保存一次文件夹操作。
     *
     * @param result 规则运算结果
     * @param source 生成 result 时的 DesktopConfig 快照
     * @return 成功返回 null；规则失败、快照过期或写盘未发布时返回原因
     */
    fun apply(
        result: FolderResult,
        source: DesktopConfig,
    ): FolderFailure? {
        if (result is FolderResult.Failure) return result.reason
        val success = result as FolderResult.Success
        val expected = success.desktop.copy(sortMode = SortMode.MANUAL)
        return runCatching {
            AppServices.config.edit { cfg ->
                // 快照已过期时不写入，保留当前配置；调用方据返回值提示重试。
                if (cfg.desktop != source) cfg else cfg.copy(desktop = expected)
            }
            if (AppServices.config.state.value.desktop == expected) null else FolderFailure.UNKNOWN
        }.getOrDefault(FolderFailure.UNKNOWN)
    }

    /**
     * 保存一次自动成组结果。
     *
     * 参数 expected 为规则产出的新桌面，source 为产出该结果时的桌面快照；两者与处理状态
     * 同属一个 DesktopConfig，因此写盘要么整体生效，要么保持原快照。
     * 排序模式由规则决定：只有实际改变布局时才切到手动排序，仅记录保留或拒绝结论时保持原模式。
     *
     * @return 真表示配置已发布且与期望一致；假表示未写入任何字段，包括每个包的处理状态
     */
    fun applyAutoGroup(
        expected: DesktopConfig,
        source: DesktopConfig,
    ): Boolean {
        val target = expected
        return runCatching {
            AppServices.config.edit { cfg ->
                // 快照已过期时不写入，避免覆盖用户在此期间的操作；调用方会在下一轮重新判定
                if (cfg.desktop != source) cfg else cfg.copy(desktop = target)
            }
            AppServices.config.state.value.desktop == target
        }.getOrDefault(false)
    }
}
