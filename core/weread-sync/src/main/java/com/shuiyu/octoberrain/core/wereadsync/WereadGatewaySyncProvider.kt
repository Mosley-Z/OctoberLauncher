package com.shuiyu.octoberrain.core.wereadsync

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * 宿主和内容层共用的官方网关同步适配器。
 *
 * 书架与资产使用同一轮逐书进度，笔记先规范化再交给各内核消费者读取，
 * 避免两个同步入口分别维护字段猜测和不完整的数据格式。
 */
class WereadGatewaySyncProvider(
    private val request: WereadRequest,
    root: File,
) : WereadSyncProvider {
    constructor(gateway: WereadGateway, root: File) : this(
        WereadRequest { apiName, business ->
            (gateway.request(apiName, business) as? WereadGatewayResult.Success)?.payload
        },
        root,
    )

    private val books = WereadBookSync(request, root)
    private val notes = WereadNoteSync(request, root)

    override suspend fun shelf(): List<WereadShelfBook>? = books.shelf()

    override suspend fun assets(): List<WereadAssetBook>? = books.assets()

    override suspend fun notes(): WereadNoteBatch? = notes.sync()

    override suspend fun stats(
        mode: String,
        baseTimeSec: Long,
    ): String? =
        request
            .request(
                "/readdata/detail",
                buildJsonObject {
                    put("mode", mode)
                    put("baseTime", baseTimeSec)
                },
            )?.toString()

    /** 票据派生由拥有内核依赖的内容适配层提供，数据源本身不虚构历史票据。 */
    override suspend fun bill(
        mode: String,
        periodStartSec: Long,
        statsRaw: String,
    ): String? = null

    override fun warnings(): List<String> = books.warnings + notes.warnings
}
