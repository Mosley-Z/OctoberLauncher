package com.shuiyu.octoberrain.core.wereadsync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/** 资产专用拉取状态；少量本书缓存不能证明用户做过完整资产拉取。后台线程读写。 */
object WereadAssetRefreshState {
    const val FIRST_NOTICE =
        "完整统计需读取当前书架电子书并逐本补齐累计进度，已有少量缓存不代表完整统计。" +
            "首次可能较慢；点击刷新开始。已移架且从未缓存的历史、有声书及文章不在可枚举范围内。"
    private const val FILE = "asset-refresh-state.json"

    fun notice(root: File): String? {
        if (File(root, FILE).isFile || hasLegacyAssets(root)) return null
        return FIRST_NOTICE
    }

    /** 兼容旧版本已经拉取的资产：升级不再给已有资产补插首次说明。 */
    private fun hasLegacyAssets(root: File): Boolean {
        val cached = runCatching { Json.parseToJsonElement(File(root, "assets.json").readText()) }.getOrNull()
        val books = if (cached is JsonArray) cached else (cached as? JsonObject)?.get("books") as? JsonArray
        return books?.isNotEmpty() == true
    }

    fun save(
        root: File,
        report: WereadSyncReport,
        now: Long,
    ) {
        runCatching {
            root.mkdirs()
            val target = File(root, FILE)
            val temp = File(root, "$FILE.tmp")
            temp.writeText(
                buildJsonObject {
                    put("success", report.success)
                    put("message", report.message)
                    put("at", now)
                }.toString(),
            )
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        }
    }
}
