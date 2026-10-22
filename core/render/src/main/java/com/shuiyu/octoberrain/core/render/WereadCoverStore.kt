package com.shuiyu.octoberrain.core.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 微信读书书封缓存。
 *
 * 微读原盘的书架数据只带封面地址，不带封面文件。本缓存在首次需要时按书目编号下载封面，
 * 写到应用私有目录，之后直接读本地文件，避免每次绘制或滚动重复请求网络。
 *
 * 地址以 http 或 https 开头时按网络封面处理，其余按本地文件路径处理，便于导入数据
 * 与在线数据共用同一读取入口。
 *
 * 读取、预取、同步下载与解码各自独立，函数数量随缓存职责展开而增加，拆分反而割裂
 * 「查缓存、缺失则下载、解码」这一条顺序，因此对函数数量一项局部豁免。
 */
@Suppress("TooManyFunctions")
object WereadCoverStore {
    /** 封面缓存目录名。 */
    private const val DIRECTORY_NAME: String = "weread_covers"

    /** 单个封面体积上限，超出时放弃写入。 */
    private const val MAX_BYTES: Int = 2 * 1024 * 1024

    /** 网络连接与读取超时，单位毫秒。 */
    private const val TIMEOUT_MS: Int = 8000

    /** 读取缓冲字节数。 */
    private const val BUFFER_BYTES: Int = 16 * 1024

    /** 请求头中的客户端标识，部分封面服务要求非空。 */
    private const val USER_AGENT: String = "Mozilla/5.0 (Linux; Android) OctoberRain"

