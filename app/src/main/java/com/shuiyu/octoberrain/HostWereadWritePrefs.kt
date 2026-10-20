package com.shuiyu.octoberrain

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.InkLog
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.common.WereadPrefsWriter
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.UUID

/**
 * 微读墨记配置回写端。
 *
 * 把十雨侧的墨单外观与墨台分区配置打包成写偏好动作，交给微读墨记的导出组件写入其偏好。
 * 写入只涉及展示配置，不触碰阅读数据与密钥；调用方身份由导出端按包名校验。
 */
object HostWereadWritePrefs : WereadPrefsWriter {
    /** 写偏好请求的请求码，与迁移请求区分。 */
    const val REQUEST_WRITE_PREFS = 0x0F11

    /** 微读墨记包名。 */
    private const val EXPORT_PACKAGE = "com.inkread.weekread"

    /** 导出组件类名，与迁移共用同一个组件。 */
    private const val EXPORT_CLASS = "$EXPORT_PACKAGE.bridge.ExportActivity"

    /** 写偏好动作，与导出桥的 BridgeContract 保持一致。 */
    private const val ACTION_WRITE_PREFS = "$EXPORT_PACKAGE.action.WRITE_PREFS"

    /** 请求体额外字段名。 */
    private const val EXTRA_WRITE_PAYLOAD = "write_payload"

    /** 一次性令牌额外字段名。 */
    private const val EXTRA_BRIDGE_TOKEN = "bridge_token"

    /** 写入条目数回传字段名。 */
    private const val EXTRA_WRITE_COUNT = "write_count"

    /** 失败说明回传字段名。 */
    private const val EXTRA_ERROR_MESSAGE = "error_message"

    /** 宿主 Activity 的弱引用，避免持有页面。 */
    private var activityRef: WeakReference<Activity>? = null

    /** 挂起请求的结束回调，同一时刻只保留一次推送。 */
    private var pending: ((String) -> Unit)? = null

    /** 挂接宿主 Activity。 */
    fun attach(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    /** 解除挂接并清理未完成请求。 */
    fun detach() {
        activityRef = null
        pending = null
    }

    /**
     * 把当前配置推送到微读墨记。
     *
     * 配置取自全局服务表中的当前快照，调用时机为负一屏设置或整体卡片保存之后。
     *
     * @param onFinished 结果回调，参数为可展示的说明
     * @return 是否成功发起请求
     */
    override fun push(onFinished: (String) -> Unit): Boolean {
        val activity = activityRef?.get()
        val blocked =
            when {
                activity == null -> "当前页面无法同步配置"
                !AppServices.isReady() -> "配置尚未就绪，稍后再试"
                else -> null
            }
        if (blocked != null || activity == null) {
            onFinished(blocked ?: "当前页面无法同步配置")
            return false
        }
        val payload = payloadOf(AppServices.config.state.value)
        val intent =
            Intent(ACTION_WRITE_PREFS).apply {
                component = ComponentName(EXPORT_PACKAGE, EXPORT_CLASS)
                putExtra(EXTRA_WRITE_PAYLOAD, payload)
                putExtra(EXTRA_BRIDGE_TOKEN, UUID.randomUUID().toString())
            }
        pending = onFinished
        return runCatching {
            activity.startActivityForResult(intent, REQUEST_WRITE_PREFS)
            true
        }.getOrElse { error ->
            InkLog.d("OctoberRain", "weread prefs push failed: ${error.message}")
            pending = null
            onFinished("未找到微读墨记，无法同步配置")
            false
        }
    }

    /**
     * 转发宿主 Activity 的 onActivityResult，返回 true 表示本对象已消费。
     *
     * @param requestCode 请求码
     * @param resultCode 结果码
     * @param data 回传数据
     */
    fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ): Boolean {
        if (requestCode != REQUEST_WRITE_PREFS) return false
        val callback = pending
        pending = null
        val message =
            if (resultCode == Activity.RESULT_OK) {
                val count = data?.getIntExtra(EXTRA_WRITE_COUNT, 0) ?: 0
                "配置已同步，共 $count 项"
            } else {
                data?.getStringExtra(EXTRA_ERROR_MESSAGE) ?: "配置同步未完成"
            }
        callback?.invoke(message)
        return true
    }

    /**
     * 组装写偏好请求体。
     *
     * 字段名与微读墨记的偏好键一致，只包含展示配置；卡片周期由整体卡片当前形态换算。
     *
     * @param config 当前配置快照
     * @return 单层 JSON 文本
     */
    private fun payloadOf(config: LauncherConfig): String {
        val menu = config.integrations.wereadMenu
        val desk = config.integrations.wereadDesk
        val forms = config.desktop.negativePage.forms
        return JSONObject()
            .apply {
                put("card_period", cardPeriodOf(forms))
                put("desk_on_mask", desk.effectiveOnMask)
                put("desk_order", JSONArray(desk.effectiveOrder))
                put("menu_title", menu.effectiveTitle)
                put("menu_unit", menu.effectiveUnit)
                put("menu_top_n", menu.effectiveTopN)
                put("menu_min_sec", menu.effectiveMinSeconds)
                put("menu_drop_off_shelf", menu.dropOffShelf)
                put("menu_note_src", menu.effectiveNoteSource)
                put("menu_excerpt_mode", menu.effectiveExcerptMode)
                put("menu_show_author", menu.showAuthor)
                put("menu_show_duration", menu.showDuration)
                put("menu_show_progress", menu.showProgress)
                put("menu_footer", menu.effectiveFooter)
                put("menu_blocks_mask", menu.effectiveBlocksMask)
                put("menu_view", menu.effectiveView)
            }.toString()
    }

    /**
     * 把整体卡片的当前形态换算为微读墨记的卡片周期。
     *
     * 形态序号与内核常量一致：0 本周、1 本月、2 本书、3 本记、4 待办。
     * 待办形态在微读墨记中没有对应周期，按本周处理。
     *
     * @param forms 负一屏整体卡片的形态记录
     * @return 卡片周期标识
     */
    private fun cardPeriodOf(forms: Map<String, Int>): String =
        when (forms["weread_board"] ?: 0) {
            1 -> "monthly"
            2 -> "book"
            3 -> "note"
            else -> "weekly"
        }
}
