package com.shuiyu.octoberrain.core.data

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import com.shuiyu.octoberrain.core.common.ConfigStore
import com.shuiyu.octoberrain.core.common.FontInfo
import com.shuiyu.octoberrain.core.common.FontProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * 字体仓库：管理内置字体标识与导入的 TTF 文件。
 *
 * 导入的字体复制到应用私有目录，配置中保存文件路径；
 * 加载失败时返回默认字体，避免界面因单个字体损坏而崩溃。
 */
class FontsRepository(
    private val context: Context,
    private val config: ConfigStore,
) : FontProvider {
    private val fontDir = File(context.filesDir, "fonts").apply { mkdirs() }

    private var labelCache: Typeface? = null
    private var labelCachedPath: String? = null

    override fun labelTypeface(): Typeface? {
        val path = config.state.value.appearance.labelFontPath ?: return null
        if (path == labelCachedPath) return labelCache
        labelCachedPath = path
        labelCache = loadFile(path)
        return labelCache
    }

    override fun clockTypeface(id: String): Typeface {
        if (!id.startsWith("file:")) return Typeface.DEFAULT
        val path = id.removePrefix("file:")
        return loadFile(path) ?: Typeface.DEFAULT
    }

    override fun fonts(): List<FontInfo> {
        val imported =
            fontDir
                .listFiles { file -> file.isFile && file.extension.lowercase(Locale.ROOT) == "ttf" }
                ?.sortedBy { it.name }
                ?.map { file ->
                    FontInfo(
                        id = "file:${file.absolutePath}",
                        label = file.nameWithoutExtension,
                        path = file.absolutePath,
                    )
                }
                ?: emptyList()
        return FontInfo.BUILTIN + imported
    }

    override suspend fun importFont(uri: Uri): FontInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val displayName = queryDisplayName(uri) ?: "imported_${System.currentTimeMillis()}"
                val safe = displayName.replace(Regex("[^A-Za-z0-9._\\u4e00-\\u9fa5-]"), "_")
                val target = File(fontDir, if (safe.endsWith(".ttf", true)) safe else "$safe.ttf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(target).use { output -> input.copyTo(output) }
                } ?: return@runCatching null
                val typeface = Typeface.createFromFile(target) ?: return@runCatching null
                typeface // 验证文件可用
                FontInfo(
                    id = "file:${target.absolutePath}",
                    label = target.nameWithoutExtension,
                    path = target.absolutePath,
                )
            }.getOrNull()
        }

    /** 通过内容解析器查询文件名。 */
    private fun queryDisplayName(uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
        }.getOrNull()

    /** 从文件加载字体，异常返回 null。 */
    private fun loadFile(path: String): Typeface? =
        runCatching {
            val file = File(path)
            if (file.isFile) Typeface.createFromFile(file) else null
        }.getOrNull()
}
