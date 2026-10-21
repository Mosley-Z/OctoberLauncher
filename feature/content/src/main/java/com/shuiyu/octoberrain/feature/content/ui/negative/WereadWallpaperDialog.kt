package com.shuiyu.octoberrain.feature.content.ui.negative

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.FileProvider
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.design.InkColors
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.feature.content.ui.ContentUi

/**
 * 墨台壁纸预览对话框。
 *
 * 对话框以全屏方式等比展示内核生成的竖版壁纸位图，并提供保存到相册与分享两项
 * 操作。保存写入公共图片目录下的 TenRain 子目录，分享把位图写入应用缓存目录后
 * 以内容地址交给系统选择器，避免向其他应用暴露文件路径。
 */
internal object WereadWallpaperDialog {
    /**
     * 展示壁纸预览对话框。
     *
     * @param context 用于创建对话框与启动分享的界面上下文
     * @param bitmap 内核渲染得到的壁纸位图
     */
    fun show(
        context: Context,
        bitmap: Bitmap,
    ) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(buildContent(context, bitmap) { dialog.dismiss() })
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.white)
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }

    /**
     * 构建对话框内容视图。
     *
     * @param context 视图上下文
     * @param bitmap 需要预览的位图
     * @param onClose 关闭动作
     * @return 内容视图
     */
    private fun buildContent(
        context: Context,
        bitmap: Bitmap,
        onClose: () -> Unit,
    ): LinearLayout {
        val root =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(InkColors.PAPER)
            }
        root.addView(buildTitleBar(context, onClose))
        root.addView(
            ImageView(context).apply {
                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.FIT_CENTER
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        root.addView(buildActionBar(context, bitmap))
        return root
    }

    /**
     * 构建标题行。
     *
     * @param context 视图上下文
     * @param onClose 关闭动作
     * @return 标题行视图
     */
    private fun buildTitleBar(
        context: Context,
        onClose: () -> Unit,
    ): LinearLayout {
        val tokens = DesignRuntime.tokens
        val bar =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = tokens.topBarHeight
                setPadding(tokens.pageGutter, 0, tokens.pageGutter, 0)
            }
        bar.addView(
            InkWidgets.heading(context, TITLE).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f,
                    )
            },
        )
        bar.addView(ContentUi.actionText(context, CLOSE) { onClose() })
        return bar
    }

    /**
     * 构建保存与分享操作行。
     *
     * @param context 视图上下文
     * @param bitmap 需要保存或分享的位图
     * @return 操作行视图
     */
    private fun buildActionBar(
        context: Context,
        bitmap: Bitmap,
    ): LinearLayout {
        val tokens = DesignRuntime.tokens
        val bar =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(tokens.pageGutter, tokens.gap, tokens.pageGutter, tokens.pageBottom)
            }
        val saveParams =
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            )
        saveParams.marginEnd = tokens.gap
        bar.addView(
            InkWidgets.button(context, SAVE, filled = true) { save(context, bitmap) },
            saveParams,
        )
        bar.addView(
            InkWidgets.button(context, SHARE, filled = false) { share(context, bitmap) },
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ),
        )
        return bar
    }

    /**
     * 把位图保存到相册，结果在主线程提示。
     *
     * @param context 界面上下文
     * @param bitmap 需要保存的位图
     */
    private fun save(
        context: Context,
        bitmap: Bitmap,
    ) {
        val appContext = context.applicationContext ?: context
        val name = WereadWallpaperStore.fileName()
        Thread(
            {
                val stored =
                    runCatching {
                        WereadWallpaperStore.saveToGallery(appContext, name, bitmap)
                    }.getOrDefault(false)
                toastOnMain(
                    if (stored) SAVE_DONE_PREFIX + name else SAVE_FAILED,
                )
            },
            THREAD_NAME,
        ).apply { isDaemon = true }.start()
    }

    /**
     * 把位图写入缓存后交给系统分享选择器。
     *
     * @param context 界面上下文
     * @param bitmap 需要分享的位图
     */
    private fun share(
        context: Context,
        bitmap: Bitmap,
    ) {
        val appContext = context.applicationContext ?: context
        val cached = WereadWallpaperStore.writeShareFile(appContext, bitmap)
        val uri: Uri? =
            cached?.let {
                runCatching {
                    FileProvider.getUriForFile(
                        appContext,
                        appContext.packageName + FILE_PROVIDER_SUFFIX,
                        it,
                    )
                }.getOrNull()
            }
        if (cached == null || uri == null) {
            toastOnMain(SHARE_FAILED)
            return
        }
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = MIME_PNG
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        val chooser = Intent.createChooser(intent, SHARE)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(chooser) }
            .onFailure { toastOnMain(SHARE_FAILED) }
    }

    /**
     * 在主线程显示提示。
     *
     * @param message 提示文案
     */
    private fun toastOnMain(message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            runCatching { AppServices.router.toast(message) }
        }
    }

    /** 文件提供者授权名后缀。 */
    private const val FILE_PROVIDER_SUFFIX: String = ".files"

    /** 保存与分享使用的图片类型。 */
    private const val MIME_PNG: String = "image/png"

    /** 后台写入线程名。 */
    private const val THREAD_NAME: String = "weread-desk-wallpaper"

    /** 对话框标题。 */
    private const val TITLE: String = "墨台壁纸"

    /** 关闭按钮文案。 */
    private const val CLOSE: String = "关闭"

    /** 保存按钮文案。 */
    private const val SAVE: String = "保存到相册"

    /** 分享按钮文案。 */
    private const val SHARE: String = "分享"

    /** 保存成功提示前缀。 */
    private const val SAVE_DONE_PREFIX: String = "已保存到相册 TenRain/"

    /** 保存失败提示。 */
    private const val SAVE_FAILED: String = "保存失败，请检查存储权限"

    /** 分享失败提示。 */
    private const val SHARE_FAILED: String = "分享失败，请稍后重试"
}
