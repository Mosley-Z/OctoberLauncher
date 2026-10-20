package com.shuiyu.octoberrain.feature.settings.qr

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.feature.settings.SettingsUi

/**
 * 局域网录密钥面板。
 *
 * 面板启动 [KeyEntryServer] 并展示二维码，手机扫码后在网页中提交密钥；
 * 收到合法密钥后写入整合配置并关闭面板。服务无法启动时退化为提示文本。
 */
internal class KeyQrPanel(
    private val context: Context,
) {
    /** 面板内需要动态更新的控件。 */
    private class PanelViews(
        val root: LinearLayout,
        val image: ImageView,
        val address: TextView,
        val status: TextView,
    )

    /** 主线程处理器，用于把服务回调切回界面线程。 */
    private val handler = Handler(Looper.getMainLooper())

    /** 当前对话框。 */
    private var dialog: Dialog? = null

    /** 当前运行的服务。 */
    private var server: KeyEntryServer? = null

    /** 状态文本控件。 */
    private var statusView: TextView? = null

    /** 展示面板。 */
    fun show() {
        if (dialog != null) return
        val views = buildPanel()
        statusView = views.status
        val panel =
            Dialog(context).apply {
                setContentView(views.root)
                window?.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                window?.setBackgroundDrawableResource(android.R.color.transparent)
                setOnDismissListener { stopServer() }
            }
        dialog = panel
        panel.show()
        startServer(views)
    }

    /** 构建面板视图树。 */
    private fun buildPanel(): PanelViews {
        val density = context.resources.displayMetrics.density
        val padding = (24 * density).toInt()
        val root =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                val gap = DesignRuntime.tokens.gap
                setPadding(gap, padding, gap, padding)
                background =
                    GradientDrawable().apply {
                        cornerRadius = 16 * density
                        setColor(InkColors.PAPER)
                    }
            }
        val title = label("扫码录入密钥", 17f, InkColors.INK, Gravity.CENTER_HORIZONTAL)
        val hint =
            label(
                "手机与电脑连接同一局域网，扫描二维码后在网页中粘贴接口密钥。",
                13f,
                InkColors.INK_2,
                Gravity.START,
                8,
            )
        val image =
            ImageView(context).apply {
                layoutParams =
                    LinearLayout.LayoutParams((220 * density).toInt(), (220 * density).toInt()).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                    }
            }
        val address = label("", 12f, InkColors.INK_2, Gravity.CENTER_HORIZONTAL, 10)
        val status = label("正在启动…", 13f, InkColors.INK_2, Gravity.CENTER_HORIZONTAL, 10)
        val close =
            label("关闭", 14f, InkColors.INK, Gravity.CENTER, 16).apply {
                setOnClickListener { dismiss() }
            }
        listOf(title, hint, image, address, status, close).forEach(root::addView)
        return PanelViews(root, image, address, status)
    }

    /** 创建只读文本控件。 */
    private fun label(
        text: String,
        size: Float,
        color: Int,
        gravity: Int,
        topPadding: Int = 0,
    ): TextView =
        TextView(context).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            this.gravity = gravity
            val top = (topPadding * context.resources.displayMetrics.density).toInt()
            setPadding(0, top, 0, 0)
        }

    /** 启动服务并更新二维码与状态。 */
    private fun startServer(views: PanelViews) {
        val entry =
            KeyEntryServer().also { server = it }
        val url =
            entry.start(
                object : KeyEntryServer.Listener {
                    override fun onKey(key: String) {
                        handler.post {
                            saveKey(key)
                            dismiss()
                        }
                    }

                    override fun onClosed(reason: String) {
                        handler.post { statusView?.text = reason }
                    }
                },
            )
        if (url == null) {
            views.image.visibleOrGone(false)
            statusView?.text = "未获取到局域网地址，请确认已连接无线网络后重试。"
            return
        }
        val matrix = runCatching { QrEncoder.encode(url) }.getOrNull()
        if (matrix == null) {
            views.image.visibleOrGone(false)
            statusView?.text = "二维码生成失败，请重试。"
            return
        }
        val bitmap: Bitmap = QrEncoder.render(matrix, QR_PIXELS)
        views.image.setImageBitmap(bitmap)
        views.image.visibleOrGone(true)
        views.address.text = url
        statusView?.text = "等待手机提交…"
    }

    /** 写入密钥并刷新相关设置行。 */
    private fun saveKey(key: String) {
        SettingsUi.edit { config ->
            config.copy(
                integrations =
                    config.integrations.copy(
                        wereadApiKey = key,
                        wereadEnabled = true,
                    ),
            )
        }
        SettingsUi.refresh()
        SettingsUi.toast(context, "密钥已保存")
    }

    /** 关闭服务并移除对话框。 */
    private fun stopServer() {
        server?.stop()
        server = null
    }

    /** 关闭面板。 */
    private fun dismiss() {
        val panel = dialog ?: return
        dialog = null
        stopServer()
        runCatching { panel.dismiss() }
    }

    /** 按条件显示或隐藏控件。 */
    private fun ImageView.visibleOrGone(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** 二维码位图边长，单位像素。 */
    private companion object {
        const val QR_PIXELS = 720
    }
}
