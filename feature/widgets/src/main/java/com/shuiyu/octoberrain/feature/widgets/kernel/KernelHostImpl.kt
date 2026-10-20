package com.shuiyu.octoberrain.feature.widgets.kernel

import android.graphics.Bitmap
import android.util.LruCache
import com.inkread.weekread.kernel.KernelHost
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.WereadSection
import com.shuiyu.octoberrain.core.render.WereadCoverStore
import com.shuiyu.octoberrain.feature.widgets.WereadBoardKernelHost
import java.io.File
import kotlin.math.max

/**
 * 内核宿主实现：向微读内核提供私有目录、缩放系数、重绘入口、页面跳转、桌面开关与书封位图。
 *
 * 目录固定为应用私有目录下的 weread 子目录，首次访问时创建；
 * 内核传入的页面标识按字符串映射到应用路由，未识别的标识落到微信读书书架；
 * 书封按书目编号缓存，避免每次绘制重新解码。
 *
 * @param host 组件转交的宿主接口，提供上下文与重绘入口
 */
internal class KernelHostImpl(
    private val host: WereadBoardKernelHost,
    private val onRedraw: () -> Unit = { host.requestDraw() },
) : KernelHost {
    /** 内核数据目录，首次读取时创建。 */
    private val storeDir: File by lazy {
        File(host.context.filesDir, STORE_DIR_NAME).apply { if (!exists()) mkdirs() }
    }

    /** 书封缓存，按书目编号索引，容量取可用内存的八分之一。 */
    private val coverCache: LruCache<String, Bitmap> by lazy {
        val maxBytes = (Runtime.getRuntime().maxMemory() / COVER_CACHE_DIVISOR).toInt()
        object : LruCache<String, Bitmap>(maxBytes) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int = value.byteCount
        }
    }

    /** 返回内核数据目录。 */
    override fun filesDir(): File = storeDir

    /**
     * 返回内核排版单位。
     *
     * 与微读 `InkTheme.unitPx` 同源，取屏幕长边像素乘以 0.0015，480×800 设备为 1.2。
     * 组件已把画布缩放到 368 逻辑宽，逻辑坐标内 1 号字对应 1.2 像素，与原版卡片相同。
     */
    override fun scale(): Float {
        val metrics = host.context.resources.displayMetrics
        return max(metrics.widthPixels, metrics.heightPixels) * UNIT_RATIO
    }

    /** 请求组件重绘，内核在数据时刻变化时调用。 */
    override fun requestRedraw() {
        onRedraw()
    }

    /**
     * 打开内核指定的页面。
     *
     * 路由尚未装配时直接返回，避免组件初始化期间的空引用。
     *
     * @param page 内核页面标识，取值 desk、bookshelf、bill、assets、fingerprint、book、note、todo
     */
    override fun openPage(page: String) {
        val destination = destinationOf(page)
        if (AppServices.hasRouter()) AppServices.router.navigate(destination)
    }

    /** 返回微信读书密钥，未配置时为空字符串。 */
    override fun apiKey(): String =
        if (AppServices.isReady()) AppServices.config.state.value.integrations.wereadApiKey else ""

    /**
     * 返回桌面按钮是否可用。
     *
     * 整体卡片不承载「打开墨台」入口，按钮绘制与命中区域一并关闭，
     * 与抬头只保留形态切换和更新于两块一致。
     */
    override fun deskEnabled(): Boolean = false

    /** 返回桌面按钮文案。 */
    override fun deskOpenLabel(): String = DESK_OPEN_LABEL

    /**
     * 读取书封位图，按书目编号缓存。
     *
     * 本地文件直接解码；网络地址先查封面缓存目录，缺失时排队后台下载，下载完成后请求
     * 组件重绘，本次返回空以便首帧先用书名占位。
     *
     * @param bookId 书目编号，作为缓存键
     * @param path 封面地址或本地路径
     * @return 书封位图，尚未缓存或解码失败时为空
     */
    fun coverBitmap(
        bookId: String,
        path: String?,
    ): Bitmap? {
        val usable = bookId.isNotEmpty() && !path.isNullOrBlank()
        val cached = if (usable) coverCache.get(bookId) else null
        val decoded =
            if (usable && cached == null) {
                WereadCoverStore.bitmap(host.context, bookId, path.orEmpty(), COVER_MAX_PX) {
                    host.requestDraw()
                }
            } else {
                null
            }
        return cached ?: decoded?.also { coverCache.put(bookId, it) }
    }

    /**
     * 按页面标识取路由目标。
     *
     * 资产与指纹没有独立页面，落在负一屏；账单落在微信读书账单分段。
     *
     * @param page 内核页面标识
     * @return 应用路由目标
     */
    private fun destinationOf(page: String): Destination =
        when (page) {
            PAGE_DESK -> Destination.Negative
            PAGE_BOOKSHELF -> Destination.Bookshelf
            PAGE_BILL -> Destination.WereadTab(WereadSection.BILL)
            PAGE_ASSETS -> Destination.Negative
            PAGE_FINGERPRINT -> Destination.Negative
            PAGE_BOOK -> Destination.Book
            PAGE_NOTE -> Destination.Note
            PAGE_TODO -> Destination.Todo
            else -> Destination.Bookshelf
        }

    /** 宿主适配常量。 */
    private companion object {
        /** 内核数据目录名。 */
        const val STORE_DIR_NAME: String = "weread"

        /** 排版单位相对屏幕长边像素的比例，与微读 `InkTheme.UNIT_RATIO` 一致。 */
        const val UNIT_RATIO: Float = 0.0015f

        /** 桌面按钮文案。 */
        const val DESK_OPEN_LABEL: String = "打开墨台"

        /** 书封解码像素上限。 */
        const val COVER_MAX_PX: Int = 256

        /** 书封缓存容量相对可用内存的分母。 */
        const val COVER_CACHE_DIVISOR: Long = 8L

        /** 墨单页面标识。 */
        const val PAGE_DESK: String = "desk"

        /** 微信读书书架页面标识。 */
        const val PAGE_BOOKSHELF: String = "bookshelf"

        /** 账单页面标识。 */
        const val PAGE_BILL: String = "bill"

        /** 资产页面标识。 */
        const val PAGE_ASSETS: String = "assets"

        /** 阅读指纹页面标识。 */
        const val PAGE_FINGERPRINT: String = "fingerprint"

        /** 本书页面标识。 */
        const val PAGE_BOOK: String = "book"

        /** 本记页面标识。 */
        const val PAGE_NOTE: String = "note"

        /** 待办页面标识。 */
        const val PAGE_TODO: String = "todo"
    }
}
