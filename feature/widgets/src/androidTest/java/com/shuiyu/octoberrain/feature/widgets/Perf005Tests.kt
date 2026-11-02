package com.shuiyu.octoberrain.feature.widgets

import android.app.Instrumentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.AppearanceConfig
import com.shuiyu.octoberrain.core.common.Destination
import com.shuiyu.octoberrain.core.common.LauncherConfig
import com.shuiyu.octoberrain.core.design.DesignRuntime
import com.shuiyu.octoberrain.core.device.DeviceProfile
import com.shuiyu.octoberrain.core.device.DisplayClass
import com.shuiyu.octoberrain.core.device.HeightClass
import com.shuiyu.octoberrain.core.device.SizeClass
import com.shuiyu.octoberrain.core.widget.WidgetHost
import com.shuiyu.octoberrain.core.widget.WidgetView
import kotlinx.coroutines.Job
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Reflection only observes real production state; it never injects cache events or bitmaps. */
class Perf005Tests(
    private val instrumentation: Instrumentation,
    private val context: Context,
) {
    var stage = "setup"
        private set

    var passed = 0
        private set

    private val draws = AtomicInteger()
    private lateinit var widget: ImageWidget
    private lateinit var surface: Bitmap

    fun run() {
        val fixture = File(context.filesDir, "perf005-" + UUID.randomUUID()).canonicalFile
        check(fixture.parentFile == context.filesDir.canonicalFile)
        check(fixture.mkdir())
        try {
            val first = File(fixture, "a.png")
            png(first, Color.BLACK)
            png(File(fixture, "b.png"), Color.WHITE)
            initializeWidget(first)
            verifyDirectoryChanges(fixture)
            verifyContentChange()
            verifyLifecycle()
            check(!AppServices.isReady())
            stage = "complete"
        } finally {
            main {
                if (::widget.isInitialized) widget.onDestroy()
                if (::surface.isInitialized) surface.recycle()
            }
            check(fixture.parentFile == context.filesDir.canonicalFile)
            check(fixture.deleteRecursively())
        }
    }

    private fun initializeWidget(first: File) {
        main {
            DesignRuntime.install(
                DeviceProfile(
                    widthPx = 320,
                    heightPx = 320,
                    widthDp = 320,
                    heightDp = 320,
                    density = 1f,
                    sizeClass = SizeClass.Compact,
                    heightClass = HeightClass.Short,
                    display = DisplayClass.Lcd,
                    hasFrontLight = false,
                    statusBarInsetPx = 0,
                    navigationInsetPx = 0,
                    longEdgePx = 320,
                    shortEdgePx = 320,
                ),
                AppearanceConfig(),
                null,
            )
            surface = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
            widget = ImageWidget(context)
            widget.host =
                object : WidgetHost {
                    override fun requestWidgetDraw(widget: WidgetView) {
                        widget.draw(Canvas(surface), 320, 320)
                        draws.incrementAndGet()
                    }

                    override fun openDestination(destination: Destination) {
                        error("unexpected-navigation")
                    }
                }
            val config = LauncherConfig()
            widget.onConfigChanged(config.copy(desktop = config.desktop.copy(widgetImagePath = first.path)))
            widget.draw(Canvas(surface), 320, 320)
        }
        await("initial") { idle() && draws.get() > 0 && value(widget, "bitmap") != null && candidates() == 2 }
        main { check(value(widget, "observer") != null) }
        passed++
    }

    private fun verifyDirectoryChanges(fixture: File) {
        repeat(2) { click("unchanged-$it") }
        val appended = File(fixture, "c.png")
        mutation("append", { png(appended, Color.BLACK) }, 3)
        val renamed = File(fixture, "d.png")
        mutation("rename", { check(appended.renameTo(renamed)) }, 3)
        mutation("delete", { check(renamed.delete()) }, 2)
        passed++
    }

    private fun verifyContentChange() {
        var path = ""
        var before = 0
        main {
            path = value(widget, "decodedPath") as String
            before = surface.getPixel(160, 160)
        }
        val selected = File(path)
        val oldTime = selected.lastModified()
        val event = eventVersion()
        png(selected, if (before == Color.BLACK) Color.WHITE else Color.BLACK)
        check(selected.setLastModified(oldTime + 4000))
        settleEvents("content-event", event)
        // Two ordinary clicks return to the same selected path.
        click("content-next")
        click("content-return")
        main {
            check(value(widget, "decodedPath") == path)
            check(surface.getPixel(160, 160) != before)
        }
        passed++
    }

    private fun verifyLifecycle() {
        main { widget.onHostVisible(false) }
        refresh("hidden-static")
        var retained: Any? = null
        main {
            retained = value(widget, "bitmap")
            check(retained != null)
            check(value(widget, "observer") == null)
            check(value(widget, "ticking") == false)
            widget.onHostVisible(false)
            check(value(widget, "bitmap") === retained)
        }
        passed++
        main {
            widget.onDestroy()
            widget.onHostVisible(true)
            widget.onRefresh(true)
        }
        await("destroy-quiescent") {
            value(widget, "bitmap") == null &&
                value(widget, "observer") == null &&
                value(widget, "ticking") == false
        }
        passed++
    }

    private fun mutation(
        label: String,
        change: () -> Unit,
        count: Int,
    ) {
        val version = eventVersion()
        change()
        settleEvents("$label-event", version)
        click("$label-refresh", count)
        await("$label-candidates") { idle() && candidates() == count }
    }

    private fun click(
        label: String,
        expectedCount: Int? = null,
    ) {
        val previous = draws.get()
        var expectedPath: String? = null
        var expectedIndex = -1
        main {
            check(idle())
            check(candidates() >= 2)
            if (expectedCount == null) {
                val files = value(widget, "images") as List<*>
                expectedIndex = ((value(widget, "index") as Int) + 1) % files.size
                expectedPath = (files[expectedIndex] as File).absolutePath
            }
            check(widget.onClick(160f, 160f))
        }
        await(label) {
            idle() &&
                draws.get() > previous &&
                value(widget, "bitmap") != null &&
                if (expectedCount != null) {
                    candidates() == expectedCount
                } else {
                    value(widget, "decodedPath") == expectedPath && value(widget, "index") == expectedIndex
                }
        }
    }

    // Main-thread only: the job includes all merged pending reload iterations.
    private fun idle(): Boolean {
        val field = InkWidgetView::class.java.getDeclaredField("reloadJob")
        field.isAccessible = true
        val job = field.get(widget) as Job?
        return job?.isActive != true
    }

    // A bounded quiet window is evidence of observed quiescence, not an OS delivery guarantee.
    private fun settleEvents(
        label: String,
        previous: Long,
    ) {
        var observed = previous
        var changedAt = SystemClock.uptimeMillis()
        await(label) {
            val current = eventVersion()
            if (current != observed) {
                observed = current
                changedAt = SystemClock.uptimeMillis()
            }
            current > previous && SystemClock.uptimeMillis() - changedAt >= 300
        }
    }

    private fun refresh(label: String) {
        val previous = draws.get()
        main { widget.onRefresh(true) }
        await(label) { idle() && draws.get() > previous && value(widget, "bitmap") != null }
    }

    private fun candidates(): Int = (value(widget, "images") as List<*>).size

    private fun eventVersion(): Long {
        val cache = value(widget, "directoryCache")!!
        val lock = value(cache, "lock")!!
        return synchronized(lock) { value(cache, "eventVersion") as Long }
    }

    private fun value(
        owner: Any,
        name: String,
    ): Any? {
        val field = owner.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(owner)
    }

    private fun await(
        label: String,
        condition: () -> Boolean,
    ) {
        stage = label
        val deadline = SystemClock.uptimeMillis() + 8000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            main { ready = condition() }
            if (ready) return
            SystemClock.sleep(20)
        }
        error("stage-timeout")
    }

    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            try {
                action()
            } catch (error: Throwable) {
                failure = error
            }
        }
        failure?.let { throw it }
    }

    private fun png(
        file: File,
        color: Int,
    ) {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
    }
}
