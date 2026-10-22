package com.shuiyu.octoberrain.feature.reader.pdf

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SharedMemory
import androidx.annotation.RequiresApi
import java.util.concurrent.Executors

/** 隔离UID没有存储/网络权限；所有PdfRenderer/Page操作进入同一工作线程。 */
@RequiresApi(27)
class PdfWorkerService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private var document: PdfRenderer? = null
    private val binder =
        object : IPdfWorker.Stub() {
            override fun open(
                source: ParcelFileDescriptor,
                result: IPdfResult,
            ) {
                worker.execute {
                    var adopted = false
                    val reply =
                        respond {
                            document?.close()
                            document = null
                            val renderer = PdfRenderer(source)
                            document = renderer
                            adopted = true
                            Bundle().apply { putInt("pages", renderer.pageCount) }
                        }
                    if (!adopted) source.close()
                    deliver(result, reply)
                }
            }

            override fun render(
                page: Int,
                width: Int,
                height: Int,
                zoom: Float,
                centerX: Float,
                centerY: Float,
                crop: Boolean,
                pixels: SharedMemory,
                result: IPdfResult,
            ) {
                worker.execute {
                    val reply =
                        try {
                            respond {
                                val count = PdfViewport.bytes(width, height)
                                require(pixels.size == count)
                                val renderer = checkNotNull(document)
                                require(page in 0 until renderer.pageCount)
                                require(zoom.isFinite() && zoom in 1f..4f)
                                require(
                                    centerX.isFinite() && centerX in 0f..1f && centerY.isFinite() && centerY in 0f..1f,
                                )
                                renderer.openPage(page).use { pdfPage ->
                                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                    try {
                                        bitmap.eraseColor(Color.WHITE)
                                        val scale =
                                            PdfViewport.scale(
                                                pdfPage.width,
                                                pdfPage.height,
                                                width,
                                                height,
                                                zoom,
                                                crop,
                                            )
                                        val matrix =
                                            Matrix().apply {
                                                setScale(scale, scale)
                                                postTranslate(
                                                    width / 2f - pdfPage.width * scale * centerX,
                                                    height / 2f - pdfPage.height * scale * centerY,
                                                )
                                            }
                                        pdfPage.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        val mapped = pixels.mapReadWrite()
                                        try {
                                            bitmap.copyPixelsToBuffer(mapped)
                                        } finally {
                                            SharedMemory.unmap(mapped)
                                        }
                                    } finally {
                                        bitmap.recycle()
                                    }
                                }
                                Bundle()
                            }
                        } finally {
                            pixels.close()
                        }
                    deliver(result, reply)
                }
            }

            override fun closeDocument() {
                worker.execute {
                    document?.close()
                    document = null
                }
            }
        }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        worker.execute {
            document?.close()
            document = null
        }
        worker.shutdown()
        super.onDestroy()
    }

    // 原生异常限在隔离进程；Java错误只返回类型，不能把文档内容/路径带回日志。
    @Suppress("TooGenericExceptionCaught")
    private fun respond(action: () -> Bundle): Bundle =
        try {
            action()
        } catch (_: SecurityException) {
            Bundle().apply { putString("error", "password") }
        } catch (_: Exception) {
            Bundle().apply { putString("error", "corrupt") }
        }

    private fun deliver(
        result: IPdfResult,
        reply: Bundle,
    ) {
        try {
            result.complete(reply)
        } catch (_: android.os.RemoteException) {
            // 宿主已离开，结果丢弃。
        }
    }
}
