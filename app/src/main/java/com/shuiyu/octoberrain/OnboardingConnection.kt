package com.shuiyu.octoberrain

import android.app.Dialog
import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.WereadKeyEntrySession
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.core.wereadsync.WereadGateway
import com.shuiyu.octoberrain.core.wereadsync.WereadGatewayError
import com.shuiyu.octoberrain.core.wereadsync.WereadGatewayResult
import com.shuiyu.octoberrain.feature.wereadapp.ui.qr.OnboardingKeyEntryAdapter
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 首启连接只验证账号统计接口，不同步书库、不写入阅读缓存。 */
internal object OnboardingConnection {
    enum class Result { CONNECTED, INVALID, UNAVAILABLE }

    fun verify(
        key: String,
        onResult: (Result) -> Unit,
    ) {
        Thread({
            val response =
                runCatching {
                    WereadGateway({ key }).request(
                        "/readdata/detail",
                        buildJsonObject {
                            put("mode", "monthly")
                            put(
                                "baseTime",
                                System.currentTimeMillis() / 1000L,
                            )
                        },
                    )
                }.getOrNull()
            val result =
                when (response) {
                    is WereadGatewayResult.Success -> Result.CONNECTED
                    is WereadGatewayResult.Failure ->
                        if (response.error == WereadGatewayError.INVALID_KEY ||
                            response.error == WereadGatewayError.MISSING_KEY
                        ) {
                            Result.INVALID
                        } else {
                            Result.UNAVAILABLE
                        }
                    else -> Result.UNAVAILABLE
                }
            onResult(result)
        }, "onboarding-verify").apply {
            isDaemon = true
            start()
        }
    }

    fun showEntry(
        context: Context,
        session: WereadKeyEntrySession,
    ): Dialog {
        val dialog = Dialog(context)
        val size = (220 * context.resources.displayMetrics.density).toInt()
        val column =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(com.shuiyu.octoberrain.core.design.InkColors.PAPER)
                setPadding(24, 24, 24, 24)
                addView(InkWidgets.title(context, "手机扫码录入本机"))
                addView(InkWidgets.body(context, "微信读书手机客户端 → 设置 → 微信读书 SKILL → 复制 API KEY，再扫码粘贴提交。手机与墨水屏需在可互通的局域网。"))
                addView(
                    OnboardingKeyEntryAdapter.qrView(context, session.payload, size),
                    LinearLayout.LayoutParams(size, size),
                )
                addView(InkWidgets.body(context, session.address))
                addView(InkWidgets.button(context, "关闭", false) { dialog.dismiss() })
            }
        dialog.setContentView(column)
        dialog.setOnDismissListener { AppServices.wereadKeyEntry?.stop() }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        return dialog
    }
}
