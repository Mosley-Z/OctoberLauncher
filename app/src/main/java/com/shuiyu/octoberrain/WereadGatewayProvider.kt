package com.shuiyu.octoberrain

import com.shuiyu.octoberrain.core.wereadsync.WereadAssetBook
import com.shuiyu.octoberrain.core.wereadsync.WereadGateway
import com.shuiyu.octoberrain.core.wereadsync.WereadGatewayResult
import com.shuiyu.octoberrain.core.wereadsync.WereadNoteBatch
import com.shuiyu.octoberrain.core.wereadsync.WereadShelfBook
import com.shuiyu.octoberrain.core.wereadsync.WereadSyncProvider
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 网关适配的数据提供者。
 *
 * 每个接口只用一次调用取原始 JSON，结构与字段映射交给 [WereadGatewayParsing]；
 * 失败时返回 null，同步服务据此跳过该项并保留既有数据。
 *
 * @param gateway 网关客户端，已绑定密钥来源
 */
internal class WereadGatewayProvider(
    private val gateway: WereadGateway,
) : WereadSyncProvider {
    override suspend fun shelf(): List<WereadShelfBook>? = WereadGatewayParsing.parseShelf(request(API_SHELF))

    override suspend fun stats(
        mode: String,
        baseTimeSec: Long,
    ): String? = request(API_READ_DATA, mode, baseTimeSec)

    override suspend fun assets(): List<WereadAssetBook>? {
        val raw = request(API_READ_DATA, MODE_OVERALL, 0L)
        return WereadGatewayParsing.parseAssets(raw)
    }

    /**
     * 账单快照。
     *
     * 网关只返回统计口径的响应，票据形态由内核从统计快照派生，
     * 因此这里不产出票据；返回 null 让同步服务跳过该项。
     *
     * @param mode 周期模式
     * @param periodStartSec 周期起点秒数
     * @param statsRaw 该周期的统计响应原文
     * @return 恒为 null
     */
    override suspend fun bill(
        mode: String,
        periodStartSec: Long,
        statsRaw: String,
    ): String? = null

    override suspend fun notes(): WereadNoteBatch? = notesBatch()

    /**
     * 笔记概览与逐本划线合并。
     *
     * 概览接口只返回书目与计数，内核渲染摘录与指纹要读 `nt_<bookId>.json` 的
     * `marks` 数组，因此对笔记数大于零的书再逐本取划线并合并成同一份文件。
     * 单本失败只跳过该本，整批仍返回索引与已取到的部分。
     *
     * @return 笔记批次，概览不可用时返回 null
     */
    private fun notesBatch(): WereadNoteBatch? {
        val index = request(API_NOTEBOOKS) ?: return null
        val books = WereadGatewayParsing.noteBooks(index)
        val files =
            books.take(NOTE_BOOK_LIMIT).mapNotNull { book ->
                val bookId = book.optString("bookId")
                if (bookId.isBlank()) {
                    null
                } else {
                    val marks = WereadGatewayParsing.parseMarks(requestMarks(bookId))
                    WereadGatewayParsing.noteFileWithMarks(book, marks)
                }
            }
        return WereadNoteBatch(indexJson = index, notes = files)
    }

    /** 取单本书的划线原文，失败时返回 null 让该本保留元数据。 */
    private fun requestMarks(bookId: String): String? {
        val business = buildJsonObject { put("bookId", bookId) }
        val result = gateway.request(API_BOOKMARKS, business)
        return (result as? WereadGatewayResult.Success)?.payload?.toString()
    }

    /** 调用网关并取数据文本，未传周期时不带业务参数。 */
    private fun request(
        apiName: String,
        mode: String? = null,
        baseTimeSec: Long = 0L,
    ): String? {
        val business =
            if (mode == null) {
                JsonObject(emptyMap())
            } else {
                buildJsonObject {
                    put("mode", mode)
                    put("baseTime", baseTimeSec)
                }
            }
        val result = gateway.request(apiName, business)
        return (result as? WereadGatewayResult.Success)?.payload?.toString()
    }

    /** 接口名与周期标识。 */
    private companion object {
        /** 书架接口名。 */
        const val API_SHELF: String = "/shelf/sync"

        /** 阅读统计与账单接口名。 */
        const val API_READ_DATA: String = "/readdata/detail"

        /** 笔记概览接口名。 */
        const val API_NOTEBOOKS: String = "/user/notebooks"

        /** 逐本划线接口名。 */
        const val API_BOOKMARKS: String = "/book/bookmarklist"

        /** 单次同步最多补划线的书目数，控制请求次数。 */
        const val NOTE_BOOK_LIMIT: Int = 20

        /** 全时段模式标识。 */
        const val MODE_OVERALL: String = "overall"
    }
}
