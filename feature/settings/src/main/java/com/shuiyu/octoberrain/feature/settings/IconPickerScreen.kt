package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.IconTargets
import com.shuiyu.octoberrain.core.common.Screen
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkPage
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.core.render.RefreshScheduler
import com.shuiyu.octoberrain.core.render.ThumbCacheRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 单个入口的图标选择页。
 *
 * 参数 targetId 为应用包名或 special:<名称>，两者共用同一套覆盖存储与目录选择。
 * 顶部展示入口名与当前图标，可恢复默认图标；下方沿用美化模式的图片目录，
 * 按两列列出缩略图，点击图片即写入图标覆盖并返回上一页。
 * 目录位置与美化模式共用 [ImageDirectoryStore]，任一处更改目录后另一处同步。
 */
class IconPickerScreen(
    private val targetId: String,
) : Screen {
    /** 页面级协程作用域，随页面销毁取消。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 当前承载内容的页面容器，未创建或已销毁时为 null。 */
    private var pageRef: InkPage? = null

    /** 当前图片目录，进入页面时从共享存储读取。 */
    private var imageDir: File = ImageDirectoryStore.defaultDirectory

    /** 入口显示名称，创建页面时解析一次。 */
    private var appLabel: String = targetId

    override fun createView(context: Context): View {
        appLabel = IconTargets.labelOf(targetId, SettingsUi::appLabel)
        imageDir = ImageDirectoryStore.load(context)
        val page =
            InkPage(context, "$appLabel · 图标", showBack = true) {
                runCatching { AppServices.router.back() }
            }
        pageRef = page
        build(page, context)
        return page
    }

    override fun onDestroy() {
        scope.cancel()
        pageRef = null
    }

    /** 页面内容重建；已附着时推迟到下一帧，避免在触摸分发中移除视图。 */
    private fun rebuild() {
        val page = pageRef ?: return
        page.post {
            if (pageRef === page) {
                page.content.removeAllViews()
                build(page, page.context)
            }
        }
    }

    /** 构建页面内容：当前图标、目录信息与缩略图网格。 */
    private fun build(
        page: InkPage,
        context: Context,
    ) {
        val tokens = DesignRuntime.tokens
        page.content.addView(buildCurrentCard(context))
        page.content.addView(InkWidgets.spacer(context, tokens.gapLarge))
        page.content.addView(
            InkWidgets.caption(context, "图片目录").apply {
                setPadding(0, 0, 0, tokens.gap / 2)
            },
        )
        page.content.addView(buildDirectoryRow(context))
        page.content.addView(InkWidgets.spacer(context, tokens.gap))
        buildImageGrid(page, context)
    }

    /** 当前图标预览与恢复默认入口。 */
    private fun buildCurrentCard(context: Context): View {
        val tokens = DesignRuntime.tokens
        val card =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                background = InkWidgets.background(tokens.radius, InkColors.PAPER, tokens.stroke, InkColors.INK)
                setPadding(tokens.dp(14f), tokens.dp(14f), tokens.dp(14f), tokens.dp(10f))
            }
        val size = tokens.dp(64f)
        val preview =
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = false
            }
        card.addView(preview, LinearLayout.LayoutParams(size, size))
        card.addView(
            InkWidgets.caption(context, "当前图标").apply {
                gravity = Gravity.CENTER
                setPadding(0, tokens.gap / 2, 0, 0)
            },
        )
        val hasOverride =
            runCatching { AppServices.iconOverrides.overrides().containsKey(targetId) }.getOrDefault(false)
        card.addView(
            InkWidgets.caption(context, if (hasOverride) "当前使用自定义图片" else "当前使用系统图标").apply {
                gravity = Gravity.CENTER
                setPadding(0, tokens.gap / 4, 0, 0)
            },
        )
        card.addView(
            InkWidgets.textView(context, "恢复默认图标", tokens.textBody, InkColors.INK).apply {
                gravity = Gravity.CENTER
                setPadding(0, tokens.gap, 0, tokens.gap / 2)
                setOnClickListener { restoreDefault(context) }
            },
        )
        loadCurrentIcon(preview, size)
        return card
    }

    /** 读取当前生效图标：图标覆盖优先，其次应用图标或内置字形。 */
    private fun loadCurrentIcon(
        target: ImageView,
        sizePx: Int,
    ) {
        scope.launch {
            val bitmap =
                withContext(Dispatchers.IO) {
                    AppServices.iconOverrides.loadOverride(targetId, sizePx)
                        ?: runCatching {
                            val special = IconTargets.specialEntryOf(targetId)
                            if (special != null) {
                                AppServices.catalog.loadSpecialIcon(special, sizePx)
                            } else {
                                AppServices.catalog.loadIcon(targetId, sizePx)
                            }
                        }.getOrNull()
                }
            if (bitmap != null && target.isAttachedToWindow) target.setImageBitmap(bitmap)
        }
    }

    /** 目录路径与更改入口。 */
    private fun buildDirectoryRow(context: Context): View {
        val tokens = DesignRuntime.tokens
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = InkWidgets.background(tokens.radius, InkColors.PAPER, tokens.stroke, InkColors.INK)
                setPadding(tokens.gap, tokens.gap / 2, tokens.gap, tokens.gap / 2)
            }
        row.addView(
            InkWidgets.textView(context, imageDir.absolutePath, tokens.textCaption, InkColors.INK_2).apply {
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.MIDDLE
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            },
        )
        row.addView(
            InkWidgets.textView(context, "更改", tokens.textCaption, InkColors.INK).apply {
                setPadding(tokens.gap, tokens.gap / 3, 0, tokens.gap / 3)
                setOnClickListener { chooseDirectory(context) }
            },
        )
        return row
    }

    /** 两列缩略图网格，空目录时给出提示。 */
    private fun buildImageGrid(
        page: InkPage,
        context: Context,
    ) {
        val tokens = DesignRuntime.tokens
        val images = ImageDirectoryStore.images(imageDir, ImageDirectoryStore.MAX_IMAGES)
        if (images.isEmpty()) {
            page.content.addView(
                InkWidgets.body(context, "该目录没有图片，可以点「更改」切换目录。").apply {
                    textSize = tokens.textCaption
                },
            )
            return
        }
        val cellPx = imageCellPx()
        val caption = InkWidgets.caption(context, "点击图片即可替换图标")
        page.content.addView(caption)
        page.content.addView(InkWidgets.spacer(context, tokens.gap / 2))
        images.chunked(2).forEach { pair ->
            val row =
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    isBaselineAligned = false
                }
            pair.forEachIndexed { index, file ->
                row.addView(buildImageCell(context, file, cellPx), cellParams(1f))
                if (index == 0) row.addView(View(context), gapParams())
            }
            page.content.addView(
                row,
                LinearLayout
                    .LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { bottomMargin = tokens.gap / 2 },
            )
        }
    }

    /** 单个缩略图单元：方形图片与文件名。 */
    private fun buildImageCell(
        context: Context,
        file: File,
        cellPx: Int,
    ): View {
        val tokens = DesignRuntime.tokens
        val column =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setOnClickListener { applyImage(context, file) }
            }
        val image =
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = false
                background = InkWidgets.background(tokens.radiusSmall, InkColors.PAPER, tokens.stroke, InkColors.INK_4)
            }
        column.addView(image, LinearLayout.LayoutParams(cellPx, cellPx))
        column.addView(
            InkWidgets.textView(context, file.name, tokens.textSmall, InkColors.INK_2).apply {
                gravity = Gravity.CENTER
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, tokens.gap / 3, 0, 0)
                layoutParams =
                    LinearLayout.LayoutParams(
                        cellPx,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    )
            },
        )
        loadThumbnail(file.path, image, cellPx)
        return column
    }

    /** 异步采样解码缩略图，解码完成且视图仍附着时回填。 */
    private fun loadThumbnail(
        path: String,
        target: ImageView,
        sizePx: Int,
    ) {
        scope.launch {
            val bitmap: Bitmap? =
                withContext(Dispatchers.IO) {
                    ImageDirectoryStore.decodeSampled(target.context, path, sizePx)
                }
            if (bitmap != null && target.isAttachedToWindow) target.setImageBitmap(bitmap)
        }
    }

    /** 为当前入口设置图标覆盖，成功后返回上一页。 */
    private fun applyImage(
        context: Context,
        file: File,
    ) {
        if (!file.isFile) {
            SettingsUi.toast(context, "图片文件已不存在")
            return
        }
        scope.launch {
            val ok =
                runCatching {
                    AppServices.iconOverrides.setOverride(targetId, Uri.fromFile(file))
                }.getOrDefault(false)
            if (pageRef == null) return@launch
            if (ok) {
                ThumbCacheRegistry.invalidate(context, file)
                RefreshScheduler.notifyContentChanged()
                SettingsUi.toast(context, "图标已更换")
                runCatching { AppServices.router.back() }
            } else {
                SettingsUi.toast(context, "图标设置失败，请更换图片")
            }
        }
    }

    /** 清除当前入口的图标覆盖。 */
    private fun restoreDefault(context: Context) {
        scope.launch {
            runCatching { AppServices.iconOverrides.clearOverride(targetId) }
            if (pageRef == null) return@launch
            SettingsUi.toast(context, "已恢复默认图标")
            runCatching { AppServices.router.back() }
        }
    }

    /** 选择图片目录，系统选择器不可用时回退手动输入。 */
    private fun chooseDirectory(context: Context) {
        if (AppServices.hasPicker()) {
            val opened =
                runCatching {
                    AppServices.picker.pickDirectory { uri ->
                        if (pageRef == null) return@pickDirectory
                        val dir = uri?.let { ImageDirectoryStore.directoryOf(it) }
                        if (dir != null && dir.isDirectory) {
                            imageDir = dir
                            ImageDirectoryStore.save(context, dir)
                            rebuild()
                            RefreshScheduler.notifyContentChanged()
                        } else {
                            promptDirectory(context)
                        }
                    }
                }.getOrDefault(false)
            if (opened) return
        }
        promptDirectory(context)
    }

    /** 手动输入图片目录路径。 */
    private fun promptDirectory(context: Context) {
        InkDialogs.input(
            context,
            "图片目录",
            hint = "/storage/emulated/0/Pictures",
            initial = imageDir.absolutePath,
        ) { text ->
            val dir = File(text.trim())
            if (!dir.isDirectory) {
                SettingsUi.toast(context, "目录不存在")
                return@input
            }
            imageDir = dir
            ImageDirectoryStore.save(context, dir)
            rebuild()
            RefreshScheduler.notifyContentChanged()
        }
    }

    /** 缩略图边长：按屏宽减去页面内边距与列间空隙后两等分。 */
    private fun imageCellPx(): Int {
        val tokens = DesignRuntime.tokens
        val width = tokens.device.widthPx - tokens.pageGutter * 2
        return ((width - tokens.gap) / 2).coerceIn(tokens.dp(64f), tokens.dp(160f))
    }

    /** 等宽单元布局参数。 */
    private fun cellParams(weight: Float): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)

    /** 列间间隙布局参数。 */
    private fun gapParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(DesignRuntime.tokens.gap, 1)
}
