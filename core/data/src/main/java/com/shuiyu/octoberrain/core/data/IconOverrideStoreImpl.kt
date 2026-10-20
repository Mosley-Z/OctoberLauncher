package com.shuiyu.octoberrain.core.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.shuiyu.octoberrain.core.common.ConfigStore
import com.shuiyu.octoberrain.core.common.IconOverrideStore
import com.shuiyu.octoberrain.core.render.ImageDecoder
import com.shuiyu.octoberrain.core.render.ImageSizing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 单应用图标覆盖实现。
 *
 * 图片复制到 filesDir/icon-overrides，配置只记录包名与路径；
 * 原图片文件不被修改，清除覆盖时仅删除副本并移除配置项。
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

    /** 当前配置中的覆盖映射。 */
    override fun overrides(): Map<String, String> = config.state.value.appearance.iconOverrides

    /**
     * 为指定应用设置图标。
     *
     * 从 Uri 读取图片并复制到私有目录，校验可解码后写入配置；
     * 已存在旧图时先删除，避免残留文件。
     */
    override suspend fun setOverride(
        packageName: String,
        uri: Uri,
    ): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = File(dir, "${safeName(packageName)}.png")
                val copied =
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                        true
                    } ?: false
                if (!copied) return@withContext false
                if (ImageDecoder.size(target.absolutePath) == null) {
                    target.delete()
                    return@withContext false
                }
                // 旧扩展名可能不同，清理同包名的其他副本
                dir.listFiles()?.forEach { file ->
                    if (file != target && file.nameWithoutExtension == safeName(packageName)) file.delete()
                }
                config.edit { current ->
                    current.copy(
                        appearance =
                            current.appearance.copy(
                                iconOverrides = current.appearance.iconOverrides + (packageName to target.absolutePath),
                            ),
                    )
                }
                versionState.value += 1
                true
            }.getOrDefault(false)
        }

    /** 清除单个应用的覆盖图标。 */
    override fun clearOverride(packageName: String) {
        val path = config.state.value.appearance.iconOverrides[packageName]
        config.edit { current ->
            current.copy(
                appearance =
                    current.appearance.copy(
                        iconOverrides = current.appearance.iconOverrides - packageName,
                    ),
            )
        }
        path?.let { File(it).delete() }
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
        packageName: String,
        sizePx: Int,
    ): Bitmap? {
        val path = config.state.value.appearance.iconOverrides[packageName] ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return runCatching {
            val size = ImageDecoder.size(path) ?: return null
            val sample = ImageSizing.requestSample(size[0], size[1], sizePx, sizePx)
            val decoded = ImageDecoder.decodeWithSample(path, sample, keepAlpha = true) ?: return null
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

    /** 把包名转换为可用于文件名的安全字符串。 */
    private fun safeName(packageName: String): String = packageName.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
