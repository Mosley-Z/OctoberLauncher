package com.shuiyu.octoberrain

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.UiVisibility
import com.shuiyu.octoberrain.core.common.WereadRefreshRequest
import com.shuiyu.octoberrain.core.common.WereadSyncHost
import com.shuiyu.octoberrain.core.wereadsync.AutomaticSyncBudget
import com.shuiyu.octoberrain.core.wereadsync.WereadCardRefreshService
import com.shuiyu.octoberrain.core.wereadsync.WereadGateway
import com.shuiyu.octoberrain.core.wereadsync.WereadStore
import com.shuiyu.octoberrain.core.wereadsync.WereadSyncReport
import com.shuiyu.octoberrain.core.wereadsync.WereadSyncService
import com.shuiyu.octoberrain.core.wereadsync.WereadSyncTransport
import com.shuiyu.octoberrain.core.wereadsync.parseSyncState
import com.shuiyu.octoberrain.feature.content.ContentSourceImpl
import com.shuiyu.octoberrain.feature.content.data.WereadSyncBridge
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 独立模式的数据同步装配。
 *
 * 网关拉取与本地写入都在后台线程执行，结果写回同步状态；界面只读状态文案与触发同步。
 * 数据目录固定为 `filesDir/weread`，与迁移导入共用同一份原盘结构，内核与内容层
 * 因此不需要区分数据来源。
 * 同一宿主集中管理同步生命周期、状态读取和完成后的消费者通知。
 */
@Suppress("TooManyFunctions")
object HostWereadSync : WereadSyncHost {
    /** 数据目录名，与内核读取位置保持一致。 */
    private const val DIRECTORY = "weread"

    /** 状态键所在的键值文件名。 */
    private const val PREFS_FILE = "kernel_prefs.json"

    /** 运行标志，避免并发触发。 */
    private val queue = WereadSyncQueue()

    /** 数据目录，装配后赋值。 */
    private var root: File? = null

    /** 最近一次同步报告，用于状态文案。 */
    @Volatile
    private var lastReport: WereadSyncReport? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var lastImmersiveSuccess = 0L

    @Volatile private var lastImmersiveAttempt = 0L

    /** 仅失效消费者缓存，不触发另一轮联网同步。 */
    internal fun notifyDataChanged() {
        mainHandler.post {
            (AppServices.content as? ContentSourceImpl)?.invalidate()
            AppServices.refresh.requestPartial()
        }
    }

    /**
     * 装配数据目录。
     *
     * @param filesDir 应用私有目录
     */
    fun attach(filesDir: File) {
        root = File(filesDir, DIRECTORY)
        lastImmersiveSuccess = File(root, "immersive-refresh.stamp").lastModified()
    }

    /** 当前是否正在同步。 */
    override fun isRunning(): Boolean = queue.isRunning()

    /** 前台启动只做限量同步；失败重启也遵守15分钟冷却，不因空墨单反复联网。 */
    internal fun syncAutomaticallyIfNeeded() {
        val dir = root
        val integrations = AppServices.config.state.value.integrations
        val enabled =
            UiVisibility.active.value && integrations.effectiveWereadSource == "synced" && apiKey().isNotEmpty()
        if (dir == null || !enabled) return
        val now = System.currentTimeMillis()
        val attempt = File(dir, "automatic-attempt.stamp")
        if (attempt.isFile && now - attempt.lastModified() < 900_000L) return
        val shelf = File(dir, "shelf.json")
        val bills = File(dir, "bills.json")
        val stale =
            !shelf.isFile ||
                !bills.isFile ||
                bills.length() <= 2L ||
                now - shelf.lastModified() > integrations.effectiveWereadRefreshMinutes * 60_000L
        if (stale && refresh(WereadRefreshRequest("automatic")) { _, _ -> }) {
            runCatching {
                dir.mkdirs()
                attempt.writeText("")
                attempt.setLastModified(now)
            }
        }
    }

    /** 最近一次同步的状态文案。 */
    override fun statusText(): String {
        val state = readState()
        return when {
            queue.isRunning() -> "正在同步数据"
            lastReport != null -> lastReport?.message.orEmpty()
            state != null && state.success -> "上次同步 ${formatTime(state.finishedAtMillis)}"
            state != null -> "上次同步失败：${state.message}"
            else -> "尚未同步"
        }
    }

    /**
     * 触发一次同步。
     *
     * @param onFinished 结束回调，第一个参数为成功与否，第二个参数为状态文案
     * @return 已启动或已合并到下一轮返回 true，密钥缺失或目录未就绪返回 false
     */
    override fun syncNow(onFinished: (Boolean, String) -> Unit): Boolean {
        val dir = root
        val key = apiKey()
        if (dir == null || key.isEmpty()) return false
        if (queue.submitFull(onFinished)) startWorker(dir, key, onFinished)
        return true
    }

