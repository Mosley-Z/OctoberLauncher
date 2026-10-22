package com.shuiyu.octoberrain.feature.reader.pdf

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.SharedMemory
import androidx.annotation.RequiresApi
import com.shuiyu.octoberrain.core.common.ReaderFailure
import com.shuiyu.octoberrain.core.common.ReaderLocator
import com.shuiyu.octoberrain.feature.reader.source.ReaderSourceException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.Closeable

/** 单会话异步Binder；超时/死亡结束等待，取消结果不进入宿主位图。 */
@RequiresApi(27)
internal class PdfClient(
    context: Context,
) : Closeable {
    private val context = context.applicationContext

    @Volatile private var worker: IPdfWorker? = null
    private val ready = CompletableDeferred<IPdfWorker>()

    @Volatile private var bound = false

    @Volatile private var closed = false

    @Volatile private var pending: CompletableDeferred<Bundle>? = null
    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName,
                service: IBinder,
            ) {
                val connected = IPdfWorker.Stub.asInterface(service)
                worker = connected
                ready.complete(connected)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                failed()
            }

            override fun onBindingDied(name: ComponentName) {
                failed()
            }

            override fun onNullBinding(name: ComponentName) {
                failed()
            }
        }

    suspend fun connect() {
        withContext(Dispatchers.Main.immediate) {
            bound =
                context.bindService(Intent(context, PdfWorkerService::class.java), connection, Context.BIND_AUTO_CREATE)
            if (!bound) throw ReaderSourceException(ReaderFailure.RENDERER)
        }
        try {
            withTimeout(5000) { ready.await() }
        } catch (_: TimeoutCancellationException) {
            stopped()
        }
    }

    suspend fun open(source: ParcelFileDescriptor): Int =
        request { worker, result -> worker.open(source, result) }.getInt("pages")

    suspend fun render(
        location: ReaderLocator.Pdf,
        width: Int,
        height: Int,
        pixels: SharedMemory,
    ) {
        request { worker, result ->
            worker.render(
                location.page,
                width,
                height,
                location.zoom,
                location.centerX,
                location.centerY,
                location.crop,
                pixels,
                result,
            )
        }
    }

    private suspend fun request(send: (IPdfWorker, IPdfResult) -> Unit): Bundle {
        check(!closed)
        val reply = CompletableDeferred<Bundle>()
        pending = reply
        val callback =
            object : IPdfResult.Stub() {
                override fun complete(result: Bundle) {
                    reply.complete(result)
                }
            }
        return try {
            send(ready.await(), callback)
            val result = withTimeout(15000) { reply.await() }
            when (result.getString("error")) {
                "password" -> throw ReaderSourceException(ReaderFailure.PASSWORD)
                "corrupt" -> throw ReaderSourceException(ReaderFailure.CORRUPT)
            }
            result
        } catch (_: RemoteException) {
            stopped()
        } catch (_: TimeoutCancellationException) {
            stopped()
        } finally {
            pending = null
        }
    }

    private fun stopped(): Nothing {
        close()
        throw ReaderSourceException(ReaderFailure.RENDERER)
    }

    private fun failed() {
        worker = null
        val error = ReaderSourceException(ReaderFailure.RENDERER)
        ready.completeExceptionally(error)
        pending?.completeExceptionally(error)
    }

    override fun close() {
        if (!closed) {
            closed = true
            pending?.cancel()
            try {
                worker?.closeDocument()
            } catch (_: RemoteException) {
                // 已死亡，无需远程关闭。
            }
            worker = null
            if (bound) {
                bound = false
                context.unbindService(connection)
            }
        }
    }
}
