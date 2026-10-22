package com.shuiyu.octoberrain.core.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.shuiyu.octoberrain.core.common.ConfigStore
import com.shuiyu.octoberrain.core.common.IconOverrideStore
import com.shuiyu.octoberrain.core.common.IconTargets
import com.shuiyu.octoberrain.core.render.ImageDecoder
import com.shuiyu.octoberrain.core.render.ImageSizing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 单条目图标覆盖实现。
 *
 * 图片复制到 filesDir/icon-overrides，配置记录目标标识与路径；目标标识为应用
 * 包名或 `special:` 入口名。原图片文件不被修改，清除覆盖时仅删除副本并移除配置项。
 * 历史配置里带 `app:` 前缀的键在读取时归一，写回时统一为裸标识。
 */
class IconOverrideStoreImpl(
    private val context: Context,
    private val config: ConfigStore,
) : IconOverrideStore {
    /** 覆盖图标目录，懒创建。 */
    private val dir: File
        get() = File(context.filesDir, "icon-overrides").apply { mkdirs() }

    /** 覆盖变更计数，供展示方刷新图标缓存。 */
    private val versionState = MutableStateFlow(0)

    override val version: StateFlow<Int> = versionState.asStateFlow()

    /** 当前配置中的覆盖映射，键已归一为图标目标标识。 */
    override fun overrides(): Map<String, String> =
        config.state.value.appearance.iconOverrides.entries.associate {
            IconTargets.normalizeStoredKey(it.key) to it.value
        }

    /**
     * 为指定目标设置图标。
     *
     * 从 Uri 读取图片到独立副本，校验可解码且配置确认保存后才删除旧副本；
     * 失败不改变旧图、配置与版本，历史键在成功保存时归一。
     */
    override suspend fun setOverride(
        targetId: String,
        uri: Uri,
    ): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val applied =
                    IconOverrideSwap(
                        directory = dir,
                        openSource = { context.contentResolver.openInputStream(uri) },
                        isValidImage = { ImageDecoder.size(it.absolutePath) != null },
                        updateConfig = { saveOverride(targetId, it) },
                    ).execute(IconFileNames.fileName(targetId), pathOf(targetId))
                if (applied) versionState.value += 1
                applied
            }.getOrDefault(false)
        }

    /** 配置持久化可能被拒绝；必须读回新路径，不能只以 edit 返回判断成功。 */
    private fun saveOverride(
        targetId: String,
        path: String,
    ): Boolean {
        config.edit { current ->
            val overrides = current.appearance.iconOverrides
            val stale =
                overrides.keys.filter { key ->
                    key != targetId && IconTargets.normalizeStoredKey(key) == targetId
                }
            current.copy(
                appearance = current.appearance.copy(iconOverrides = overrides - stale + (targetId to path)),
            )
        }
        return pathOf(targetId) == path
    }

    /** 清除单个目标的覆盖图标，同时清理历史键与对应文件。 */
    override fun clearOverride(targetId: String) {
        val stale =
            config.state.value.appearance.iconOverrides.filterKeys { key ->
                IconTargets.normalizeStoredKey(key) == targetId
            }
        config.edit { current ->
            current.copy(
                appearance =
                    current.appearance.copy(
                        iconOverrides = current.appearance.iconOverrides - stale.keys,
                    ),
            )
        }
        stale.values.forEach { File(it).delete() }
        versionState.value += 1
    }

    /** 清除全部覆盖图标。 */
    override fun clearAll() {
        val paths =
            config.state.value.appearance.iconOverrides.values
                .toList()
        config.edit { current ->
            current.copy(appearance = current.appearance.copy(iconOverrides = emptyMap()))
        }
        paths.forEach { File(it).delete() }
        versionState.value += 1
    }

    /**
     * 读取覆盖图标。
     *
     * 按目标边长采样解码并居中裁切为正方形，失败返回 null 由调用方回退系统图标。
     */
    override fun loadOverride(
        targetId: String,
        sizePx: Int,
    ): Bitmap? {
        val path = pathOf(targetId)
        return if (path == null || !File(path).exists()) {
            null
        } else {
            runCatching {
                val size = ImageDecoder.size(path) ?: return@runCatching null
                val sample = ImageSizing.requestSample(size[0], size[1], sizePx, sizePx)
                val decoded = ImageDecoder.decodeWithSample(path, sample, keepAlpha = true) ?: return@runCatching null
                val side = minOf(decoded.width, decoded.height)
                val left = (decoded.width - side) / 2
                val top = (decoded.height - side) / 2
                val square = Bitmap.createBitmap(decoded, left, top, side, side)
                val scaled = Bitmap.createScaledBitmap(square, sizePx, sizePx, true)
                if (square != decoded) decoded.recycle()
                if (scaled != square) square.recycle()
                scaled
            }.getOrNull()
        }
    }

    /** 读取目标对应的已保存路径，兼容带前缀的历史键。 */
    private fun pathOf(targetId: String): String? =
        config.state.value.appearance.iconOverrides.entries
            .firstOrNull { (key, _) -> IconTargets.normalizeStoredKey(key) == targetId }
            ?.value
}