    override fun refresh(
        request: WereadRefreshRequest,
        onFinished: (Boolean, String) -> Unit,
    ): Boolean {
        if (request.mode == "todo") {
            notifyDataChanged()
            onFinished(true, "待办已重新读取")
            return true
        }
        val autoSuppressed = shouldSkipAutoRefresh(request)
        val dir = root
        val key = apiKey()
        return when {
            autoSuppressed || dir == null || key.isEmpty() -> false
            !queue.tryStartRefresh() -> false
            else -> {
                if (request.mode == "immersive_auto") lastImmersiveAttempt = System.currentTimeMillis()
                startWorker(dir, key, onFinished, request)
                true
            }
        }
    }

    private fun shouldSkipAutoRefresh(request: WereadRefreshRequest): Boolean {
        if (request.mode != "immersive_auto") return isAutomatic(request) && !UiVisibility.active.value
        val now = System.currentTimeMillis()
        val zone = java.time.ZoneId.systemDefault()
        val day =
            java.time.Instant
                .ofEpochMilli(now)
                .atZone(zone)
                .toLocalDate()
        val lastDay =
            java.time.Instant
                .ofEpochMilli(lastImmersiveSuccess)
                .atZone(zone)
                .toLocalDate()
        val recentSuccess = day == lastDay && now - lastImmersiveSuccess < 600_000L
        val recentFailure = lastImmersiveAttempt > lastImmersiveSuccess && now - lastImmersiveAttempt < 900_000L
        return !UiVisibility.active.value || recentSuccess || recentFailure
    }

    private fun isAutomatic(request: WereadRefreshRequest?): Boolean =
        request?.mode == "automatic" || request?.mode == "immersive_auto"

    /** 启动后台同步线程。 */
    private fun startWorker(
        dir: File,
        key: String,
        onFinished: (Boolean, String) -> Unit,
        request: WereadRefreshRequest? = null,
    ) {
        val thread =
            Thread({
                val report =
                    runCatching {
                        runBlocking {
                            syncWithStatus(dir, key, request)
                        }
                    }.getOrElse { error ->
                        WereadSyncReport(false, "同步失败：${error.message ?: "未知错误"}")
                    }
                if (report.success &&
                    request?.mode?.startsWith("immersive") == true
                ) {
                    lastImmersiveSuccess = System.currentTimeMillis()
                }
                lastReport = report
                val pending = queue.finish()
                if (pending.isNotEmpty()) {
                    startWorker(dir, apiKey(), { success, message ->
                        pending.forEach { callback -> runCatching { callback(success, message) } }
                    })
                }
                notifyDataChanged()
                onFinished(report.success, report.message)
            }, "weread-sync")
        thread.isDaemon = true
        thread.start()
    }

    /** 保留网关失败类型，已保存密钥的网络错误不能再提示未填写。 */
    private suspend fun syncWithStatus(
        dir: File,
        key: String,
        request: WereadRefreshRequest?,
    ): WereadSyncReport {
        val gateway = WereadGateway({ key })
        val budget =
            if (isAutomatic(request)) {
                AutomaticSyncBudget({
                    UiVisibility.active.value
                }, SystemClock::elapsedRealtime)
            } else {
                null
            }

        val transport = WereadSyncTransport(gateway::request, budget)
        val report =
            if (request == null || request.mode == "automatic") {
                WereadSyncService(
                    WereadStore(dir),
                    WereadSyncBridge(dir, transport),
                    failureMessage = { transport.failureMessage },
                ).sync(
                    bases =
                        if (request ==
                            null
                        ) {
                            WereadSyncService.defaultPeriods()
                        } else {
                            WereadSyncService.automaticPeriods()
                        },
                    includeLibrary = request == null,
                )
            } else {
                WereadCardRefreshService(transport, dir).refresh(request)
            }
        val message = transport.failureMessage ?: return report
        return report.copy(success = false, message = message)
    }

    /** 读取当前密钥。 */
    private fun apiKey(): String =
        AppServices.config
            ?.state
            ?.value
            ?.integrations
            ?.wereadApiKey
            .orEmpty()
            .trim()

    /** 读取上次同步状态，目录或键缺失时返回 null。 */
    private fun readState() = parseSyncState(readStateText())

    /** 从键值文件读取状态文本。 */
    private fun readStateText(): String? {
        val file = root?.let { dir -> File(dir, PREFS_FILE).takeIf { candidate -> candidate.isFile } }
        val element = file?.let { runCatching { Json.parseToJsonElement(it.readText()) }.getOrNull() }
        val strings = (element as? JsonObject)?.get("string") as? JsonObject
        return strings?.get("weread_sync_state")?.jsonPrimitive?.content
    }

    /** 把毫秒格式化为时分。 */
    private fun formatTime(millis: Long): String =
        if (millis <= 0L) "未知" else SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(millis))
}
