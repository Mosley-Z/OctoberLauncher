package com.shuiyu.octoberrain.feature.widgets

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.IMAGE_CAROUSEL_OPTIONS
import com.shuiyu.octoberrain.core.common.editWidgetPage
import com.shuiyu.octoberrain.core.common.effectiveImageCarouselMinutes
import com.shuiyu.octoberrain.core.common.effectiveImageSwipeDirection
import com.shuiyu.octoberrain.core.common.forWidgetPage
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.render.RefreshScheduler
import com.shuiyu.octoberrain.core.render.ThumbCacheRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 图片组件的选择、清除与轮播设置，组件本身和组件库共用。 */
internal object ImageWidgetSettings {
    /** 显示组件库中已选图片组件的设置。 */
    fun show(
        context: Context,
        scope: CoroutineScope,
        pageIndex: Int = 0,
    ) {
        val desktop =
            AppServices.config.state.value.desktop
                .forWidgetPage(pageIndex)
        val minutes = desktop.effectiveImageCarouselMinutes
        val swipeLabel = SWIPE_LABELS[desktop.effectiveImageSwipeDirection]
        InkDialogs.choose(
            context,
            "图片组件",
            listOf("选择 / 更换图片", "清除组件图片", "轮播间隔：$minutes 分钟", "切换方向：$swipeLabel"),
        ) { index ->
            when (index) {
                0 -> choose(context, scope, pageIndex = pageIndex)
                1 -> clear(context, pageIndex)
                2 ->
                    InkDialogs.choose(context, "轮播间隔", IMAGE_CAROUSEL_OPTIONS.map { "$it 分钟" }) { selected ->
                        AppServices.config.edit { config ->
                            config.copy(
                                desktop =
                                    config.desktop.editWidgetPage(
                                        pageIndex,
                                    ) { it.copy(imageCarouselMinutes = IMAGE_CAROUSEL_OPTIONS[selected]) },
                            )
                        }
                        changed()
                    }
                3 ->
                    InkDialogs.choose(context, "切换方向", SWIPE_LABELS) { selected ->
                        AppServices.config.edit { config ->
                            config.copy(
                                desktop =
                                    config.desktop.editWidgetPage(
                                        pageIndex,
                                    ) { it.copy(imageSwipeDirection = selected) },
                            )
                        }
                        changed()
                    }
            }
        }
    }

    /** 切换方向在设置中的显示顺序，与 `IMAGE_SWIPE_*` 常量取值一致。 */
    private val SWIPE_LABELS: List<String> = listOf("关闭", "左右", "上下", "上下左右")

    /** 复制并核实图片后再替换配置；取消或失败保留原图。 */
    fun choose(
        context: Context,
        scope: CoroutineScope,
        pageIndex: Int = 0,
        onChanged: () -> Unit = {},
    ) {
        pick(context, scope) { uri ->
            scope.launch {
                val target =
                    withContext(Dispatchers.IO) {
                        copyImage(context, uri, pageIndex)
                    }
                if (target == null) {
                    AppServices.router.toast("图片读取失败")
                    return@launch
                }
                WidgetImageSource.retainAccess(context, uri)
                val previous =
                    AppServices.config.state.value.desktop
                        .forWidgetPage(pageIndex)
                        .widgetImagePath
                AppServices.config.edit { config ->
                    config.copy(
                        desktop =
                            config.desktop.editWidgetPage(
                                pageIndex,
                            ) { it.copy(widgetImagePath = target.absolutePath, widgetImageSourceUri = uri.toString()) },
                    )
                }
                val saved =
                    AppServices.config.state.value.desktop
                        .forWidgetPage(pageIndex)
                if (saved.widgetImagePath != target.absolutePath || saved.widgetImageSourceUri != uri.toString()) {
                    target.delete()
                    AppServices.router.toast("图片设置未保存，请重试")
                    return@launch
                }
                deleteUnusedCopy(context, previous)
                changed()
                onChanged()
                AppServices.router.toast("已更新图片组件")
            }
        }
    }

