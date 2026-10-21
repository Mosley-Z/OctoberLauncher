package com.shuiyu.octoberrain.feature.content.ui.negative

import com.inkread.weekread.kernel.desk.DeskPage
import com.inkread.weekread.kernel.desk.PricePad
import com.shuiyu.octoberrain.core.wereadsync.WereadDeskEditsStore
import java.lang.reflect.Field
import kotlin.math.roundToInt

/**
 * k6 墨台编辑的落盘桥。
 *
 * k6 只把录价保存在内存，且未公开当前键盘的书目。兼容层只读取两处分区的私有 pad，
 * 其余均调用公开接口；确认前落盘，失败关闭键盘并通知宿主重新加载，避免虚报成功。
 */
internal class WereadDeskEditBridge(
    private val store: WereadDeskEditsStore,
) {
    /** 处理键盘按键；FAILED 时宿主须重载页面并显示保存失败提示。 */
    fun press(
        page: DeskPage,
        key: Int,
    ): Result {
        if (key != PricePad.PK_OK) {
            page.padPress(key)
            return Result.APPLIED
        }
        val saved =
            runCatching {
                val pad = activePad(page) ?: error("k6 price pad unavailable")
                val fen = priceFen(pad.text())
                store.setPrice(pad.bookId(), fen)
            }.getOrDefault(false)
        if (!saved) {
            page.closePad()
        } else {
            // 清价会移除内存覆盖，先丢弃加载器的旧偏好缓存。
            page.data().loader().reload()
            page.padPress(key)
        }
        return if (saved) Result.SAVED else Result.FAILED
    }

    /** 长按移出后保存内核的完整快照；失败时宿主须重载撤销临时移出。 */
    fun saveHidden(page: DeskPage): Boolean = store.saveHidden(page.data().hiddenIds())

    /** 只读取固定 k6 字段；升级内核时由契约测试检查结构。 */
    private fun activePad(page: DeskPage): PricePad? =
        padFields().firstNotNullOfOrNull { (sectionField, padField) ->
            val section = sectionField.get(page)
            (section?.let { padField.get(it) } as? PricePad)?.takeIf { it.isOpen }
        }

    /** 操作是否只改变输入、已保存或保存失败。 */
    enum class Result { APPLIED, SAVED, FAILED }

    companion object {
        /** 与 PricePad 的确认规则一致：空串删除，其余按元四舍五入为分。 */
        internal fun priceFen(text: String): Int =
            if (text.isEmpty()) -1 else (text.toDouble() * 100.0).roundToInt().coerceAtLeast(0)

        /** 固定版本字段契约，供测试直接验证实际打包的内核。 */
        internal fun padFields(): List<Pair<Field, Field>> =
            listOf("billSection", "assetSection").map { name ->
                val section = DeskPage::class.java.getDeclaredField(name).apply { isAccessible = true }
                val pad = section.type.getDeclaredField("pad").apply { isAccessible = true }
                check(pad.type == PricePad::class.java)
                section to pad
            }
    }
}
