package app.kholo.reader.pdf

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.pdf.view.PdfView

private const val HALF_TURN = 180
private const val INVERT_OFFSET = 255f

private val INVERT_MATRIX = floatArrayOf(
    -1f, 0f, 0f, 0f, INVERT_OFFSET,
    0f, -1f, 0f, 0f, INVERT_OFFSET,
    0f, 0f, -1f, 0f, INVERT_OFFSET,
    0f, 0f, 0f, 1f, 0f,
)

/**
 * `PdfView` ka parent. Rotate ka public API nahi hai, isliye poore View ko ghumate hain (M1 me test hua).
 * Hardware layer pehle se on rakhte hain, taaki night mode sirf paint badle aur page kaala na ho.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class PdfViewHost(context: Context, onTap: () -> Unit) : FrameLayout(context) {
    val pdfView = PdfView(context).apply { id = View.generateViewId() }
    private var rotationDegrees = 0
    private var topInset = 0

    init {
        addView(pdfView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        pdfView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        val detector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    onTap()
                    return false
                }
            },
        )
        pdfView.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false
        }
    }

    fun setNight(night: Boolean) {
        val invert = ColorMatrixColorFilter(ColorMatrix(INVERT_MATRIX))
        pdfView.setLayerPaint(if (night) Paint().apply { colorFilter = invert } else null)
    }

    fun setPageRotation(degrees: Int) {
        if (degrees == rotationDegrees) return
        rotationDegrees = degrees
        applyInset()
    }

    /** Page ka upar wala hissa top bar ke neeche na chhupe. */
    fun setTopInset(pixels: Int) {
        if (pixels == topInset) return
        topInset = pixels
        applyInset()
    }

    /** Seedhe page par padding, taaki scroll bar ke neeche ja sake. Ghume hue View me padding bhi ghumti hai. */
    private fun applyInset() {
        pdfView.setPadding(0, if (rotationDegrees == 0) topInset else 0, 0, 0)
        requestLayout()
    }

    private val reservedTop: Int get() = if (rotationDegrees == 0) 0 else topInset

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val fullHeight = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, fullHeight)
        val height = fullHeight - reservedTop
        val sideways = rotationDegrees % HALF_TURN != 0
        val childWidth = if (sideways) height else width
        val childHeight = if (sideways) width else height
        pdfView.measure(
            MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(childHeight, MeasureSpec.EXACTLY),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val childWidth = pdfView.measuredWidth
        val childHeight = pdfView.measuredHeight
        val x = (width - childWidth) / 2
        val y = reservedTop + (height - reservedTop - childHeight) / 2
        pdfView.layout(x, y, x + childWidth, y + childHeight)
        pdfView.rotation = rotationDegrees.toFloat()
    }
}