    /** 将来源复制到当前页私有目录并校验图片；失败时清理本轮临时副本。 */
    private fun copyImage(
        context: Context,
        uri: Uri,
        pageIndex: Int,
    ): File? {
        val directory =
            File(
                context.filesDir,
                if (pageIndex ==
                    0
                ) {
                    "widget-image"
                } else {
                    "widget-image/page-$pageIndex"
                },
            ).apply { mkdirs() }
        val candidate = File.createTempFile("widget-", ".img", directory)
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                candidate.outputStream().use { output -> input.copyTo(output) }
            } ?: error("无法读取图片")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(candidate.absolutePath, bounds)
            check(bounds.outWidth > 0 && bounds.outHeight > 0)
            candidate
        }.getOrElse {
            candidate.delete()
            null
        }
    }

    /** 系统选择器不可用时扫描有限深度和数量的本地图片；取消不启动回退。 */
    private fun pick(
        context: Context,
        scope: CoroutineScope,
        onPicked: (Uri) -> Unit,
    ) {
        if (AppServices.hasPicker()) {
            val opened =
                runCatching {
                    AppServices.picker.pickForImport(arrayOf("image/*")) { uri ->
                        if (uri != null) onPicked(uri)
                    }
                }.getOrDefault(false)
            if (opened) return
        }
        scope.launch {
            val files = withContext(Dispatchers.IO) { localImages(context) }
            if (files.isEmpty()) {
                InkDialogs.confirm(
                    context,
                    "未找到可用图片",
                    "请把图片放入 Pictures、Download、DCIM 或应用外部文件目录，或授予存储访问权限。",
                    confirmLabel = "存储设置",
                    onConfirm = { runCatching { AppServices.permissions.openStorageSettings() } },
                )
                return@launch
            }
            val base = Environment.getExternalStorageDirectory().absolutePath
            InkDialogs.choose(context, "选择组件图片", files.map { it.absolutePath.removePrefix("$base/") }) { index ->
                onPicked(Uri.fromFile(files[index]))
            }
        }
    }

    /** 最多扫描三层目录、展示120个图片候选，不进入全盘。 */
    private fun localImages(context: Context): List<File> {
        val public = Environment.getExternalStorageDirectory()
        val roots =
            listOf("Pictures", "Download", "DCIM", "Documents", "Books", "Fonts").map { File(public, it) } +
                context.getExternalFilesDirs(null).filterNotNull()
        return roots
            .asSequence()
            .filter { it.isDirectory }
            .flatMap { root ->
                root
                    .walkTopDown()
                    .maxDepth(MAX_DEPTH)
                    .onFail { _, _ -> }
                    .asSequence()
            }.filter { it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS }
            .take(MAX_CANDIDATES)
            .sortedByDescending { it.lastModified() }
            .toList()
    }

    /** 清除当前选择，仅删除应用图片目录中对应的单个副本。 */
    private fun clear(
        context: Context,
        pageIndex: Int,
    ) {
        val path =
            AppServices.config.state.value.desktop
                .forWidgetPage(pageIndex)
                .widgetImagePath
        AppServices.config.edit { config ->
            config.copy(
                desktop =
                    config.desktop.editWidgetPage(pageIndex) {
                        it.copy(widgetImagePath = null, widgetImageSourceUri = null)
                    },
            )
        }
        val saved =
            AppServices.config.state.value.desktop
                .forWidgetPage(pageIndex)
        if (saved.widgetImagePath != null || saved.widgetImageSourceUri != null) {
            AppServices.router.toast("图片设置未保存，请重试")
            return
        }
        deleteUnusedCopy(context, path)
        changed()
        AppServices.router.toast("组件图片已清除")
    }

    /** 仅删除已无任何页面引用的组件私有副本，不删除用户来源文件。 */
    private fun deleteUnusedCopy(
        context: Context,
        path: String?,
    ) {
        val desktop = AppServices.config.state.value.desktop
        val references =
            listOfNotNull(desktop.widgetImagePath) + desktop.pageWidgets.values.mapNotNull { it.widgetImagePath }
        WidgetImageFiles.unusedManaged(path, context.filesDir, references)?.let { file ->
            ThumbCacheRegistry.invalidate(context, file)
            file.delete()
        }
    }

    private const val MAX_CANDIDATES = 120
    private const val MAX_DEPTH = 4
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "bmp")

    private fun changed() {
        RefreshScheduler.notifyContentChanged()
        AppServices.refresh.requestPartial()
    }
}
