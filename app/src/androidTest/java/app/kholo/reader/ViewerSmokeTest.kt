package app.kholo.reader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.Window
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

private const val TAG = "ViewerSmoke"
private const val OPEN_WAIT_MS = 6_000L
private const val SWIPES = 6
private const val FRAME_BUDGET_NS = 16_666_667L

/**
 * Har file type asli ViewerActivity me khulta hai, upar-neeche scroll hota hai, aur app zinda rehna chahiye.
 * Frames ka hisaab logcat me `ViewerSmoke`. Debug build hai, isliye yeh sirf aapas me tulna ke liye.
 * Screenshots `files/shots` me.
 */
@RunWith(AndroidJUnit4::class)
class ViewerSmokeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app: Context get() = instrumentation.targetContext

    private fun uriFor(name: String): Uri {
        val file = File(app.cacheDir, "open/smoke-$name").apply { parentFile?.mkdirs() }
        instrumentation.context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
        return FileProvider.getUriForFile(app, "${app.packageName}.files", file)
    }

    private fun swipe(activity: Activity, up: Boolean) {
        val view = activity.window.decorView
        val x = view.width / 2f
        val low = view.height * 0.75f
        val high = view.height * 0.25f
        val (from, to) = if (up) low to high else high to low
        val start = SystemClock.uptimeMillis()
        val steps = 12

        // Seedha app ki window ko: Xiaomi par bahar se input bhejna (INJECT_EVENTS) band hai.
        fun send(action: Int, y: Float) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, y, 0)
            instrumentation.runOnMainSync { view.dispatchTouchEvent(event) }
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, from)
        for (i in 1..steps) {
            Thread.sleep(8)
            send(MotionEvent.ACTION_MOVE, from + (to - from) * i / steps)
        }
        send(MotionEvent.ACTION_UP, to)
        Thread.sleep(300)
    }

    private fun shot(name: String) {
        val dir = File(app.getExternalFilesDir(null), "shots").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { image ->
            File(dir, "viewer-$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun opensAndScrolls(name: String, mime: String) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setClass(app, ViewerActivity::class.java)
            .setDataAndType(uriFor(name), mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ActivityScenario.launch<ViewerActivity>(intent).use { scenario ->
            Thread.sleep(OPEN_WAIT_MS)
            assertEquals("$name closed itself", Lifecycle.State.RESUMED, scenario.state)
            shot("$name-open")
            val frames = AtomicInteger()
            val slow = AtomicInteger()
            var window: Window? = null
            val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                frames.incrementAndGet()
                if (metrics.getMetric(FrameMetrics.TOTAL_DURATION) > FRAME_BUDGET_NS) slow.incrementAndGet()
            }
            scenario.onActivity { activity ->
                window = activity.window
                activity.window.addOnFrameMetricsAvailableListener(listener, activity.window.decorView.handler)
            }
            lateinit var activity: Activity
            scenario.onActivity { activity = it }
            repeat(SWIPES) { swipe(activity, up = true) }
            repeat(SWIPES / 2) { swipe(activity, up = false) }
            Thread.sleep(1_500)
            instrumentation.runOnMainSync { window?.removeOnFrameMetricsAvailableListener(listener) }
            shot("$name-scrolled")
            assertEquals("$name died while scrolling", Lifecycle.State.RESUMED, scenario.state)
            // Chhoti file screen me hi aa jati hai, tab scroll par naya frame banta hi nahi.
            Log.i(TAG, "$name frames=${frames.get()} over16ms=${slow.get()}")
        }
    }

    @Test fun longPdf() = opensAndScrolls("long-500-pages.pdf", "application/pdf")

    @Test fun mixedSizePdf() = opensAndScrolls("mixed-sizes.pdf", "application/pdf")

    @Test fun rotatedPdf() = opensAndScrolls("rotated-pages.pdf", "application/pdf")

    @Test fun scannedPdf() = opensAndScrolls("scan-like.pdf", "application/pdf")

    @Test fun lockedPdfAsksForPassword() = opensAndScrolls("Aadhaar_fake_ASHA1990.pdf", "application/pdf")

    @Test fun longWord() = opensAndScrolls(
        "word-3000.docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    )

    @Test fun richWord() = opensAndScrolls(
        "rich.docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    )

    @Test fun oldWord() = opensAndScrolls("old.doc", "application/msword")

    @Test fun excel() = opensAndScrolls(
        "rich.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    )

    @Test fun bigExcel() = opensAndScrolls(
        "big-100k-rows.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    )

    @Test fun powerPoint() = opensAndScrolls(
        "sample.pptx",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    )

    @Test fun csv() = opensAndScrolls("small.csv", "text/csv")

    @Test fun text() = opensAndScrolls("plain.txt", "text/plain")
}
