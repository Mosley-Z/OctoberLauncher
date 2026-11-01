package com.shuiyu.octoberrain.core.common

import java.util.Locale

/**
 * 同包多入口的一次自动成组规则。
 *
 * 规则只读取应用目录快照与桌面配置，不访问配置存储、不持有状态，也不依赖绘制流程。
 * 调用方在目录完整刷新后调用 [run] 与 [pendingLegacy]，把结果交给一次配置事务写入；
 * 每个包只需判定一次，判定结果记录在 [DesktopConfig.folderAutoGroup] 中，重启、刷新、
 * 升级与备份恢复后保持既有安排。
 */
object FolderAutoGroup {
    /** 已按当前组件建立文件夹。 */
    const val GROUPED = "grouped"

    /** 该包已有入口位于手动文件夹内，保留手动安排。 */
    const val KEPT = "kept"

    /** 用户暂不把旧包级引用转换为组件文件夹。 */
    const val DECLINED = "declined"

    /** 旧包级引用待确认的标记，与组件键解析器写入的结论取值一致。 */
    const val PENDING = "pending"

    private const val MAX_NAME_LENGTH = 40

    /** 一次待确认的旧包级引用，按包聚合。 */
    data class LegacyPending(
        /** 包名。 */
        val packageName: String,
        /** 建议的文件夹名称，取应用级名称，缺失时回退组件名称与包名。 */
        val folderName: String,
        /** 仍待确认的旧包级入口键。 */
        val entryKeys: List<String>,
        /** 旧键所在文件夹标识；位于桌面顶层时为空。 */
        val hostFolderId: String?,
        /** 旧键所在文件夹名称；位于桌面顶层时为空。 */
        val hostFolderName: String?,
        /** 当前可启动组件数量，用于确认文案。 */
        val entryCount: Int,
    )

    /**
     * 按当前应用目录整理同包多入口。
     *
     * 参数 desktop 为当前桌面配置，apps 为完整应用目录快照，labels 为包名到应用级名称的映射，
     * visibleOrder 为当前实际呈现的有效入口顺序（含自动排序结果，可包含文件夹与特殊入口键）。
     * 目录为空时表示枚举尚未完成，直接返回空；整理结果为入参本身时同样返回空，
     * 调用方据此跳过写盘，避免无意义的事务。
     */
    fun run(
        desktop: DesktopConfig,
        apps: List<InstalledApp>,
        labels: Map<String, String> = emptyMap(),
        visibleOrder: List<String> = emptyList(),
    ): DesktopConfig? {
        // 旧包级入口键尚未解析完成时不归组：此时桌面仍以旧键记录入口，
        // 归组会消费这些键并写入处理状态，使用户看不到旧引用确认浮层
        if (!desktop.componentKeyMigrated) return null
        val normalized = FolderRules.normalize(desktop)
        // 只保留隐藏状态之外、具备启动 Activity 且至少两个入口的包
        val groups =
            apps
                .asSequence()
                .filter { !it.activityName.isNullOrBlank() }
                .filter { it.packageName !in normalized.hiddenPackages }
                .groupBy { it.packageName }
                .filterValues { it.size >= 2 }
        val updated =
            if (apps.isEmpty() || groups.isEmpty()) {
                null
            } else {
                applyPackageGroups(normalized, groups, labels, visibleOrder)
            }
        return updated?.takeIf { it != desktop }
    }

    /**
     * 列出仍待确认的旧包级引用。
     *
     * 参数 desktop 为当前桌面配置，apps 为完整应用目录快照，labels 为包名到应用级名称的映射。
     * 返回按名称排序的待确认列表；旧键已不在桌面与文件夹中出现、或该包组件不足两个时跳过，
     * 避免建立单成员文件夹。
     */
    fun pendingLegacy(
        desktop: DesktopConfig,
        apps: List<InstalledApp>,
        labels: Map<String, String> = emptyMap(),
    ): List<LegacyPending> {
        val pendingKeys = desktop.legacyComponentDecisions.filterValues { it == PENDING }.keys
        if (pendingKeys.isEmpty()) return emptyList()
        val byPackage =
            pendingKeys
                .mapNotNull { key -> EntryKeys.legacyPackageOf(key)?.let { it to key } }
                .groupBy({ it.first }, { it.second })
        return byPackage
            .mapNotNull { (packageName, keys) -> legacyPendingOf(desktop, apps, labels, packageName, keys.toSet()) }
            .sortedBy { it.folderName }
    }

