package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkDialogs
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.core.render.RefreshScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// 美化模式右栏：壁纸与图片目录。
//
// 右栏在「图片目录」与「已用图标」之间切换，按两列网格展示缩略图，
// 点击缩略图为左栏选中应用设置图标。目录选择优先走系统选择器，
// 选择器不可用时回退到手工输入路径。

/** 右栏缩略图条目：key 用于覆盖模式定位，path 为本地图片路径。 */
internal data class ImageCell(
    val key: String,
    val path: String,
    val title: String,
    val subtitle: String?,
)

/** 右栏容器：来源切换、目录信息、缩略图网格。 */
internal fun BeautifyScreen.buildImageColumn(context: Context): View {
    val tokens = DesignRuntime.tokens
    val column =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background =
                InkWidgets.background(
                    tokens.radius,
                    InkColors.PAPER,
                    tokens.stroke,
                    InkColors.INK,
                )
            setPadding(tokens.gap / 2, tokens.gap / 2, tokens.gap / 2, tokens.gap / 2)
        }

    val switchRow =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isBaselineAligned = false
        }
    switchRow.addView(
        chip(context, "图片目录", !showingOverrides) {
            if (showingOverrides) {
                showingOverrides = false
                render()
            }
        },
    )
    switchRow.addView(gapView(context), LinearLayout.LayoutParams(tokens.gap / 2, 1))
    switchRow.addView(
        chip(context, "已用图标", showingOverrides) {
            if (!showingOverrides) {
                showingOverrides = true
                render()
            }
        },
    )
    column.addView(switchRow)

    val overrides = overrides()
    val cells: List<ImageCell> =
        if (showingOverrides) {
            overrideCells(overrides)
        } else {
            directoryCells()
        }

    val pathRow =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isBaselineAligned = false
            setPadding(tokens.gap / 4, tokens.gap / 2, 0, tokens.gap / 2)
        }
    pathRow.addView(
        InkWidgets
            .caption(
                context,
                if (showingOverrides) {
                    "已用图标 · ${overrides.size} 张"
                } else {
                    imageDir.path
                },
            ).apply {
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.MIDDLE
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f,
                    )
            },
    )
    if (!showingOverrides) {
        pathRow.addView(chip(context, "更改", false) { chooseDirectory() })
    }
    column.addView(pathRow)

    column.addView(
        ScrollView(context).apply {
            isFillViewport = false
            addView(buildImageGrid(context, cells))
        },
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
    )
    return column
}

/** 缩略图两列网格，单元格按列宽自适应并限制在 96dp 以内。 */
private fun BeautifyScreen.buildImageGrid(
    context: Context,
    cells: List<ImageCell>,
): View {
    val tokens = DesignRuntime.tokens
    val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    if (cells.isEmpty()) {
        grid.addView(
            InkWidgets
                .caption(
                    context,
                    if (showingOverrides) {
                        "尚未设置图标覆盖。切换到图片目录，点击缩略图为左侧选中应用换图标。"
                    } else {
                        "目录内没有图片，可点「更改目录」选择其他位置。"
                    },
                ).apply { setPadding(tokens.gap / 2, tokens.gap / 2, 0, 0) },
        )
        return grid
    }

    val cellPx = imageCellPx()
    cells.chunked(2).forEach { pair ->
        val row =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
                setPadding(0, tokens.gap / 4, 0, tokens.gap / 4)
            }
        pair.forEach { cell ->
            row.addView(
                buildImageCell(context, cell, cellPx),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
        }
        if (pair.size == 1) {
            row.addView(
                View(context),
                LinearLayout.LayoutParams(0, 1, 1f),
            )
        }
        grid.addView(row)
    }
    return grid
}