    /** 下载线程池，单线程避免并发抢占带宽。 */
    private val downloader: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "weread-cover")
        }
    }

    /** 正在下载的书目编号，避免同一本书重复排队。 */
    private val callbacks = ConcurrentHashMap<String, MutableList<(String) -> Unit>>()

    /** 主线程投递器，下载完成后回到主线程回调。 */
    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    /**
     * 取书封对应的本地文件。
     *
     * 网络地址先查缓存目录，本地路径直接判断文件是否存在。
     *
     * @param context 应用上下文
     * @param bookId 书目编号，作为缓存键
     * @param source 封面地址或本地路径
     * @return 可读的本地文件，尚未缓存或不可用时为空
     */
    fun local(
        context: Context,
        bookId: String,
        source: String?,
    ): File? =
        when {
            source.isNullOrBlank() -> null
            isRemote(source) -> cacheFile(context, bookId)?.takeIf { it.isFile && it.length() > 0L }
            else -> File(source).takeIf { it.isFile && it.length() > 0L }
        }

    /**
     * 在后台下载书封并在完成后回调。
     *
     * 同一书目编号在下载期间只排队一次；回调在主线程执行，调用方可在回调里刷新视图。
     *
     * @param context 应用上下文
     * @param bookId 书目编号
     * @param source 封面地址
     * @param onReady 下载成功后的回调，参数为书目编号
     */
    fun prefetch(
        context: Context,
        bookId: String,
        source: String?,
        onReady: (String) -> Unit,
    ): () -> Unit {
        val skip = source.isNullOrBlank() || !isRemote(source) || local(context, bookId, source) != null
        if (skip) return {}
        val start =
            synchronized(callbacks) {
                val waiting = callbacks[bookId]
                if (waiting == null) {
                    callbacks[bookId] = mutableListOf(onReady)
                    true
                } else {
                    waiting.add(onReady)
                    false
                }
            }
        if (start) {
            val appContext = context.applicationContext
            downloader.execute { fetch(appContext, bookId, source) }
        }
        return { synchronized(callbacks) { callbacks[bookId]?.remove(onReady) } }
    }

    /**
     * 确认书封已缓存，缺失时在调用线程内下载。
     *
     * 供后台线程使用，网络请求会阻塞当前线程；主线程路径应改用 [bitmap] 或 [prefetch]。
     *
     * @param context 应用上下文
     * @param bookId 书目编号
     * @param source 封面地址或本地路径
     * @return 可读的本地文件，下载失败时为空
     */
    fun ensure(
        context: Context,
        bookId: String,
        source: String?,
    ): File? = local(context, bookId, source) ?: downloadNow(context, bookId, source)

    /**
     * 解码书封为指定位图。
     *
     * 先查本地缓存，缺失时触发后台下载并返回空，下载完成后由调用方回刷。
     *
     * @param context 应用上下文
     * @param bookId 书目编号
     * @param source 封面地址或本地路径
     * @param maxPx 边长上限
     * @param onReady 后台下载完成后的回调
     * @return 解码后的位图，缓存缺失时为空
     */
    fun bitmap(
        context: Context,
        bookId: String,
        source: String?,
        maxPx: Int,
        onReady: (String) -> Unit,
    ): Bitmap? {
        val file = local(context, bookId, source)
        if (file == null) prefetch(context, bookId, source, onReady)
        return file?.let { decode(it, maxPx) }
    }

    /**
     * 下载任务主体，在后台线程执行。
     *
     * @param context 应用上下文
     * @param bookId 书目编号
     * @param source 封面地址
     * @param onReady 下载成功后的回调
     */
    private fun fetch(
        context: Context,
        bookId: String,
        source: String,
    ) {
        val done =
            runCatching {
                val target = cacheFile(context, bookId) ?: throw IOException("封面缓存目录不可用")
                download(source, target)
            }.isSuccess
        val waiting = synchronized(callbacks) { callbacks.remove(bookId).orEmpty() }
        if (done) mainHandler.post { waiting.forEach { it(bookId) } }
    }

    /**
     * 同步下载并返回文件，在调用线程执行。
     *
     * @param context 应用上下文
     * @param bookId 书目编号
     * @param source 封面地址
     * @return 下载后的文件，失败时为空
     */
    private fun downloadNow(
        context: Context,
        bookId: String,
        source: String?,
    ): File? {
        val target = source?.takeIf { isRemote(it) }?.let { cacheFile(context, bookId) } ?: return null
        return runCatching {
            download(source, target)
            target.takeIf { it.isFile && it.length() > 0L }
        }.getOrNull()
    }

    /**
     * 判断地址是否为网络地址。
     *
     * @param source 封面地址
     * @return 是网络地址时为 true
     */
    private fun isRemote(source: String): Boolean =
        source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true)

    /**
     * 取书目编号对应的缓存文件。
     *
     * 编号中的路径分隔符与非法字符替换为下划线，避免写到目录之外。
     *
     * @param context 应用上下文
     * @param bookId 书目编号
     * @return 缓存文件，编号为空时为空
     */
    private fun cacheFile(
        context: Context,
        bookId: String,
    ): File? {
        if (bookId.isBlank()) return null
        val safe = bookId.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(96)
        return coverDir(context)?.let { File(it, "$safe.img") }
    }

    /**
     * 取封面缓存目录，缺失时创建。
     *
     * @param context 应用上下文
     * @return 可写目录，创建失败时为空
     */
    private fun coverDir(context: Context): File? {
        val dir = File(context.filesDir, DIRECTORY_NAME)
        return if (dir.exists() || dir.mkdirs()) dir else null
    }

    /**
     * 下载封面到指定文件。
     *
     * 先写临时文件再改名，避免中断留下半截文件被当作有效缓存。
     *
     * @param source 封面地址
     * @param target 目标文件
     */
    private fun download(
        source: String,
        target: File,
    ) {
        val connection = URL(source).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return
            val temp = File(target.parentFile, "${target.name}.part")
            connection.inputStream.use { input ->
                temp.outputStream().use { output -> copyLimited(input, output) }
            }
            if (!temp.renameTo(target)) temp.delete()
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 把输入流复制到输出流，超过体积上限时抛异常。
     *
     * @param input 网络输入流
     * @param output 文件输出流
     */
    private fun copyLimited(
        input: InputStream,
        output: OutputStream,
    ) {
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > MAX_BYTES) throw IOException("封面体积超出上限")
            output.write(buffer, 0, read)
        }
    }

    /**
     * 按边长上限采样解码。
     *
     * @param file 封面文件
     * @param maxPx 边长上限
     * @return 解码后的位图，失败时为空
     */
    private fun decode(
        file: File,
        maxPx: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxPx || bounds.outHeight / sample > maxPx) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }
}
