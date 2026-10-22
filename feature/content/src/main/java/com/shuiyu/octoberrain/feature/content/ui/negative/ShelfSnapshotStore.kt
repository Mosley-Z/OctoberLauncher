package com.shuiyu.octoberrain.feature.content.ui.negative

import com.shuiyu.octoberrain.core.common.AppJson
import com.shuiyu.octoberrain.core.common.BookItem
import com.shuiyu.octoberrain.core.common.WereadShelfItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 仅代表用户书架的已保存展示，不与其他模块的同步缓存混用。 */
@Serializable
internal data class ShelfSnapshot(
    val directory: String = "",
    val updatedAt: Long = 0,
    val attemptedAt: Long = 0,
    val local: List<BookItem> = emptyList(),
    val weread: List<WereadShelfItem> = emptyList(),
)

/** 每种书源保存一份快照，本地快照还必须匹配目录。 */
internal class ShelfSnapshotStore(
    private val root: File,
) {
    fun load(
        source: String,
        directory: String,
    ): ShelfSnapshot? {
        val saved =
            runCatching {
                AppJson.format.decodeFromString(ShelfSnapshot.serializer(), file(source).readText())
            }.getOrNull()
        return saved?.takeIf { source != ShelfSourceSupport.LOCAL || it.directory == directory }
    }

    fun save(
        source: String,
        snapshot: ShelfSnapshot,
    ) {
        root.mkdirs()
        val pending = File(root, "$source.tmp")
        pending.writeText(AppJson.format.encodeToString(ShelfSnapshot.serializer(), snapshot))
        Files.move(pending.toPath(), file(source).toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun file(source: String): File = File(root, "${ShelfSourceSupport.normalize(source)}.json")
}
