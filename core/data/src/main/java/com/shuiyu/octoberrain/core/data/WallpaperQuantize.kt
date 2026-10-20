package com.shuiyu.octoberrain.core.data

import android.graphics.Bitmap
import com.shuiyu.octoberrain.core.render.Ditherer

/**
 * 壁纸量化：把外观配置中的量化档位映射为灰阶级数，并对壁纸位图执行抖动。
 *
 * 档位语义为 0 关闭、1 二级灰、2 四级灰、3 八级灰；非法档位按关闭处理。
 * 抖动复用 [Ditherer] 的误差扩散实现，避免出现第二套量化算法。
 */
internal object WallpaperQuantize {
    /** 关闭量化的档位值。 */
    const val OFF = 0

    /** 二级灰对应的量化档位。 */
    const val LEVEL_TWO = 1

    /** 四级灰对应的量化档位。 */
    const val LEVEL_FOUR = 2

    /** 八级灰对应的量化档位。 */
    const val LEVEL_EIGHT = 3

    /**
     * 把量化档位映射为 [Ditherer.dither] 使用的灰阶级数。
     *
     * 参数 quantize 为配置中的量化档位；关闭或非法值返回 0，
     * 其余档位返回 2、4、8，作为抖动调色板的级数。
     */
    fun levelsFor(quantize: Int): Int =
        when (quantize) {
            LEVEL_TWO -> 2
            LEVEL_FOUR -> 4
            LEVEL_EIGHT -> 8
            else -> 0
        }

    /**
     * 按量化档位处理壁纸位图。
     *
     * 参数 source 为已裁剪到目标尺寸的壁纸位图；quantize 为量化档位。
     * 档位为关闭或 [source] 为空时原样返回；其余档位返回抖动后的新位图，
     * 原图由调用方按持有关系回收。抖动失败时回退原图，保证锁屏与桌面不空白。
     */
    fun apply(
        source: Bitmap?,
        quantize: Int,
    ): Bitmap? =
        source?.let { bitmap ->
            val levels = levelsFor(quantize)
            if (levels == 0) {
                bitmap
            } else {
                runCatching { Ditherer.dither(bitmap, levels) }.getOrNull() ?: bitmap
            }
        }
}
