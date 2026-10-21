package com.shuiyu.octoberrain.feature.content.data

import com.inkread.weekread.kernel.model.BillSheet
import com.shuiyu.octoberrain.core.wereadsync.WereadGateway
import com.shuiyu.octoberrain.core.wereadsync.WereadGatewaySyncProvider
import com.shuiyu.octoberrain.core.wereadsync.WereadSyncProvider
import java.io.File

/** 内容刷新与宿主后台同步共用同一数据源，仅在这里补充内核提供的票据派生。 */
class WereadSyncBridge(
    root: File,
) : WereadSyncProvider by WereadGatewaySyncProvider(
        WereadGateway({ ContentPaths.integrations().wereadApiKey }),
        root,
    ) {
    override suspend fun bill(
        mode: String,
        periodStartSec: Long,
        statsRaw: String,
    ): String? =
        runCatching { BillSheet.buildBillSheet(statsRaw, mode, periodStartSec) }
            .getOrNull()
            ?.takeIf { json -> json.contains("\"items\"") }
}