    /**
     * 列出已拒绝整理、可再次主动处理的旧包级引用所属包名。
     *
     * 参数 desktop 为当前桌面配置。只有同时存在拒绝结论与旧键记录时才返回，
     * 菜单据此决定是否显示再次整理入口，避免长期占用菜单空间。
     */
    fun declinedPackages(desktop: DesktopConfig): List<String> =
        FolderRules
            .normalize(desktop)
            .folderAutoGroup
            .filterValues { it == DECLINED }
            .keys
            .filter { packageName -> declinedKeysOf(desktop, packageName).isNotEmpty() }
            .sorted()

    /**
     * 把指定包已拒绝的旧引用恢复为待确认状态。
     *
     * 参数 desktop 为当前桌面配置，packageName 为包名。恢复后移除该包的处理结论，
     * 使确认浮层可以再次发起；没有可恢复记录时返回空，不产生配置变化。
     */
    fun reopenDeclined(
        desktop: DesktopConfig,
        packageName: String,
    ): DesktopConfig? {
        val normalized = FolderRules.normalize(desktop)
        val keys = declinedKeysOf(normalized, packageName)
        if (normalized.folderAutoGroup[packageName] != DECLINED || keys.isEmpty()) return null
        val reopened =
            normalized.legacyComponentDecisions.mapValues { (key, value) ->
                if (value == DECLINED && key in keys) PENDING else value
            }
        val updated =
            normalized.copy(
                folderAutoGroup = normalized.folderAutoGroup - packageName,
                legacyComponentDecisions = reopened,
            )
        return updated.takeIf { it != desktop }
    }

    /**
     * 写入一次旧包级引用的处理决定。
     *
     * 参数 desktop 为当前桌面配置，apps 为完整应用目录快照，packageName 为待处理的包名，
     * accepted 表示用户是否确认转换，labels 为包名到应用级名称的映射，
     * visibleOrder 为当前实际呈现的有效入口顺序，用于保持替换后的相对位置。
     * 拒绝时只记录结论、不改动桌面顺序与文件夹；接受时把旧键替换为组件文件夹或唯一组件键。
     * 没有待确认旧键时返回空，处理结果与入参相同也返回空。
     */
    fun resolveLegacy(
        desktop: DesktopConfig,
        apps: List<InstalledApp>,
        packageName: String,
        accepted: Boolean,
        labels: Map<String, String> = emptyMap(),
        visibleOrder: List<String> = emptyList(),
    ): DesktopConfig? {
        val oldKeys =
            desktop.legacyComponentDecisions
                .filter { (key, value) -> value == PENDING && EntryKeys.legacyPackageOf(key) == packageName }
                .keys
        val normalized = FolderRules.normalize(desktop)
        val decisions = normalized.legacyComponentDecisions.toMutableMap()
        val baseOrder = effectiveOrder(normalized.order, visibleOrder)
        val keys = orderedKeys(componentsOf(apps, packageName), baseOrder)
        val updated =
            when {
                oldKeys.isEmpty() -> null
                // 拒绝只记录结论，桌面顺序与文件夹保持原样，旧键继续按不可用语义显示
                !accepted -> {
                    oldKeys.forEach { decisions[it] = DECLINED }
                    normalized.copy(
                        folderAutoGroup = normalized.folderAutoGroup + (packageName to DECLINED),
                        legacyComponentDecisions = decisions.toMap(),
                    )
                }
                keys.isEmpty() -> null
                // 组件不足两个时把旧键原位解析为唯一组件键，不建立单成员文件夹
                keys.size < 2 -> {
                    val componentKey = keys.first()
                    val order = baseOrder.map { if (it in oldKeys) componentKey else it }.distinct()
                    oldKeys.forEach { decisions[it] = GROUPED }
                    normalized.copy(
                        order = order,
                        folderAutoGroup = normalized.folderAutoGroup + (packageName to GROUPED),
                        legacyComponentDecisions = decisions.toMap(),
                        sortMode = SortMode.MANUAL,
                    )
                }
                else -> resolveLegacyFolder(normalized, apps, packageName, labels, decisions, oldKeys, keys, baseOrder)
            }
        return updated?.takeIf { it != desktop }
    }

