package com.shuiyu.octoberrain.feature.settings

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.LauncherEntry
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkGlyphs
import com.shuiyu.octoberrain.core.design.InkWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 左栏单行：图标、应用名与「已换」标记，选中行使用浅灰底。 */
internal fun BeautifyScreen.buildAppRow(
    context: Context,
    entry: LauncherEntry,
    overridden: Boolean,
): View {
    val tokens = DesignRuntime.tokens
    val row =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isBaselineAligned = false
            minimumHeight = tokens.listRowHeight
            setPadding(tokens.gap / 2, tokens.gap / 4, tokens.gap / 2, tokens.gap / 4)
            if (entry.key == selectedKey) setBackgroundColor(InkColors.INK_4)
            isClickable = true
            isFocusable = true
            setOnClickListener { selectRow(entry.key) }
        }

    val iconSize = tokens.dp(32f)
    val packageName = entry.packageName
    if (packageName != null) {
        val image =
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(tokens.dp(2f), tokens.dp(2f), tokens.dp(2f), tokens.dp(2f))
            }
        row.addView(
            image,
            LinearLayout.LayoutParams(iconSize, iconSize).apply { rightMargin = tokens.gap / 2 },
        )
        loadAppIcon(packageName, image, iconSize)
    } else {
        val glyph = InkGlyphs.view(context, InkGlyphs.Kind.FOLDER, tokens.dp(20f))
        row.addView(
            glyph,
            LinearLayout.LayoutParams(iconSize, iconSize).apply { rightMargin = tokens.gap / 2 },
        )
    }

    row.addView(
        InkWidgets.textView(context, entry.label, tokens.textBody, InkColors.INK).apply {
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            layoutParams =
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                )
        },
    )
    if (overridden) {
        row.addView(
            InkWidgets.textView(context, "已换", tokens.textCaption, InkColors.INK_2).apply {
                setPadding(tokens.gap / 2, 0, 0, 0)
            },
        )
    }
    return row
}

/** 异步读取应用图标，优先使用已设置的覆盖图标。 */
internal fun BeautifyScreen.loadAppIcon(
    packageName: String,
    target: ImageView,
    sizePx: Int,
) {
    scope.launch {
        val bitmap =
            withContext(Dispatchers.IO) {
                runCatching { AppServices.iconOverrides.loadOverride(packageName, sizePx) }
                    .getOrNull()
                    ?: runCatching { AppServices.catalog.loadIcon(packageName, sizePx) }
                        .getOrNull()
            }
        if (bitmap != null && root != null && target.isAttachedToWindow) {
            target.setImageBitmap(bitmap)
        }
    }
}
