package app.kholo.reader.debug.spike

import android.content.pm.ActivityInfo
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.ext.SdkExtensions
import android.util.Log
import android.util.SparseArray
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.pdf.PdfDocument
import androidx.pdf.SandboxedPdfLoader
import androidx.pdf.view.PdfView
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * M1 spike (sirf debug build). Asli gestures chala kar har zoom niyam ko naapta hai aur logcat me
 * `SPIKE` tag se nateeje likhta hai. Iska code app me nahi jata, nateeje docs/m1-spike-results.md me.
 *
 * adb shell am start -n app.kholo.reader/.debug.spike.PdfSpikeActivity --es file simple.pdf
 *     --es password X --es scenario all
 */
class PdfSpikeActivity : android.app.Activity() {
    private lateinit var pdfView: PdfView
    private val scope: CoroutineScope = MainScope()
    private var locations = SparseArray<RectF>()
    private var lastZoom = 0f

    private fun out(message: String) {
        Log.i("SPIKE", message)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        pdfView = PdfView(this).apply { id = View.generateViewId() }
        root.addView(pdfView, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        val ext = if (Build.VERSION.SDK_INT >= 31) SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) else -1
        out("device sdk=${Build.VERSION.SDK_INT} extS=$ext restored=${savedInstanceState != null}")
        pdfView.addOnViewportChangedListener(
            object : PdfView.OnViewportChangedListener {
                override fun onViewportChanged(
                    firstVisiblePage: Int,
                    visiblePagesCount: Int,
                    pageLocations: SparseArray<RectF>,
                    zoomLevel: Float,
                ) {
                    val copy = SparseArray<RectF>()
                    for (i in 0 until pageLocations.size()) {
                        copy.put(pageLocations.keyAt(i), RectF(pageLocations.valueAt(i)))
                    }
                    locations = copy
                    lastZoom = zoomLevel
                }
            },
        )
        val name = intent.getStringExtra("file") ?: "simple.pdf"
        val password = intent.getStringExtra("password")
        val scenario = if (savedInstanceState != null) "idle" else (intent.getStringExtra("scenario") ?: "none")
        scope.launch { open(name, password, scenario) }
    }

    private suspend fun open(name: String, password: String?, scenario: String) {
        val file = File(filesDir, name).takeIf { it.exists() } ?: File(getExternalFilesDir(null), name)
        val uri = Uri.fromFile(file)
        val start = SystemClock.elapsedRealtime()
        val document = try {
            SandboxedPdfLoader(this).openDocument(uri, password)
        } catch (e: Exception) {
            out("OPEN FAIL file=$name ${e.javaClass.name}: ${e.message}")
            return
        }
        out("OPEN OK file=$name pages=${document.pageCount} took=${SystemClock.elapsedRealtime() - start}ms")
        pdfView.addOnFirstContentLoadListener(
            object : PdfView.OnFirstContentLoadListener {
                override fun onFirstContentLoad() {
                    out("FIRST CONTENT at=${SystemClock.elapsedRealtime() - start}ms")
                }
            },
        )
        if (scenario == "nightearly") night()
        pdfView.pdfDocument = document
        delay(1500)
        when (scenario) {
            "all" -> runAll(document)
            "night" -> {
                night()
                delay(1500)
                pdfView.invalidate()
                pdfView.scrollBy(0, 1)
            }
            "rotateview" -> rotateView()
            "rotatedevice" -> rotateDevice()
            "search" -> search(document)
            "mixed" -> mixed(document)
            else -> state("idle")
        }
        out("SCENARIO DONE $scenario")
    }

    private fun state(label: String) {
        val p0 = locations.get(pdfView.firstVisiblePage)
        out(
            "$label zoom=${"%.3f".format(pdfView.zoom)} min=${"%.2f".format(pdfView.minZoom)} " +
                "scroll=${pdfView.scrollX},${pdfView.scrollY} first=${pdfView.firstVisiblePage} " +
                "view=${pdfView.width}x${pdfView.height} page=$p0",
        )
    }

    private suspend fun runAll(document: PdfDocument) {
        state("A.open")
        val widest = maxPageWidth(document)
        val fit = pdfView.width / widest
        out("A.expectedFit=${"%.3f".format(fit)} actual=${"%.3f".format(pdfView.zoom)}")
        pdfView.minZoom = fit

        val fx = pdfView.width * 0.7f
        val fy = pdfView.height * 0.4f
        val before = pdfView.viewToPdfPoint(fx, fy)
        pinch(fx, fy, startSpan = 200f, endSpan = 700f)
        delay(300)
        val after = before?.let { pdfView.pdfToViewPoint(it) }
        state("B.pinchIn")
        out("B.anchorDrift dx=${after?.let { it.x - fx }} dy=${after?.let { it.y - fy }}")

        delay(1200)
        state("B.afterSettle(sharp re-render window)")

        drag(pdfView.width * 0.2f, pdfView.height * 0.5f, 3000f, 0f)
        delay(300)
        state("D.dragFarRight")
        out("D.leftGap=${locations.get(pdfView.firstVisiblePage)?.left}")
        drag(pdfView.width * 0.8f, pdfView.height * 0.5f, -3000f, 0f)
        delay(300)
        state("D.dragFarLeft")
        out("D.rightGap=${locations.get(pdfView.firstVisiblePage)?.let { pdfView.width - it.right }}")

        val sx = pdfView.scrollX
        drag(pdfView.width * 0.5f, pdfView.height * 0.7f, 40f, -500f)
        delay(300)
        state("E.verticalSwipeWithJitter")
        out("E.scrollXDelta=${pdfView.scrollX - sx}")

        pinch(pdfView.width * 0.5f, pdfView.height * 0.5f, startSpan = 800f, endSpan = 120f)
        pinch(pdfView.width * 0.5f, pdfView.height * 0.5f, startSpan = 800f, endSpan = 120f)
        pinch(pdfView.width * 0.5f, pdfView.height * 0.5f, startSpan = 800f, endSpan = 120f)
        delay(300)
        state("C.pinchOutFully")
        out("C.belowMin=${pdfView.zoom < fit - 0.001f} scrollX=${pdfView.scrollX}")

        val tx = pdfView.width * 0.3f
        val ty = pdfView.height * 0.3f
        val tapBefore = pdfView.viewToPdfPoint(tx, ty)
        doubleTap(tx, ty)
        delay(600)
        state("F.doubleTap1")
        out("F.anchorDrift=${tapBefore?.let { pdfView.pdfToViewPoint(it) }?.let { "${it.x - tx},${it.y - ty}" }}")
        doubleTap(tx, ty)
        delay(600)
        state("F.doubleTap2")

        pdfView.zoom = fit * 2.5f
        delay(200)
        pdfView.zoom = fit
        pdfView.scrollTo(0, pdfView.scrollY)
        delay(300)
        state("G.fitButtonViaPublicApi")
    }