    /**
     * 逐包写入自动成组结果。
     *
     * 参数 normalized 为规整后的桌面配置，groups 为待判定的包与组件，labels 为应用级名称映射，
     * visibleOrder 为当前实际呈现的有效入口顺序。定位以持久顺序为轴并补齐其中缺失的可见条目，
     * 使新组落在成员原本的位置、其余条目的相对顺序保持不变；没有持久顺序时按呈现顺序定位。
     * 已判定过的包、已有组件位于手动文件夹内的包都保持原安排，其余包建立文件夹并从桌面顺序移除成员。
     * 仍有待确认旧包级引用的包在本轮跳过，留给确认流程建立文件夹。
     */
    private fun applyPackageGroups(
        normalized: DesktopConfig,
        groups: Map<String, List<InstalledApp>>,
        labels: Map<String, String>,
        visibleOrder: List<String>,
    ): DesktopConfig? {
        val decisions = normalized.folderAutoGroup.toMutableMap()
        val consumedKeys = mutableSetOf<String>()
        var order = effectiveOrder(normalized.order, visibleOrder)
        var folders = normalized.folders
        var layoutChanged = false
        for ((packageName, components) in groups) {
            // 已判定过的包保持原安排，重启、刷新与备份恢复都不再改写；
            // 仍有待确认旧包级引用的包跳过自动成组，交给确认流程处理，
            // 避免静默消费旧键导致确认浮层不再出现
            if (packageName in decisions || pendingLegacyKeysOf(normalized, packageName).isNotEmpty()) continue
            val memberKeys = components.map { EntryKeys.appEntryKey(it) }
            val memberSet = memberKeys.toSet()
            // 已有手动成员留在原组，记录保留结论后进入下一个包
            if (folders.any { folder -> folder.memberKeys.any { it in memberSet } }) {
                decisions[packageName] = KEPT
            } else {
                val base = normalized.copy(folders = folders, order = order)
                val folderId = FolderRules.nextFolderId(base)
                // 旧包级键作为替换锚点一并移除，避免同包旧键残留在桌面顺序中
                val orderedMembers = orderedKeys(components, order)
                val replacedKeys = memberSet + EntryKeys.packageKey(packageName)
                order = insertFolderKey(order, replacedKeys, "folder:$folderId")
                consumedKeys += EntryKeys.packageKey(packageName)
                folders =
                    folders +
                    DesktopFolder(
                        id = folderId,
                        name = folderName(packageName, components, labels),
                        memberKeys = orderedMembers,
                    )
                decisions[packageName] = GROUPED
                layoutChanged = true
            }
        }
        val updated =
            normalized.copy(
                folders = cleanupConsumedMembers(folders, consumedKeys),
                order = order,
                folderAutoGroup = decisions.toMap(),
                sortMode = if (layoutChanged) SortMode.MANUAL else normalized.sortMode,
            )
        val unchanged = !layoutChanged && decisions == normalized.folderAutoGroup
        return if (unchanged) null else updated
    }

    /**
     * 汇总一个包的待确认引用。
     *
     * 参数 desktop 为当前桌面配置，apps 为应用目录快照，labels 为应用级名称映射，
     * packageName 为包名，keys 为该包待确认的旧键。
     */
    private fun legacyPendingOf(
        desktop: DesktopConfig,
        apps: List<InstalledApp>,
        labels: Map<String, String>,
        packageName: String,
        keys: Set<String>,
    ): LegacyPending? {
        val components = componentsOf(apps, packageName)
        val liveKeys =
            keys.filter { key ->
                key in desktop.order || desktop.folders.any { folder -> key in folder.memberKeys }
            }
        if (components.size < 2 || liveKeys.isEmpty()) return null
        val host = desktop.folders.firstOrNull { folder -> folder.memberKeys.any { it in liveKeys } }
        return LegacyPending(
            packageName = packageName,
            folderName = folderName(packageName, components, labels),
            entryKeys = liveKeys.sorted(),
            hostFolderId = host?.id,
            hostFolderName = host?.name,
            entryCount = components.size,
        )
    }

