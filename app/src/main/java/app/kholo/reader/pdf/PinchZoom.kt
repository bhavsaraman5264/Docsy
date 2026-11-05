package app.kholo.reader.pdf

import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.pdf.view.PdfView
import kotlin.math.roundToInt

/**
 * Library ka pinch ungliyon se bahut peeche rehta hai: poori screen ka pinch sirf 2x hota hai.
 * Library ko gesture chalane dete hain, taaki pinch ke dauraan bitmap render na ho, aur har event ke
 * baad zoom aur position yahan se set karte hain. Isse page ungliyon ke neeche chipka rehta hai.
 */
class PinchZoom(context: Context, private val view: PdfView) : ScaleGestureDetector.SimpleOnScaleGestureListener() {
    private val detector = ScaleGestureDetector(context, this).apply { isQuickScaleEnabled = false }
    private var active = false
    private var targetZoom = 1f
    private var focusX = 0f
    private var focusY = 0f
    private var contentX = 0f
    private var contentY = 0f

    fun onEvent(event: MotionEvent) {
        detector.onTouchEvent(event)
    }

    /** Library ke event sambhalne ke baad chalta hai, taaki aakhri baat hamari rahe. */
    fun apply() {
        if (!active || view.gestureState == PdfView.GESTURE_STATE_IDLE) return
        view.zoom = targetZoom
        view.scrollTo(
            ZoomRules.scrollFor(contentX, targetZoom, focusX, inset = 0).roundToInt(),
            ZoomRules.scrollFor(contentY, targetZoom, focusY, view.paddingTop).roundToInt(),
        )
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        active = true
        targetZoom = view.zoom
        focusX = detector.focusX
        focusY = detector.focusY
        contentX = ZoomRules.contentAt(focusX, view.scrollX, targetZoom, inset = 0)
        contentY = ZoomRules.contentAt(focusY, view.scrollY, targetZoom, view.paddingTop)
        return true
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        targetZoom = (targetZoom * detector.scaleFactor).coerceIn(view.minZoom, view.maxZoom)
        focusX = detector.focusX
        focusY = detector.focusY
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        active = false
    }
}
