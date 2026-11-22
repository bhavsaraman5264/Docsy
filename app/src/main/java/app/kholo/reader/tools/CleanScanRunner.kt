package app.kholo.reader.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import app.kholo.reader.R
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.filter.FilterFactory
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceGray
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.roundToInt

/** Scan aam taur par 150–300 DPI ke hote hain. 200 par akshar saaf rehte hain aur page ~15 MB RAM leta hai. */
private const val CLEAN_DPI = 200
private const val CLEAN_QUALITY = 80

/** Render ek samay ek hi page, par saaf karna aur JPEG saath chalte hain. Kam RAM wale phone ke liye do hi. */
private const val CLEAN_WORKERS = 2

/** Page pankti-pankti badalta hai, taaki poore page ki doosri copy RAM me na bane. */
private const val STRIP_ROWS = 64

/** B&W ki seema Otsu se aati hai, par bahut halka ya bahut gehra page bhi theek rahe. */
private const val INK_MIN = 110
private const val INK_MAX = 200
private const val PIXELS_PER_BYTE = 8
private const val TOP_BIT = 0x80
private const val CCITT_G4 = -1
private const val CORNER_COUNT = 4

/** A4 ki chaudai, jaise photos se bani PDF me: kata kaagaz apni shakal me, print ke laayak chaudai par. */
private const val SHEET_WIDTH = 595.28f

/**
 * Ek saaf page. [image] null ho to kuch badalna hi nahi tha, aur asli page jaisa ka taisa jata hai: quality
 * aur size dono wahi.
 */
private class CleanedPage(
    val image: ByteArray?,
    val bilevel: Boolean,
    val width: Int,
    val height: Int,
    val blank: Boolean,
    val found: Boolean,
    /** Photo se kata kaagaz: page ko purane page ki jagah kaagaz ki apni shakal milti hai. */
    val cropped: Boolean,
)

/** Scan wali PDF se ungli, kinare, shadow, chhed aur daane hatata hai. Text kabhi nahi mitta, dekho [planPage]. */
internal class CleanScanRunner(private val choice: CleanChoice) : ToolRunner {
    override val suffix = "clean"

    override fun run(job: ToolJob, progress: ToolProgress): ToolOutput {
        val out = job.newFile()
        val pages = job.state.pageCount
        var found = 0
        progress(0, pages)
        job.pdf.rebuild(job.source(), out) { source, result ->
            pipeline((0 until pages).toList(), CLEAN_WORKERS, { cleanPage(job, it) }) { index, page ->
                if (page.found) found++
                // Saare page khaali hon to bhi PDF khaali nahi banti: aakhri page rehta hai.
                val drop = page.blank && CleanFix.Blank in choice
                if (!drop || (index == pages - 1 && result.numberOfPages == 0)) addPage(source, result, index, page)
                progress(index + 1, pages)
            }
        }
        val note = when {
            found > 0 -> R.string.clean_found
            !choice.light -> R.string.clean_nothing
            else -> null
        }
        return ToolOutput(out, note)
    }

    private fun cleanPage(job: ToolJob, index: Int): CleanedPage {
        val rendered = job.pageImage(index, CLEAN_DPI) ?: throw IOException("unreadable page")
        // Pehle photo me se kaagaz katta hai; baaki sab kaam kate page par.
        val bitmap = if (CleanFix.Crop in choice) cutPaper(rendered) else rendered
        val cropped = bitmap !== rendered
        // Kata page naya bitmap hai; purani photo ki ab zaroorat nahi.
        if (cropped) rendered.recycle()
        val width = bitmap.width
        val height = bitmap.height
        val plan: CleanPlan
        val image: ByteArray?
        try {
            plan = planOf(bitmap)
            image = cleaned(bitmap, plan, cropped)
        } finally {
            bitmap.recycle()
        }
        val changed = image != null
        val bilevel = choice.blackWhite && changed
        return CleanedPage(image, bilevel, width, height, plan.blank, plan.found && changed, cropped)
    }