/** 单个缩略图单元格：图片、名称与黑框选中标记。 */
private fun BeautifyScreen.buildImageCell(
    context: Context,
    cell: ImageCell,
    cellPx: Int,
): View {
    val tokens = DesignRuntime.tokens
    val box =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            isFocusable = true
            setOnClickListener { applyImage(cell) }
        }
    val image =
        ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background =
                InkWidgets.background(
                    tokens.radiusSmall,
                    InkColors.PAPER,
                    if (cell.path == selectedImagePath) tokens.stroke * 3 else tokens.stroke,
                    InkColors.INK,
                )
            setPadding(tokens.dp(3f), tokens.dp(3f), tokens.dp(3f), tokens.dp(3f))
        }
    box.addView(
        image,
        LinearLayout.LayoutParams(cellPx, cellPx),
    )
    box.addView(
        InkWidgets.textView(context, cell.title, tokens.textSmall, InkColors.INK).apply {
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(0, tokens.dp(2f), 0, 0)
        },
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ),
    )
    loadThumbnail(cell.path, image, cellPx)
    return box
}

/** 按列宽计算缩略图边长，上限 96dp。 */
private fun BeautifyScreen.imageCellPx(): Int {
    val tokens = DesignRuntime.tokens
    val widthPx = tokens.device.widthPx
    val gutter = tokens.pageGutter
    val inner = ((widthPx - gutter * 2) * 0.48f - tokens.gap * 2).toInt()
    val cell = (inner - tokens.gap / 2) / 2
    return cell.coerceIn(tokens.dp(40f), tokens.dp(96f))
}

/** 异步解码缩略图，按目标边长采样，禁止整图载入。 */
private fun BeautifyScreen.loadThumbnail(
    path: String,
    target: ImageView,
    sizePx: Int,
) {
    scope.launch {
        val bitmap =
            withContext(Dispatchers.IO) {
                ImageDirectoryStore.decodeSampled(target.context, path, sizePx)
            }
        if (bitmap != null && root != null && target.isAttachedToWindow) {
            target.setImageBitmap(bitmap)
        }
    }
}

/** 图片目录内的条目，按修改时间倒序。 */
private fun BeautifyScreen.directoryCells(): List<ImageCell> =
    ImageDirectoryStore
        .images(imageDir, ImageDirectoryStore.MAX_IMAGES)
        .map { file -> ImageCell(file.path, file.path, file.name, null) }

/** 已设置的图标覆盖，按应用名排序。 */
private fun BeautifyScreen.overrideCells(overrides: Map<String, String>): List<ImageCell> =
    overrides.entries
        .sortedBy { SettingsUi.appLabel(it.key).lowercase() }
        .map { entry ->
            ImageCell(
                entry.key,
                entry.value,
                SettingsUi.appLabel(entry.key),
                File(entry.value).name,
            )
        }

/** 选择图片目录，系统选择器失败时回退手动输入路径。 */
internal fun BeautifyScreen.chooseDirectory() {
    val context = root?.context ?: return
    if (AppServices.hasPicker()) {
        val opened =
            runCatching {
                AppServices.picker.pickDirectory { uri ->
                    if (root == null) return@pickDirectory
                    val dir = uri?.let { ImageDirectoryStore.directoryOf(it) }
                    if (dir != null && dir.isDirectory) {
                        imageDir = dir
                        ImageDirectoryStore.save(requireNotNull(root).context, dir)
                        showingOverrides = false
                        renderNow()
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
internal fun BeautifyScreen.promptDirectory(context: Context) {
    InkDialogs.input(
        context,
        "图片目录",
        hint = "/storage/emulated/0/Pictures",
        initial = imageDir.path,
    ) { text ->
        val dir = File(text.trim())
        val current = root?.context ?: return@input
        if (!dir.isDirectory) {
            SettingsUi.toast(current, "目录不存在")
            return@input
        }
        imageDir = dir
        ImageDirectoryStore.save(current, dir)
        showingOverrides = false
        renderNow()
        RefreshScheduler.notifyContentChanged()
    }
}