    /**
     * 建立组件文件夹并替换旧包级引用。
     *
     * 参数 normalized 为规整后的桌面配置，apps 为应用目录快照，packageName 为包名，
     * labels 为应用级名称映射，decisions 为待写回的判定结果，oldKeys 为待替换的旧键，
     * keys 为按顺序排好的组件键，baseOrder 为当前有效呈现顺序。旧键位于已有文件夹内时移出该键，
     * 新组放到宿主文件夹之后，避免文件夹嵌套；位于桌面顶层时按原位置插入。
     */
    private fun resolveLegacyFolder(
        normalized: DesktopConfig,
        apps: List<InstalledApp>,
        packageName: String,
        labels: Map<String, String>,
        decisions: MutableMap<String, String>,
        oldKeys: Set<String>,
        keys: List<String>,
        baseOrder: List<String>,
    ): DesktopConfig {
        val memberSet = keys.toSet()
        val replacedKeys = oldKeys + memberSet
        val hostFolder = normalized.folders.firstOrNull { folder -> folder.memberKeys.any { it in oldKeys } }
        val folderId = FolderRules.nextFolderId(normalized)
        val folderKey = "folder:$folderId"
        val order =
            if (hostFolder != null) {
                // 宿主文件夹内不允许嵌套，新组放到宿主之后，旧键与组件键都从顺序与宿主成员中移出
                val cleaned = baseOrder.filterNot { it in replacedKeys }
                val hostIndex = cleaned.indexOf("folder:${hostFolder.id}")
                val insert = if (hostIndex >= 0) hostIndex + 1 else cleaned.size
                cleaned.toMutableList().apply { add(insert.coerceIn(0, size), folderKey) }
            } else {
                insertFolderKey(baseOrder, replacedKeys, folderKey)
            }
        val folders =
            normalized.folders.map { folder ->
                if (folder.memberKeys.any { it in replacedKeys }) {
                    folder.copy(memberKeys = folder.memberKeys.filterNot { it in replacedKeys })
                } else {
                    folder
                }
            } +
                DesktopFolder(
                    id = folderId,
                    name = folderName(packageName, componentsOf(apps, packageName), labels),
                    memberKeys = keys,
                )
        oldKeys.forEach { decisions[it] = GROUPED }
        return normalized.copy(
            folders = folders,
            order = order,
            folderAutoGroup = normalized.folderAutoGroup + (packageName to GROUPED),
            legacyComponentDecisions = decisions.toMap(),
            sortMode = SortMode.MANUAL,
        )
    }

    /**
     * 取一个包的可启动组件，按包名过滤并排除缺少 Activity 的条目。
     *
     * 参数 apps 为应用目录快照，packageName 为包名。
     */
    private fun componentsOf(
        apps: List<InstalledApp>,
        packageName: String,
    ): List<InstalledApp> = apps.filter { it.packageName == packageName && !it.activityName.isNullOrBlank() }

    /**
     * 生成文件夹名称。
     *
     * 参数 packageName 为包名，components 为该包组件，labels 为应用级名称映射。
     * 优先使用应用级名称，其次使用该包第一条组件名称，最后回退包名；
     * 名称去空白并截断到四十个字符。
     */
    private fun folderName(
        packageName: String,
        components: List<InstalledApp>,
        labels: Map<String, String>,
    ): String =
        labels[packageName]?.trim()?.take(MAX_NAME_LENGTH)?.takeIf { it.isNotEmpty() }
            ?: components
                .mapNotNull {
                    it.label
                        .trim()
                        .take(MAX_NAME_LENGTH)
                        .takeIf { name -> name.isNotEmpty() }
                }.firstOrNull()
            ?: packageName.take(MAX_NAME_LENGTH)
}

/**
 * 取一个包仍待确认的旧包级键。
 *
 * 参数 desktop 为当前桌面配置，packageName 为包名。
 * 只返回标记为待确认且属于该包的键；空集合表示该包没有待确认引用。
 */
