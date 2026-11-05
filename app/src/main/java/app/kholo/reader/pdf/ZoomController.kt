package app.kholo.reader.pdf

import android.graphics.RectF
import android.util.SparseArray
import androidx.pdf.PdfDocument
import androidx.pdf.PdfPoint
import androidx.pdf.view.PdfView
import app.kholo.reader.core.log.AppLog
import app.kholo.reader.core.log.LogTag
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val PAGE_INFO_CHUNK = 50
private const val HIDE_CONTROLS_SCROLL_PX = 24

/** [ZoomRules] ko `PdfView` se jodta hai. Saare calls main thread par hote hain. */
class ZoomController(
    private val view: PdfView,
    private val document: PdfDocument,
    private val startPage: Int,
    private val onPage: (Int) -> Unit,
    private val onFit: (Boolean) -> Unit,
    private val onUserScroll: () -> Unit,
) : PdfView.OnViewportChangedListener {
    private val widths = IntArray(document.pageCount)
    private var widest = 0
    private var opened = false
    private var trackedWidth = 0
    private var trackedZoom = 1f
    private var trackedAnchor: PdfPoint? = null
    private var lastScrollY = 0

    fun attach(scope: CoroutineScope) {
        view.addOnViewportChangedListener(this)
        view.addOnGestureStateChangedListener(
            object : PdfView.OnGestureStateChangedListener {
                override fun onGestureStateChanged(newState: Int) {
                    if (newState == PdfView.GESTURE_STATE_IDLE) settle()
                }
            },
        )
        view.addOnLayoutChangeListener { _, _, _, right, _, _, _, oldRight, _ -> onLayout(right, oldRight) }
        view.addOnFirstContentLoadListener(
            object : PdfView.OnFirstContentLoadListener {
                override fun onFirstContentLoad() {
                    scope.launch { open() }
                }
            },
        )
        scope.launch { loadPageWidths() }
    }

    private suspend fun open() {
        if (widths[startPage] == 0) widths[startPage] = document.getPageInfo(startPage).width
        widest = maxOf(widest, widths[startPage])
        applyLimits()
        view.zoom = fitFor(startPage)
        if (startPage > 0) view.scrollToPage(startPage)
        alignPage(startPage)
        opened = true
        trackedWidth = view.width
        AppLog.d(LogTag.ZOOM) { "opened zoom=${view.zoom} page=$startPage" }
    }

    private suspend fun loadPageWidths() {
        var start = 0
        while (start < widths.size) {
            val end = minOf(start + PAGE_INFO_CHUNK, widths.size) - 1
            document.getPageInfos(start..end).forEach { info ->
                widths[info.pageNum] = info.width
                widest = maxOf(widest, info.width)
            }
            applyLimits()
            start = end + 1
        }
    }

    private fun applyLimits() {
        if (view.width <= 0 || widest <= 0) return
        val min = ZoomRules.minZoom(view.width, widest)
        view.minZoom = min
        view.maxZoom = ZoomRules.maxZoom(min)
    }

    private fun fitFor(page: Int): Float = ZoomRules.fitZoom(view.width, widths.getOrElse(page) { 0 }.ifZero(widest))

    private fun Int.ifZero(other: Int) = if (this == 0) other else this

    private fun currentPage(): Int =
        view.viewToPdfPoint(view.width / 2f, view.height / 2f)?.pageNum ?: view.firstVisiblePage

    /** Fit button: jo page dikh raha hai wo screen ki width me aa jaye, padhne ki jagah na badle. */
    fun fit() {
        val anchor = centerPoint()
        val page = anchor?.pageNum ?: view.firstVisiblePage
        view.zoom = fitFor(page)
        keepOnScreen(anchor)
        alignPage(page)
    }

    private fun centerPoint(): PdfPoint? = view.viewToPdfPoint(view.width / 2f, view.height / 2f)

    private fun keepOnScreen(anchor: PdfPoint?) {
        val point = anchor?.let(view::pdfToViewPoint) ?: return
        view.scrollBy(0, (point.y - view.height / 2f).roundToInt())
    }

    /** Page chauda content ke beech me hota hai, isliye fit ke baad uska bayan kinara screen par lao. */
    private fun alignPage(page: Int) {
        val left = view.pdfToViewPoint(PdfPoint(page, 0f, 0f)) ?: return
        view.scrollBy(left.x.roundToInt(), 0)
    }

    private fun settle() {
        if (!opened) return
        trackedAnchor = centerPoint()
        val page = currentPage()
        onPage(page)
        if (ZoomRules.shouldSnapToFit(view.zoom, fitFor(page))) fit()
        onFit(ZoomRules.isAtFit(view.zoom, fitFor(page)))
        AppLog.d(LogTag.ZOOM) { "settled zoom=${view.zoom} scroll=${view.scrollX},${view.scrollY}" }
    }

    private fun onLayout(right: Int, oldRight: Int) {
        if (!opened || right == oldRight || view.width == trackedWidth) return
        val page = trackedAnchor?.pageNum ?: view.firstVisiblePage
        val pageWidth = widths.getOrElse(page) { 0 }.ifZero(widest)
        val zoom = ZoomRules.zoomAfterResize(trackedZoom, trackedWidth, view.width, pageWidth, widest)
        val wasAtFit = ZoomRules.isAtFit(trackedZoom, ZoomRules.fitZoom(trackedWidth, pageWidth))
        val anchor = trackedAnchor
        trackedWidth = view.width
        view.post {
            applyLimits()
            view.zoom = zoom
            keepOnScreen(anchor)
            if (wasAtFit) alignPage(page)
        }
    }

    override fun onViewportChanged(
        firstVisiblePage: Int,
        visiblePagesCount: Int,
        pageLocations: SparseArray<RectF>,
        zoomLevel: Float,
    ) {
        if (!opened || view.width != trackedWidth) return
        trackedZoom = zoomLevel
        // Gesture ke dauraan har frame par sasta kaam hi karo. Poora hisaab gesture khatam hone par.
        val idle = view.gestureState == PdfView.GESTURE_STATE_IDLE
        if (idle) trackedAnchor = centerPoint()
        val page = if (idle) currentPage() else firstVisiblePage
        onPage(page)
        onFit(ZoomRules.isAtFit(zoomLevel, fitFor(page)))
        val moved = abs(view.scrollY - lastScrollY) > HIDE_CONTROLS_SCROLL_PX
        if (moved && view.gestureState != PdfView.GESTURE_STATE_IDLE) onUserScroll()
        lastScrollY = view.scrollY
    }
}
