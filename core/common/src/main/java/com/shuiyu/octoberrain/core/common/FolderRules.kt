package com.shuiyu.octoberrain.core.common

/** 一次文件夹操作的统计；added 为新增成员数，moved 为从其他文件夹移入的数量。 */
data class FolderChangeSummary(
    val added: Int = 0,
    val moved: Int = 0,
    val skipped: Int = 0,
)

/** 文件夹操作失败原因。 */
enum class FolderFailure {
    INVALID_NAME,
    FOLDER_NOT_FOUND,
    DUPLICATE_ID,
    NO_VALID_MEMBERS,
    UNKNOWN,
}

/** 纯规则操作结果；失败时不修改原配置。 */
sealed interface FolderResult {
    data class Success(
        val desktop: DesktopConfig,
        val summary: FolderChangeSummary = FolderChangeSummary(),
    ) : FolderResult

    data class Failure(
        val reason: FolderFailure,
    ) : FolderResult
}

/** 文件夹纯规则，不依赖已安装应用快照，隐藏或卸载的成员仍保留引用。 */
object FolderRules {
    /** 新建文件夹；选中顺序即成员顺序，无定位键时追加到桌面末尾。 */
    fun create(
        desktop: DesktopConfig,
        name: String,
        members: List<String>,
        nextId: String,
        insertAfterKey: String? = null,
    ): FolderResult {
        val cleanedName = name.trim()
        val keys = members.filter(::isAppKey).distinct()
        val failure =
            when {
                cleanedName.isEmpty() -> FolderFailure.INVALID_NAME
                desktop.folders.any { it.id == nextId } -> FolderFailure.DUPLICATE_ID
                nextId.isBlank() -> FolderFailure.UNKNOWN
                keys.isEmpty() -> FolderFailure.NO_VALID_MEMBERS
                else -> null
            }
        if (failure != null) return FolderResult.Failure(failure)
        val normalized = normalize(desktop)
        val folderKey = "folder:$nextId"
        val order = normalized.order.filterNot { it == folderKey }.toMutableList()
        val index = insertAfterKey?.let { order.indexOf(it) } ?: -1
        order.add(if (index < 0) order.size else index + 1, folderKey)
        val prepared =
            normalized.copy(
                folders = normalized.folders + DesktopFolder(nextId, cleanedName.take(40)),
                order = order,
            )
        return addMembers(prepared, nextId, members)
    }

    /** 重命名时去除两端空白，最长保存四十个字符。 */
    fun rename(
        desktop: DesktopConfig,
        folderId: String,
        name: String,
    ): FolderResult {
        val cleanedName = name.trim()
        val failure =
            when {
                cleanedName.isEmpty() -> FolderFailure.INVALID_NAME
                desktop.folders.none { it.id == folderId } -> FolderFailure.FOLDER_NOT_FOUND
                else -> null
            }
        if (failure != null) return FolderResult.Failure(failure)
        return FolderResult.Success(
            normalize(
                desktop.copy(
                    folders =
                        desktop.folders.map {
                            if (it.id == folderId) it.copy(name = cleanedName.take(40)) else it
                        },
                ),
            ),
        )
    }

    /** 批量添加，已在本组的成员跳过，其他组的成员原子移入。 */
    fun addMembers(
        desktop: DesktopConfig,
        folderId: String,
        keys: List<String>,
    ): FolderResult {
        val normalized = normalize(desktop)
        val target =
            normalized.folders.find { it.id == folderId }
                ?: return FolderResult.Failure(FolderFailure.FOLDER_NOT_FOUND)
        val incoming = keys.filter(::isAppKey).distinct().filterNot { it in target.memberKeys }
        val incomingSet = incoming.toSet()
        val otherMembers =
            normalized.folders
                .filterNot { it.id == folderId }
                .flatMap { it.memberKeys }
                .toSet()
        val moved = incoming.count { it in otherMembers }
        val folders =
            normalized.folders.map {
                if (it.id == folderId) {
                    it.copy(memberKeys = it.memberKeys + incoming)
                } else {
                    it.copy(memberKeys = it.memberKeys.filterNot { key -> key in incomingSet })
                }
            }
        return FolderResult.Success(
            normalize(normalized.copy(folders = folders)),
            FolderChangeSummary(added = incoming.size - moved, moved = moved, skipped = keys.size - incoming.size),
        )
    }

    /** 移出成员后追加到桌面末尾；不存在的成员不产生新入口。 */
    fun removeMember(
        desktop: DesktopConfig,
        folderId: String,
        key: String,
    ): FolderResult {
        val normalized = normalize(desktop)
        val target =
            normalized.folders.find { it.id == folderId }
                ?: return FolderResult.Failure(FolderFailure.FOLDER_NOT_FOUND)
        return if (key !in target.memberKeys) {
            FolderResult.Success(normalized, FolderChangeSummary(skipped = 1))
        } else {
            FolderResult.Success(
                normalize(
                    normalized.copy(
                        folders =
                            normalized.folders.map {
                                if (it.id == folderId) it.copy(memberKeys = it.memberKeys - key) else it
                            },
                        order = normalized.order.filterNot { it == key } + key,
                    ),
                ),
            )
        }
    }

    /** 解散后在原文件夹位置按成员顺序恢复顶层应用。 */
    fun dissolve(
        desktop: DesktopConfig,
        folderId: String,
    ): FolderResult {
        val normalized = normalize(desktop)
        val target =
            normalized.folders.find { it.id == folderId }
                ?: return FolderResult.Failure(FolderFailure.FOLDER_NOT_FOUND)
        val folderKey = "folder:$folderId"
        val order = normalized.order.flatMap { if (it == folderKey) target.memberKeys else listOf(it) }
        return FolderResult.Success(
            normalize(normalized.copy(folders = normalized.folders.filterNot { it.id == folderId }, order = order)),
        )
    }

    /** 幂等规整引用；不依赖应用安装或隐藏状态；旧无文件夹配置保持原实例。 */
    fun normalize(desktop: DesktopConfig): DesktopConfig {
        if (desktop.folders.isEmpty()) return desktop
        val ids = mutableSetOf<String>()
        val members = mutableSetOf<String>()
        val folders =
            desktop.folders.filter { it.id.isNotBlank() && ids.add(it.id) }.map { folder ->
                folder.copy(memberKeys = folder.memberKeys.filter { isAppKey(it) && members.add(it) })
            }
        val folderKeys = folders.map { "folder:${it.id}" }
        val order = desktop.order.filter { it !in members && (!it.startsWith("folder:") || it in folderKeys) }
        val normalized = desktop.copy(folders = folders, order = (order + folderKeys).distinct())
        return if (normalized == desktop) desktop else normalized
    }

    /** 在当前已有数字后缀的最大值后递增，避免与现有标识冲突。 */
    fun nextFolderId(desktop: DesktopConfig): String {
        val highest =
            desktop.folders
                .mapNotNull { Regex("[0-9]+$").find(it.id)?.value?.toBigIntegerOrNull() }
                .maxOrNull() ?: java.math.BigInteger.ZERO
        return "f${highest + java.math.BigInteger.ONE}"
    }

    private fun isAppKey(key: String): Boolean = key.startsWith("app:") && key.removePrefix("app:").isNotBlank()
}