private fun pendingLegacyKeysOf(
    desktop: DesktopConfig,
    packageName: String,
): Set<String> =
    desktop.legacyComponentDecisions
        .filter { (key, value) -> value == FolderAutoGroup.PENDING && EntryKeys.legacyPackageOf(key) == packageName }
        .keys

/**
 * 把被消费的旧包级键从文件夹成员中移出。
 *
 * 参数 folders 为当前文件夹列表，consumedKeys 为已被组件文件夹替换的旧包级键。
 * 除成员变化外保持文件夹原样，成员清空后仍保留文件夹，避免自动删除用户建立的组。
 */
private fun cleanupConsumedMembers(
    folders: List<DesktopFolder>,
    consumedKeys: Set<String>,
): List<DesktopFolder> {
    if (consumedKeys.isEmpty()) return folders
    return folders.map { folder ->
        if (folder.memberKeys.any { it in consumedKeys }) {
            folder.copy(memberKeys = folder.memberKeys.filterNot { it in consumedKeys })
        } else {
            folder
        }
    }
}

/**
 * 合并出当前有效完整顺序。
 *
 * 参数 persistedOrder 为持久化的桌面顺序，visibleOrder 为当前实际呈现的入口顺序。
 * 持久化顺序非空时以它为主轴，保留旧包级键等不可见条目在原有位置，把呈现结果中尚未持久化的
 * 新条目按呈现顺序追加在后；持久化顺序为空时直接使用呈现顺序，使首次整理在没有历史顺序的
 * 情况下也能按实际显示位置定位。
 */
private fun effectiveOrder(
    persistedOrder: List<String>,
    visibleOrder: List<String>,
): List<String> {
    if (persistedOrder.isEmpty()) return visibleOrder
    val persistedSet = persistedOrder.toSet()
    return persistedOrder + visibleOrder.filterNot { it in persistedSet }
}

/**
 * 在被替换入口最靠前的位置插入文件夹键。
 *
 * 参数 order 为当前桌面顺序，replacedKeys 为被替换的组件键与旧包级键，folderKey 为新文件夹键。
 * 先移除这些键，再把文件夹键放到它们最早出现的位置；最早出现之前的条目都在保留范围内，
 * 因此插入下标等于该位置，其余条目相对顺序保持不变。
 */
private fun insertFolderKey(
    order: List<String>,
    replacedKeys: Set<String>,
    folderKey: String,
): List<String> {
    val anchorIndex = order.indexOfFirst { it in replacedKeys }
    val remaining = order.filterNot { it in replacedKeys }
    val insertIndex = if (anchorIndex < 0) remaining.size else anchorIndex.coerceAtMost(remaining.size)
    return remaining.toMutableList().apply { add(insertIndex, folderKey) }
}

/**
 * 取一个包已拒绝的旧包级键。
 *
 * 参数 desktop 为当前桌面配置，packageName 为包名。
 * 只返回标记为已拒绝且属于该包的键；空集合表示该包没有可恢复的引用。
 */
private fun declinedKeysOf(
    desktop: DesktopConfig,
    packageName: String,
): Set<String> =
    desktop.legacyComponentDecisions
        .filter { (key, value) -> value == FolderAutoGroup.DECLINED && EntryKeys.legacyPackageOf(key) == packageName }
        .keys

/**
 * 按桌面顺序与名称生成稳定的成员键列表。
 *
 * 参数 components 为该包组件，order 为当前桌面顺序；顺序位次缺失的组件按名称、
 * Activity 与键名依次排序，保证同一快照多次调用结果一致。
 */
private fun orderedKeys(
    components: List<InstalledApp>,
    order: List<String>,
): List<String> {
    val rank = order.withIndex().associate { (index, key) -> key to index }
    return components
        .sortedWith(
            compareBy<InstalledApp> { rank[EntryKeys.appEntryKey(it)] ?: Int.MAX_VALUE }
                .thenBy { it.label.lowercase(Locale.ROOT) }
                .thenBy { it.activityName.orEmpty() }
                .thenBy { it.packageName },
        ).map { EntryKeys.appEntryKey(it) }
}
