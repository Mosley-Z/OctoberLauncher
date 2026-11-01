package com.shuiyu.octoberrain.feature.home

import android.graphics.Canvas
import android.graphics.RectF
import com.shuiyu.octoberrain.core.common.DesktopFolder
import com.shuiyu.octoberrain.core.common.EntryKeys
import com.shuiyu.octoberrain.core.common.EntryType
import com.shuiyu.octoberrain.core.common.InstalledApp
import com.shuiyu.octoberrain.core.common.LauncherEntry

/**
 * 成员来自完整应用目录，不能使用已剔除收纳应用的桌面入口列表。
 *
 * 参数 appsByKey 以组件键索引应用目录；成员键按精确身份解析：组件键要求 Activity 名一致，
 * 旧包级键只在该包候选唯一时回退，多候选视为不可用，避免预览展示一个组件而点击启动另一个。
 */
internal fun previewFolderMembers(
    folder: DesktopFolder,
    appsByKey: Map<String, InstalledApp>,
    hiddenPackages: Set<String>,
): List<LauncherEntry> =
    folder.memberKeys.mapNotNull { key ->
        val app = resolveMember(key, appsByKey) ?: return@mapNotNull null
        if (app.packageName in hiddenPackages) return@mapNotNull null
        LauncherEntry(
            key = key,
            type = EntryType.APP,
            label = app.label,
            packageName = app.packageName,
            activityName = app.activityName,
        )
    }

/** 编辑列表保留已卸载成员的引用和顺序，隐藏成员仍不展示。 */
internal fun editableFolderMembers(
    folder: DesktopFolder,
    appsByKey: Map<String, InstalledApp>,
    hiddenPackages: Set<String>,
): List<LauncherEntry> =
    folder.memberKeys.mapNotNull { key ->
        val packageName = EntryKeys.packageOf(key) ?: return@mapNotNull null
        if (packageName in hiddenPackages) return@mapNotNull null
        val app = resolveMember(key, appsByKey)
        val label = app?.label ?: "${packageName.substringAfterLast('.')}（已卸载）"
        LauncherEntry(
            key = key,
            type = EntryType.APP,
            label = label,
            packageName = packageName,
            activityName = app?.activityName,
        )
    }

/**
 * 按入口的组件精确身份解析应用目录条目。
 *
 * 参数 entry 为待启动入口，apps 为目录快照。优先匹配包名与 Activity 同时相等的条目；
 * 入口没有组件身份时按旧包级键的唯一候选回退。返回空表示该入口已失效，
 * 调用方应给出失败提示，不得改用同包其他入口。
 */
internal fun resolveEntryApp(
    entry: LauncherEntry,
    apps: Collection<InstalledApp>,
): InstalledApp? =
    entry.packageName?.let { packageName ->
        val activityName = entry.activityName
        when {
            activityName != null ->
                apps.firstOrNull { it.packageName == packageName && it.activityName == activityName }
            // 组件键缺少 Activity 身份时视为失效，不回退到同包其他入口。
            EntryKeys.activityOf(entry.key) != null -> null
            else -> resolveLegacyApp(packageName, apps)
        }
    }

/**
 * 解析成员键对应的应用条目。
 *
 * 参数 key 为成员键，appsByKey 为组件键索引。组件键要求 Activity 名与键一致；
 * 旧包级键只在该包候选唯一时回退到该组件，其余情况返回空，表示成员不可用。
 */
private fun resolveMember(
    key: String,
    appsByKey: Map<String, InstalledApp>,
): InstalledApp? {
    val activityName = EntryKeys.activityOf(key)
    return if (activityName != null) {
        appsByKey[key]?.takeIf { it.activityName == activityName }
    } else {
        EntryKeys.legacyPackageOf(key)?.let { resolveLegacyApp(it, appsByKey.values) }
    }
}

/**
 * 旧包级键的唯一候选回退。
 *
 * 参数 packageName 为包名，apps 为应用目录快照。仅当该包恰好有一个带 Activity 名的入口时返回它，
 * 多候选或没有候选返回空；预览与点击共用本判定，避免两处结论分叉。
 */
internal fun resolveLegacyApp(
    packageName: String,
    apps: Collection<InstalledApp>,
): InstalledApp? =
    apps
        .filter { it.packageName == packageName && !it.activityName.isNullOrBlank() }
        .singleOrNull()

/** 透明的 2×2 组合图标；空组保留等宽留白，所有成员绘制都裁在当前图标框内。 */
internal object FolderPreviewPainter {
    fun draw(
        canvas: Canvas,
        rect: RectF,
        members: List<LauncherEntry>,
        drawMember: (Canvas, RectF, LauncherEntry) -> Unit,
    ) {
        if (rect.width() <= 0f || rect.height() <= 0f) return
        val saved = canvas.save()
        canvas.clipRect(rect)
        val side = minOf(rect.width(), rect.height())
        val gap = side * .08f
        val size = (side - gap) / 2f
        val left = rect.centerX() - side / 2f
        val top = rect.centerY() - side / 2f
        members.take(4).forEachIndexed { index, member ->
            val x = left + (index % 2) * (size + gap)
            val y = top + (index / 2) * (size + gap)
            drawMember(canvas, RectF(x, y, x + size, y + size), member)
        }
        canvas.restoreToCount(saved)
    }
}
