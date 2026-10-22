package com.shuiyu.octoberrain.feature.content.ui.negative

import com.shuiyu.octoberrain.core.common.WereadRefreshRequest
import com.shuiyu.octoberrain.core.common.WereadSyncHost
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Both entry points use the existing book refresh, bindings and shared selection preference. */
internal object SharedBookRefresh {
    suspend fun request(host: WereadSyncHost?): Pair<Boolean, String> =
        suspendCancellableCoroutine { continuation ->
            val accepted =
                host?.refresh(WereadRefreshRequest("book")) { success, message ->
                    if (continuation.isActive) continuation.resume(success to message)
                } ?: false
            if (!accepted && continuation.isActive) {
                val message = if (host?.isRunning() == true) "共享刷新正在进行，请稍后查看" else "请先配置微信读书"
                continuation.resume(false to message)
            }
        }
}
