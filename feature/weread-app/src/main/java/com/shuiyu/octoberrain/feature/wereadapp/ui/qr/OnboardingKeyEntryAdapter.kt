package com.shuiyu.octoberrain.feature.wereadapp.ui.qr

import android.content.Context
import android.view.View
import android.widget.ImageView
import com.shuiyu.octoberrain.core.common.WereadApiKeyEntry
import com.shuiyu.octoberrain.core.common.WereadKeyEntrySession

/** 应用装配层复用现有局域网录入服务与二维码编码器。 */
class OnboardingKeyEntryAdapter : WereadApiKeyEntry {
    private var server: KeyEntryServer? = null
    override val isRunning: Boolean get() = server != null

    override fun start(listener: WereadApiKeyEntry.Listener): WereadKeyEntrySession? {
        if (server != null) return null
        val candidate = KeyEntryServer()
        val payload =
            candidate.start(
                object : KeyEntryServer.Listener {
                    override fun onKey(key: String) {
                        server = null
                        listener.onAccepted(key)
                    }

                    override fun onClosed(reason: String) {
                        server = null
                        listener.onClosed(WereadApiKeyEntry.CloseReason.EXPIRED)
                    }
                },
            )
        return payload?.let {
            server = candidate
            WereadKeyEntrySession(it, it.substringBefore("?"), System.currentTimeMillis() + 5 * 60 * 1000L)
        }
    }

    override fun stop() {
        server?.stop()
        server = null
    }

    companion object {
        /** payload 只允许官方获取地址或本机会话地址，不包含密钥。 */
        fun qrView(
            context: Context,
            payload: String,
            sizePx: Int,
        ): View =
            ImageView(context).apply {
                val matrix = QrEncoder.encode(payload)
                setImageBitmap(QrEncoder.render(matrix, sizePx))
                contentDescription = "扫码打开页面"
            }
    }
}
