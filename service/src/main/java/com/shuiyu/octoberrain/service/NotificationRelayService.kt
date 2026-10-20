package com.shuiyu.octoberrain.service

import android.app.Notification
import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.MediaState
import com.shuiyu.octoberrain.core.common.NotificationItem
import com.shuiyu.octoberrain.core.data.MediaBridge
import com.shuiyu.octoberrain.core.data.MediaCommand
import com.shuiyu.octoberrain.core.data.NotificationCenter
import com.shuiyu.octoberrain.core.data.SystemControlsImpl

/**
 * 通知转发服务：把系统挂起通知写入 [NotificationCenter]，并承担媒体会话命令的执行。
 *
 * 服务由系统绑定，连接成功后注册撤销处理器并全量同步一次；断开时解除处理器，
 * 保证界面侧在服务不可用时只操作本地副本，不产生跨进程残留。
 */
class NotificationRelayService : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())

    private var sessionManager: MediaSessionManager? = null

    private var sessionListenerAttached = false

    /** 媒体会话变化回调：重新读取当前会话并刷新媒体状态。 */
    private val sessionListener =
        MediaSessionManager.OnActiveSessionsChangedListener {
            refreshMedia()
        }

    override fun onCreate() {
        super.onCreate()
        MediaBridge.install(::performMediaCommand)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        // 界面侧撤销动作转交系统：单条撤销与全部撤销
        NotificationCenter.installHandlers(
            dismissOne = { key -> runCatching { cancelNotification(key) } },
            dismissEverything = { runCatching { cancelAllNotifications() } },
        )
        reload()
        attachMediaSessions()
    }

    override fun onListenerDisconnected() {
        NotificationCenter.installHandlers(null, null)
        MediaBridge.install(null)
        detachMediaSessions()
        instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val item = sbn?.let(::toItem) ?: return
        NotificationCenter.push(item)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val key = sbn?.key ?: return
        NotificationCenter.remove(key)
    }

    override fun onDestroy() {
        NotificationCenter.installHandlers(null, null)
        MediaBridge.install(null)
        detachMediaSessions()
        handler.removeCallbacksAndMessages(null)
        instance = null
        super.onDestroy()
    }

    /** 全量同步当前挂起通知，按投递时间降序替换列表。 */
    fun reload() {
        val items =
            runCatching { activeNotifications }
                .getOrNull()
                ?.mapNotNull(::toItem)
                .orEmpty()
                .sortedByDescending { it.postedAt }
        NotificationCenter.replaceAll(items)
    }

    /**
     * 把系统通知转换为界面条目。
     *
     * 参数 sbn 为系统通知；标题与正文都为空时返回 null；
     * 标题为空而正文非空时正文提升为标题，与上游规则一致。
     * 同时读取分组标记、常驻标记与会话名：会话名非空时按会话生成归并键，
     * 缺失时以包名作为归并键，供通知中心在写入阶段合并同一会话的消息。
     */
    private fun toItem(sbn: StatusBarNotification): NotificationItem? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: return null
        var title =
            extras
                .getCharSequence(Notification.EXTRA_TITLE)
                ?.toString()
                ?.trim()
                .orEmpty()
        var text =
            extras
                .getCharSequence(Notification.EXTRA_TEXT)
                ?.toString()
                ?.trim()
                .orEmpty()
        if (title.isEmpty() && text.isEmpty()) return null
        if (title.isEmpty()) {
            title = text
            text = ""
        }
        val key = sbn.key?.takeIf { it.isNotEmpty() } ?: "${sbn.packageName}:${sbn.id}"
        // 会话名用于聊天类通知的归并，系统在 Android 7.0 起提供该字段
        val conversationTitle =
            extras
                .getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        val groupKey =
            if (conversationTitle != null) {
                "conversation:${sbn.packageName}:$conversationTitle"
            } else {
                sbn.packageName
            }
        val isSummary =
            (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0 ||
                extras.getBoolean(EXTRA_IS_GROUP_SUMMARY, false)
        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        return NotificationItem(
            key = key,
            packageName = sbn.packageName,
            title = title,
            text = text,
            postedAt = sbn.postTime,
            groupKey = groupKey,
            conversationTitle = conversationTitle,
            isSummary = isSummary,
            isOngoing = isOngoing,
        )
    }

    /** 注册媒体会话监听，取不到权限时静默降级。 */
    private fun attachMediaSessions() {
        val manager =
            runCatching { getSystemService(MediaSessionManager::class.java) }.getOrNull()
                ?: return
        sessionManager = manager
        val component = ComponentName(this, NotificationRelayService::class.java)
        val attached =
            runCatching {
                manager.addOnActiveSessionsChangedListener(sessionListener, component)
                true
            }.getOrDefault(false)
        sessionListenerAttached = attached
        refreshMedia()
    }

    /** 解除媒体会话监听。 */
    private fun detachMediaSessions() {
        val manager = sessionManager
        if (manager != null && sessionListenerAttached) {
            runCatching { manager.removeOnActiveSessionsChangedListener(sessionListener) }
        }
        sessionListenerAttached = false
        sessionManager = null
        updateMedia(MediaState.IDLE)
    }

    /** 读取当前活动媒体会话，写回系统能力层。 */
    private fun refreshMedia() {
        val manager = sessionManager ?: return
        val component = ComponentName(this, NotificationRelayService::class.java)
        val controllers =
            runCatching { manager.getActiveSessions(component) }
                .getOrNull()
                .orEmpty()
        val controller = controllers.lastOrNull { it.playbackState != null }
        if (controller == null) {
            updateMedia(MediaState.IDLE)
            return
        }
        val metadata = controller.metadata
        val playback = controller.playbackState
        updateMedia(
            MediaState(
                active = true,
                playing = playback?.state == PlaybackState.STATE_PLAYING,
                title =
                    metadata
                        ?.description
                        ?.title
                        ?.toString()
                        .orEmpty(),
                artist =
                    metadata
                        ?.description
                        ?.subtitle
                        ?.toString()
                        .orEmpty(),
                packageName = controller.packageName,
            ),
        )
    }

    /** 执行托盘下发的媒体命令。 */
    private fun performMediaCommand(command: MediaCommand) {
        val controller = activeController() ?: return
        runCatching {
            when (command) {
                MediaCommand.TOGGLE -> {
                    val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
                    if (playing) controller.transportControls.pause() else controller.transportControls.play()
                }

                MediaCommand.PREVIOUS -> controller.transportControls.skipToPrevious()

                MediaCommand.NEXT -> controller.transportControls.skipToNext()
            }
        }
        // 播放状态由系统异步回流，稍后再读取一次
        handler.postDelayed({ refreshMedia() }, MEDIA_REFRESH_DELAY_MS)
    }

    /** 取当前可控制的媒体会话。 */
    private fun activeController(): MediaController? {
        val manager = sessionManager ?: return null
        val component = ComponentName(this, NotificationRelayService::class.java)
        return runCatching { manager.getActiveSessions(component) }
            .getOrNull()
            ?.lastOrNull { it.playbackState != null }
    }

    /** 写回媒体状态，系统能力层未装配时忽略。 */
    private fun updateMedia(state: MediaState) {
        runCatching { (AppServices.system as? SystemControlsImpl)?.updateMedia(state) }
    }

    companion object {
        /** 当前连接的服务实例，供进程内查询。 */
        @Volatile
        private var instance: NotificationRelayService? = null

        /** 请求重新同步通知；服务未连接时返回 false。 */
        fun reloadActive(): Boolean {
            val service = instance ?: return false
            service.reload()
            return true
        }

        private const val MEDIA_REFRESH_DELAY_MS = 300L

        /** 系统写入的“本条为分组摘要”标记，对应 NotificationCompat.EXTRA_IS_GROUP_SUMMARY。 */
        private const val EXTRA_IS_GROUP_SUMMARY = "android.isGroupSummary"
    }
}
