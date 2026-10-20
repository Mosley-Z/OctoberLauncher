package com.shuiyu.octoberrain.feature.wereadapp

import android.content.Context
import com.inkread.weekread.kernel.HitRegions
import com.inkread.weekread.kernel.KernelHost
import com.inkread.weekread.kernel.desk.DeskHost
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.WereadSection
import java.io.File

/**
 * 微读墨记页面的内核宿主适配。
 *
 * 同一实例同时实现 [KernelHost] 与 [DeskHost]：卡片渲染使用前者的目录、字号与重绘回调，
 * 洞察页使用后者的偏好与颜色接口。页面内的页面跳转统一经 [Router] 完成。
 *
 * @param context 用于定位微读数据目录的上下文
 * @param requestRedraw 内核请求重绘时的回调
 */
@Suppress("TooManyFunctions")
internal class WereadAppHost(
    private val context: Context,
    private val requestRedraw: () -> Unit,
) : KernelHost,
    DeskHost {
    /** 微读数据目录，与桌面卡片共用。 */
    override fun filesDir(): File = WereadAppPrefs.dataDir(context.applicationContext ?: context)

    /** 号对应的像素值，取屏长边的千分之一点五。 */
    override fun scale(): Float {
        val metrics = context.resources.displayMetrics
        return maxOf(metrics.widthPixels, metrics.heightPixels) * UNIT_PER_LONG_EDGE
    }

    /** 内核请求重绘时转发给页面视图。 */
    override fun requestRedraw() {
        requestRedraw.invoke()
    }

    /**
     * 处理内核发起的页面跳转。
     *
     * @param page 内核页面标识，取值见内核 `HitRegions` 文档：desk、bookshelf、bill、assets、
     *   fingerprint、book、note、todo
     */
    override fun openPage(page: String) {
        if (!AppServices.hasRouter()) return
        val destination: Destination? =
            when (page) {
                PAGE_DESK, PAGE_NEGATIVE, PAGE_ASSET, PAGE_FINGERPRINT -> Destination.Negative
                PAGE_BOOKSHELF -> Destination.Bookshelf
                PAGE_BILL -> Destination.WereadTab(WereadSection.BILL)
                PAGE_BOOK -> Destination.Book
                PAGE_NOTE -> Destination.Note
                PAGE_TODO -> Destination.Todo
                else -> null
            }
        if (destination != null) AppServices.router.navigate(destination)
    }

    /** 本地渲染不请求网关，返回固定标记避免内核绘出未配置密钥的占位画面。 */
    override fun apiKey(): String = LOCAL_API_KEY

    /** 页面内保留「打开墨台」按钮，跳转到负一屏墨台区。 */
    override fun deskEnabled(): Boolean = true

    /** 抬头「打开墨台」按钮的文字。 */
    override fun deskOpenLabel(): String = "打开墨台"

    /** 洞察页不区分墨台模块，返回空掩码。 */
    override fun deskModuleMask(): Int = 0

    /** 洞察页不读取墨台模块顺序。 */
    override fun deskOrder(): Array<String> = emptyArray()

    /** 读取字符串偏好，缺失时返回默认值。 */
    override fun menuPrefs(
        key: String,
        default: String,
        fallback: String,
    ): String = WereadAppPrefs.store(context).getString(key, default.ifEmpty { fallback })

    /** 读取布尔偏好。 */
    override fun menuBool(
        key: String,
        default: Boolean,
    ): Boolean = WereadAppPrefs.store(context).getBool(key, default)

    /** 读取整型偏好。 */
    override fun menuInt(
        key: String,
        default: Int,
    ): Int = WereadAppPrefs.store(context).getInt(key, default)

    /** 阅读指纹字号档，缺省 18 像素。 */
    override fun fpFontPx(): Int = WereadAppPrefs.store(context).getInt("fp_font_px", 18)

    /** 洞察页颜色统一使用纸张黑，忽略调用方默认值。 */
    override fun styleColor(
        key: String,
        default: Int,
    ): Int = default

    /** 待办命中编号的起点，供页面与内核常量对照。 */
    fun todoItemHit(index: Int): Int = HitRegions.TODO_ITEM_BASE + index

    private companion object {
        /** 号相对屏长边的比例，与文档第三章一致。 */
        const val UNIT_PER_LONG_EDGE: Float = 0.0015f

        /** 本地渲染标记，非真实密钥。 */
        const val LOCAL_API_KEY: String = "local-page"

        /** 墨台页内核标识。 */
        const val PAGE_DESK: String = "desk"

        /** 负一屏页内核标识。 */
        const val PAGE_NEGATIVE: String = "negative"

        /** 阅读资产页内核标识。 */
        const val PAGE_ASSET: String = "assets"

        /** 阅读指纹页内核标识。 */
        const val PAGE_FINGERPRINT: String = "fingerprint"

        /** 书架页内核标识。 */
        const val PAGE_BOOKSHELF: String = "bookshelf"

        /** 账单页内核标识。 */
        const val PAGE_BILL: String = "bill"

        /** 本书页内核标识。 */
        const val PAGE_BOOK: String = "book"

        /** 本记页内核标识。 */
        const val PAGE_NOTE: String = "note"

        /** 待办页内核标识。 */
        const val PAGE_TODO: String = "todo"
    }
}