    /** Photo me kaagaz mile to use seedha kaat kar naya bitmap, warna wahi photo. */
    private fun cutPaper(photo: Bitmap): Bitmap {
        val scale = PLAN_SIDE.toFloat() / maxOf(photo.width, photo.height)
        val width = (photo.width * scale).roundToInt().coerceAtLeast(1)
        val height = (photo.height * scale).roundToInt().coerceAtLeast(1)
        val small = photo.scale(width, height)
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        if (small !== photo) small.recycle()
        val found = findPaper(pixels, width, height) ?: return photo
        val factor = photo.width.toFloat() / width
        val corners = FloatArray(found.size) { found[it] * factor }
        val (pageWidth, pageHeight) = paperSize(corners)
        val w = pageWidth.toFloat()
        val h = pageHeight.toFloat()
        val page = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
        val matrix = Matrix().apply { setPolyToPoly(corners, 0, page, 0, CORNER_COUNT) }
        val out = createBitmap(pageWidth, pageHeight)
        Canvas(out).drawBitmap(photo, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** Saaf page ki file, ya null agar kuch badla hi nahi. */
    private fun cleaned(bitmap: Bitmap, plan: CleanPlan, cropped: Boolean): ByteArray? {
        val dots = CleanFix.Dots in choice
        // Kaatna, roshni, gandagi ya tedhapan ho to page pakka badlega. Sirf daane hon to dekhna padega.
        val sure = cropped || choice.light || plan.found || plan.tilt != 0f
        if (!sure && !dots) return null
        val painter = PlanPainter(plan, bitmap.width, bitmap.height)
        val dark = if (dots) ByteArray(bitmap.width * bitmap.height) else null
        paint(bitmap, painter, dark)
        val specks = if (dark != null) clearSpecks(bitmap, painter, dark) else 0
        if (sure || specks > 0) sharpenPage(bitmap)
        return if (sure || specks > 0) encode(bitmap, plan, painter) else null
    }

    private fun encode(bitmap: Bitmap, plan: CleanPlan, painter: PlanPainter): ByteArray {
        val fill = if (choice.light) Color.WHITE else plan.paper
        val page = if (plan.tilt != 0f) bitmap.straightened(plan.tilt, fill) else bitmap
        try {
            return if (choice.blackWhite) {
                bilevel(page, otsu(painter.histogram).coerceIn(INK_MIN, INK_MAX))
            } else {
                ByteArrayOutputStream().also { page.compress(Bitmap.CompressFormat.JPEG, CLEAN_QUALITY, it) }
                    .toByteArray()
            }
        } finally {
            if (page !== bitmap) page.recycle()
        }
    }

    private fun planOf(bitmap: Bitmap): CleanPlan {
        val scale = PLAN_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val small = bitmap.scale(width, height)
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        if (small !== bitmap) small.recycle()
        return planPage(pixels, width, height, choice)
    }

    private fun paint(bitmap: Bitmap, painter: PlanPainter, dark: ByteArray?) {
        val width = bitmap.width
        val rows = IntArray(width * STRIP_ROWS)
        var top = 0
        while (top < bitmap.height) {
            val count = minOf(STRIP_ROWS, bitmap.height - top)
            bitmap.getPixels(rows, 0, width, 0, top, width, count)
            for (row in 0 until count) painter.paint(rows, row * width, top + row, dark)
            bitmap.setPixels(rows, 0, width, 0, top, width, count)
            top += count
        }
    }

    private fun sharpenPage(bitmap: Bitmap) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        sharpen(pixels, bitmap.width, bitmap.height)
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    /** Akele daane kaagaz ke rang ke ho jaate hain. Kitne mite, woh lautata hai. */
    private fun clearSpecks(bitmap: Bitmap, painter: PlanPainter, dark: ByteArray): Int {
        val width = bitmap.width
        val marks = speckles(dark, width, bitmap.height)
        var count = 0
        for (at in marks.indices) {
            if (marks[at].toInt() == 0) continue
            bitmap.setPixel(at % width, at / width, painter.paperAt(at % width, at / width))
            count++
        }
        return count
    }

    private fun addPage(source: PDDocument, result: PDDocument, index: Int, page: CleanedPage) {
        val original = source.getPage(index)
        val image = page.image
        if (image == null) {
            result.importPage(original)
            return
        }
        val box = if (page.cropped) {
            PDRectangle(SHEET_WIDTH, SHEET_WIDTH * page.height / page.width)
        } else {
            VisiblePage(original).let { PDRectangle(it.width, it.height) }
        }
        val picture = if (page.bilevel) {
            bilevelImage(result, image, page.width, page.height)
        } else {
            JPEGFactory.createFromByteArray(result, image)
        }
        val made = PDPage(box)
        result.addPage(made)
        PDPageContentStream(result, made).use { it.drawImage(picture, 0f, 0f, box.width, box.height) }
    }
}

/** Page ko [tilt] ulta ghuma kar seedhi nayi tasveer. Kone [fill] se bharte hain. */
private fun Bitmap.straightened(tilt: Float, fill: Int): Bitmap {
    val out = createBitmap(width, height)
    out.eraseColor(fill)
    Canvas(out).apply {
        rotate(-tilt, width / 2f, height / 2f)
        drawBitmap(this@straightened, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
    }
    return out
}

/**
 * Kaala-safed page CCITT G4 me: fax wala tareeka, jo kaagaz ke page ko JPEG se kai guna chhota rakhta hai.
 * Kaala pixel 1 bit, jaisa PDFBox ka apna CCITTFactory karta hai.
 */
private fun bilevel(bitmap: Bitmap, limit: Int): ByteArray {
    val width = bitmap.width
    val height = bitmap.height
    val rowBytes = (width + PIXELS_PER_BYTE - 1) / PIXELS_PER_BYTE
    val packed = ByteArray(rowBytes * height)
    val row = IntArray(width)
    for (y in 0 until height) {
        bitmap.getPixels(row, 0, width, 0, y, width, 1)
        for (x in 0 until width) {
            val pixel = row[x]
            if (luma(Color.red(pixel), Color.green(pixel), Color.blue(pixel)) >= limit) continue
            val at = y * rowBytes + x / PIXELS_PER_BYTE
            packed[at] = (packed[at].toInt() or (TOP_BIT ushr (x % PIXELS_PER_BYTE))).toByte()
        }
    }
    val encoded = ByteArrayOutputStream()
    FilterFactory.INSTANCE.getFilter(COSName.CCITTFAX_DECODE)
        .encode(ByteArrayInputStream(packed), encoded, faxParams(width, height), 0)
    return encoded.toByteArray()
}

private fun faxParams(width: Int, height: Int) = COSDictionary().apply {
    setInt(COSName.COLUMNS, width)
    setInt(COSName.ROWS, height)
}

private fun bilevelImage(doc: PDDocument, encoded: ByteArray, width: Int, height: Int): PDImageXObject {
    val image = PDImageXObject(
        doc,
        ByteArrayInputStream(encoded),
        COSName.CCITTFAX_DECODE,
        width,
        height,
        1,
        PDDeviceGray.INSTANCE,
    )
    val params = faxParams(width, height).apply { setInt(COSName.K, CCITT_G4) }
    image.cosObject.setItem(COSName.DECODE_PARMS, params)
    return image
}