    private suspend fun maxPageWidth(document: PdfDocument): Float {
        var max = 0
        for (i in 0 until document.pageCount) max = maxOf(max, document.getPageInfo(i).width)
        return max.toFloat()
    }

    private suspend fun mixed(document: PdfDocument) {
        for (i in 0 until document.pageCount) {
            val info = document.getPageInfo(i)
            pdfView.scrollToPage(i)
            delay(500)
            val loc = locations.get(i)
            out("H.page=$i pts=${info.width}x${info.height} zoom=${"%.3f".format(pdfView.zoom)} view=$loc")
        }
    }

    private suspend fun search(document: PdfDocument) {
        try {
            val r = document.searchDocument("fox", 0 until minOf(3, document.pageCount))
            out("SEARCH OK pagesWithHits=${r.size()}")
        } catch (e: Exception) {
            out("SEARCH FAIL ${e.javaClass.name}: ${e.message}")
        }
        try {
            val t = document.getPageContent(0)
            out("TEXT OK chars=${t?.textContents?.sumOf { it.text.length }}")
        } catch (e: Exception) {
            out("TEXT FAIL ${e.javaClass.name}: ${e.message}")
        }
    }

    private fun night() {
        val invert = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        pdfView.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = ColorMatrixColorFilter(invert) })
        state("NIGHT applied")
    }

    /** Poore document ko 90 degree ghumana: public API nahi hai, isliye View ko hi ghumate hain. */
    private suspend fun rotateView() {
        val parent = pdfView.parent as View
        val w = parent.width
        val h = parent.height
        pdfView.layoutParams = FrameLayout.LayoutParams(h, w).also {
            it.leftMargin = (w - h) / 2
            it.topMargin = (h - w) / 2
        }
        pdfView.rotation = 90f
        delay(1200)
        state("ROTATEVIEW")
    }

    private suspend fun rotateDevice() {
        pdfView.scrollToPage(3)
        delay(800)
        state("R.beforeRotate")
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    // ---------- Synthetic touch ----------
    private suspend fun pinch(cx: Float, cy: Float, startSpan: Float, endSpan: Float) {
        val down = SystemClock.uptimeMillis()
        var t = down
        fun ev(action: Int, span: Float, count: Int): MotionEvent {
            val props = Array(count) { MotionEvent.PointerProperties().apply { id = it; toolType = MotionEvent.TOOL_TYPE_FINGER } }
            val coords = Array(count) { i ->
                MotionEvent.PointerCoords().apply {
                    x = cx + if (i == 0) -span / 2 else span / 2
                    y = cy
                    pressure = 1f
                    size = 1f
                }
            }
            return MotionEvent.obtain(
                down, t, action, count, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
            )
        }
        pdfView.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, startSpan, 1))
        pdfView.dispatchTouchEvent(ev(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8), startSpan, 2))
        val steps = 24
        for (i in 1..steps) {
            delay(16)
            t += 16
            pdfView.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, startSpan + (endSpan - startSpan) * i / steps, 2))
        }
        t += 16
        pdfView.dispatchTouchEvent(ev(MotionEvent.ACTION_POINTER_UP or (1 shl 8), endSpan, 2))
        pdfView.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, endSpan, 1))
    }

    private suspend fun drag(x: Float, y: Float, dx: Float, dy: Float) {
        val down = SystemClock.uptimeMillis()
        var t = down
        fun ev(a: Int, px: Float, py: Float) = MotionEvent.obtain(down, t, a, px, py, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        pdfView.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, x, y))
        val steps = 30
        for (i in 1..steps) {
            delay(16)
            t += 16
            pdfView.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, x + dx * i / steps, y + dy * i / steps))
        }
        // Rukne ke baad uthao, taaki fling na bane.
        delay(200)
        t += 200
        pdfView.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, x + dx, y + dy))
        pdfView.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, x + dx, y + dy))
    }

    private suspend fun doubleTap(x: Float, y: Float) {
        repeat(2) {
            val down = SystemClock.uptimeMillis()
            pdfView.dispatchTouchEvent(
                MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0).apply {
                    source = InputDevice.SOURCE_TOUCHSCREEN
                },
            )
            delay(40)
            pdfView.dispatchTouchEvent(
                MotionEvent.obtain(down, down + 40, MotionEvent.ACTION_UP, x, y, 0).apply {
                    source = InputDevice.SOURCE_TOUCHSCREEN
                },
            )
            delay(80)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
