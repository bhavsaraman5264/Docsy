package app.kholo.reader.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.util.Matrix
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot

private const val MARK_TEXT_PX = 160f
private const val MARK_MAX_PX = 4000f
private const val MARK_ALPHA = 80
private const val MARK_GREY = 110
private const val MARK_PAD = 0.3f
private const val MARK_LENGTH = 0.75f
private const val MARK_MAX_HEIGHT = 0.16f
private const val NUMBER_SIZE = 11f
private const val NUMBER_GAP = 24f
private const val NUMBER_SIDE = 36f
private const val FONT_UNITS = 1000f
private const val NUMBER_GREY = 0.2f
private const val HALF_TURN_DEGREES = 180
private const val THREE_QUARTER_DEGREES = 270

/**
 * Page jaisa screen par dikhta hai, ghuma hua ho to bhi. Iske [matrix] ke baad (0,0) dikhne wala
 * neeche-baayan kona hai.
 */
internal class VisiblePage(page: PDPage) {
    val width: Float
    val height: Float
    val matrix: Matrix

    init {
        val box = page.cropBox
        val x = box.lowerLeftX
        val y = box.lowerLeftY
        val w = box.width
        val h = box.height
        // PDF me Rotate ghadi ki disha me hota hai.
        when ((page.rotation % FULL_TURN + FULL_TURN) % FULL_TURN) {
            QUARTER_TURN -> {
                width = h
                height = w
                matrix = Matrix(0f, 1f, -1f, 0f, x + w, y)
            }
            HALF_TURN_DEGREES -> {
                width = w
                height = h
                matrix = Matrix(-1f, 0f, 0f, -1f, x + w, y + h)
            }
            THREE_QUARTER_DEGREES -> {
                width = h
                height = w
                matrix = Matrix(0f, -1f, 1f, 0f, x, y + h)
            }
            else -> {
                width = w
                height = h
                matrix = Matrix.getTranslateInstance(x, y)
            }
        }
    }
}

/** Page ke purane content ke upar likhta hai. Purana content apni jagah rehta hai, kuch nahi mitta. */
private inline fun PDDocument.drawOn(page: PDPage, block: (PDPageContentStream, VisiblePage) -> Unit) {
    val visible = VisiblePage(page)
    PDPageContentStream(this, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
        stream.transform(visible.matrix)
        block(stream, visible)
    }
}

/**
 * Har page par tirchha, halka watermark. Text Android khud banata hai aur tasveer ki tarah lagta hai, isliye Hindi
 * ke sanyukt akshar bhi sahi aate hain aur app me koi font nahi daalna padta. Tasveer PDF me ek hi baar judti hai.
 */
internal fun PDDocument.addWatermark(text: String, progress: ToolProgress) {
    val bitmap = watermarkBitmap(text)
    val image = try {
        LosslessFactory.createFromImage(this, bitmap)
    } finally {
        bitmap.recycle()
    }
    val count = numberOfPages
    pages.forEachIndexed { index, page ->
        progress(index, count)
        drawOn(page) { stream, visible ->
            val diagonal = hypot(visible.width, visible.height)
            var width = diagonal * MARK_LENGTH
            var height = width * image.height / image.width
            val maxHeight = minOf(visible.width, visible.height) * MARK_MAX_HEIGHT
            if (height > maxHeight) {
                width *= maxHeight / height
                height = maxHeight
            }
            stream.transform(Matrix.getTranslateInstance(visible.width / 2, visible.height / 2))
            stream.transform(Matrix.getRotateInstance(atan2(visible.height, visible.width).toDouble(), 0f, 0f))
            stream.drawImage(image, -width / 2, -height / 2, width, height)
        }
    }
}

private fun watermarkBitmap(text: String): Bitmap {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(MARK_ALPHA, MARK_GREY, MARK_GREY, MARK_GREY)
        typeface = Typeface.DEFAULT_BOLD
        textSize = MARK_TEXT_PX
    }
    val textWidth = paint.measureText(text)
    if (textWidth > MARK_MAX_PX) paint.textSize *= MARK_MAX_PX / textWidth
    val metrics = paint.fontMetrics
    val pad = paint.textSize * MARK_PAD
    val width = ceil(paint.measureText(text) + pad * 2).toInt().coerceAtLeast(1)
    val height = ceil(metrics.descent - metrics.ascent + pad).toInt().coerceAtLeast(1)
    val bitmap = createBitmap(width, height)
    Canvas(bitmap).drawText(text, pad, pad / 2 - metrics.ascent, paint)
    return bitmap
}

enum class NumberStyle { Plain, OfTotal }

enum class NumberPlace { Center, Right }

/** Neeche page number. Sirf ank hain, isliye PDF ka apna font kaafi hai. */
internal fun PDDocument.addPageNumbers(style: NumberStyle, place: NumberPlace, progress: ToolProgress) {
    val font = PDType1Font.HELVETICA
    val count = numberOfPages
    pages.forEachIndexed { index, page ->
        progress(index, count)
        val label = if (style == NumberStyle.OfTotal) "${index + 1} / $count" else "${index + 1}"
        drawOn(page) { stream, visible ->
            val textWidth = font.getStringWidth(label) / FONT_UNITS * NUMBER_SIZE
            // Rasid jaise chhote page par kinare ki doori bhi chhoti.
            val side = minOf(NUMBER_SIDE, visible.width / 10)
            val x = when (place) {
                NumberPlace.Right -> visible.width - side - textWidth
                NumberPlace.Center -> (visible.width - textWidth) / 2
            }
            val y = minOf(NUMBER_GAP, visible.height / 10)
            stream.beginText()
            stream.setFont(font, NUMBER_SIZE)
            stream.setNonStrokingColor(NUMBER_GREY, NUMBER_GREY, NUMBER_GREY)
            stream.newLineAtOffset(x, y)
            stream.showText(label)
            stream.endText()
        }
    }
}
