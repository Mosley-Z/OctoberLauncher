package com.shuiyu.octoberrain.feature.content.ui

import android.graphics.Bitmap
import android.view.View
import com.shuiyu.octoberrain.core.render.ImageDecoder
import com.shuiyu.octoberrain.core.render.ThumbCacheRegistry
import com.shuiyu.octoberrain.core.render.WereadCoverStore
import com.shuiyu.octoberrain.core.render.loadOrDecode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.lang.ref.WeakReference

/** 下载交给共享下载器；本地解码单独执行，视图离页即取消，回填前再次确认绑定。 */
internal object ContentCoverLoader {
    private val decodeSlots = Semaphore(2)

    fun bind(
        view: View,
        source: String,
        sidePx: Int,
        apply: (Bitmap) -> Unit,
    ) {
        val context = view.context.applicationContext
        val reference = WeakReference(view)
        val key = Integer.toHexString(source.hashCode())
        var scope: CoroutineScope? = null
        var task: Job? = null
        var generation = 0
        var cancelDownload: (() -> Unit)? = null

        fun decode() {
            val owner = scope ?: return
            if (task?.isActive == true) return
            val mark = generation
            task =
                owner.launch(Dispatchers.IO) {
                    val bitmap =
                        decodeSlots.withPermit {
                            val file = WereadCoverStore.local(context, key, source) ?: return@withPermit null
                            ThumbCacheRegistry.get(context, sidePx).loadOrDecode(file) {
                                ImageDecoder.decode(file.absolutePath, sidePx)
                            }
                        } ?: return@launch
                    reference.get()?.post {
                        if (mark == generation && reference.get()?.isAttachedToWindow == true) apply(bitmap)
                    }
                }
        }
        val listener =
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                    decode()
                    cancelDownload = WereadCoverStore.prefetch(context, key, source) { decode() }
                }

                override fun onViewDetachedFromWindow(v: View) {
                    generation++
                    cancelDownload?.invoke()
                    cancelDownload = null
                    scope?.cancel()
                    scope = null
                    task = null
                }
            }
        view.addOnAttachStateChangeListener(listener)
        if (view.isAttachedToWindow) listener.onViewAttachedToWindow(view)
    }
}
