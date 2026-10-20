package com.shuiyu.octoberrain.feature.files.ui.edit

import android.graphics.Bitmap
import com.shuiyu.octoberrain.feature.files.ui.Gray
import com.shuiyu.octoberrain.feature.files.ui.GrayParams

/**
 * 滤镜页签的控制条定义与预览计算。
 *
 * 第一行为五档滤镜，第二行为三档对比度；预览仅在缩小后的像素数组上计算，
 * 避免整幅原图参与逐像素运算。
 */
internal object FilterPanel {
    /**
     * 生成滤镜页签的控制条行。
     *
     * @param state 当前界面状态，用于标记选中的滤镜与对比度。
     * @param onFilter 滤镜档位选择回调。
     * @param onContrast 对比度档位选择回调。
     * @return 两行按钮定义，矩形由视图布局阶段补充。
     */
    fun rows(
        state: EditUiState,
        onFilter: (Int) -> Unit,
        onContrast: (Int) -> Unit,
    ): List<List<EditChip>> {
        val filters =
            EditUiState.FILTER_LABELS.mapIndexed { index, label ->
                EditChip(
                    label = label,
                    selected = state.filterLevel == index,
                    action = { onFilter(index) },
                )
            }
        val contrasts =
            EditUiState.CONTRAST_LABELS.mapIndexed { index, label ->
                EditChip(
                    label = label,
                    selected = state.contrastLevel == index,
                    action = { onContrast(index) },
                )
            }
        return listOf(filters, contrasts)
    }

    /**
     * 把位图缩放到预览长边后执行灰度滤镜。
     *
     * @param base 几何变换后的位图，不在此释放。
     * @param filterLevel 滤镜档位，取值 0 至 4。
     * @param contrastLevel 对比度档位，取值 0 至 2。
     * @return 新建的灰度位图，调用方负责回收。
     */
    fun bitmap(
        base: Bitmap,
        filterLevel: Int,
        contrastLevel: Int,
    ): Bitmap {
        val size = PreviewSizing.previewSize(base.width, base.height)
        val scaled =
            if (size[0] == base.width && size[1] == base.height) {
                base
            } else {
                Bitmap.createScaledBitmap(base, size[0], size[1], true)
            }
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        val width = scaled.width
        val height = scaled.height
        val output =
            Gray.apply(
                pixels,
                width,
                height,
                GrayParams(filterLevel, contrastLevel, false),
            )
        if (scaled !== base) scaled.recycle()
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }
}
