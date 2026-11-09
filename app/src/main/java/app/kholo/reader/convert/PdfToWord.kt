package app.kholo.reader.convert

import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PointF
import androidx.core.graphics.scale
import app.kholo.reader.tools.PdfRestricted
import app.kholo.reader.tools.PdfTools
import app.kholo.reader.tools.flattened
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.io.IOException
import kotlin.math.abs

private const val JPEG_QUALITY = 80
private const val MAX_IMAGE_SIDE = 1600
private const val MIN_IMAGE_POINTS = 12f
private const val SIDEWAYS = 90
private const val HALF_TURN = 180

/** [pictures] un pages ki ginti hai jo text nahi ban paye aur tasveer ki tarah gaye. */
class WordResult(val pictures: Int, val lostImages: Boolean)

private class PlacedImage(val image: PDImage, val width: Float, val height: Float, val top: Float)

/** PDF ko Word banata hai. Har page par khud tay hota hai ki text nikalna hai ya page ko tasveer rakhna hai. */
class PdfToWord(private val tools: PdfTools, private val newScratch: () -> File) {
    fun convert(source: File, out: File, pagePicture: (Int) -> Bitmap?, progress: (Int, Int) -> Unit): WordResult =
        tools.read(source) { doc ->
            if (doc.isEncrypted && !doc.currentAccessPermission.canExtractContent()) throw PdfRestricted()
            val (width, height) = pageSize(doc.getPage(0))
            val reader = PageTextReader()
            var pictures = 0
            var lost = false
            DocxWriter(out, newScratch(), width, height).use { writer ->
                for (index in 0 until doc.numberOfPages) {
                    progress(index, doc.numberOfPages)
                    val page = doc.getPage(index)
                    val images = PageImageFinder(page).find()
                    val runs = reader.read(doc, index + 1)
                    val paragraphs = PageLayout.paragraphs(runs, pageSize(page).first)
                    val text = paragraphs.joinToString(" ") { it.text }
                    val newPage = index > 0
                    if (PageQuality.readable(text, reader.legacyFont, images.size)) {
                        lost = writeTextPage(writer, paragraphs, images, newPage) || lost
                    } else {
                        pictures++
                        writePicturePage(writer, pagePicture(index), newPage)
                    }
                }
                writer.finish()
            }
            WordResult(pictures, lost)
        }

    private fun pageSize(page: PDPage): Pair<Float, Float> {
        val box = page.cropBox
        return if (page.rotation % HALF_TURN == SIDEWAYS) box.height to box.width else box.width to box.height
    }

    /** Tasveerein text ke beech apni oopar-neeche ki jagah par aati hain. */
    private fun writeTextPage(
        writer: DocxWriter,
        paragraphs: List<PageParagraph>,
        images: List<PlacedImage>,
        pageBreak: Boolean,
    ): Boolean {
        var newPage = pageBreak
        var lost = false
        val waiting = images.sortedBy { it.top }.toMutableList()
        fun place(image: PlacedImage) {
            if (writeImage(writer, image, newPage)) {
                newPage = false
            } else {
                lost = true
            }
        }
        paragraphs.forEach { paragraph ->
            while (waiting.isNotEmpty() && waiting.first().top < paragraph.top) place(waiting.removeAt(0))
            writer.paragraph(paragraph, newPage)
            newPage = false
        }
        waiting.forEach { place(it) }
        return lost
    }

    private fun writeImage(writer: DocxWriter, placed: PlacedImage, newPage: Boolean): Boolean {
        val bitmap = decode(placed.image) ?: return false
        val shrink = minOf(1f, writer.textWidth / placed.width, writer.textHeight / placed.height)
        // Recycle nahi: PDFBox tasveer cache karta hai, aur har page ka logo isi bitmap se dobara aata hai.
        writer.picture(placed.width * shrink, placed.height * shrink, newPage) { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        }
        return true
    }

    /** Ek tasveer na padhi jaye to baaki document nahi rukta. */
    private fun decode(image: PDImage): Bitmap? = try {
        val source = image.image
        val side = maxOf(source.width, source.height)
        val shrunk = if (side > MAX_IMAGE_SIDE) {
            val ratio = MAX_IMAGE_SIDE.toFloat() / side
            val width = (source.width * ratio).toInt().coerceAtLeast(1)
            source.scale(width, (source.height * ratio).toInt().coerceAtLeast(1))
        } else {
            source
        }
        shrunk.flattened()
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun writePicturePage(writer: DocxWriter, bitmap: Bitmap?, newPage: Boolean) {
        if (bitmap == null) throw IOException("unreadable page")
        val ratio = bitmap.height.toFloat() / bitmap.width
        val fitsWidth = writer.textWidth * ratio <= writer.textHeight
        val width = if (fitsWidth) writer.textWidth else writer.textHeight / ratio
        val height = if (fitsWidth) writer.textWidth * ratio else writer.textHeight
        try {
            writer.picture(width, height, newPage) { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            }
        } finally {
            bitmap.recycle()
        }
    }
}

/** PDFBox se shabd unki jagah aur font ke saath leta hai. */
private class PageTextReader : PDFTextStripper() {
    private var runs = mutableListOf<TextRun>()
    var legacyFont = false
        private set

    fun read(doc: PDDocument, pageNumber: Int): List<TextRun> {
        runs = mutableListOf()
        legacyFont = false
        startPage = pageNumber
        endPage = pageNumber
        getText(doc)
        return runs
    }

    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        val first = textPositions.firstOrNull() ?: return
        val last = textPositions.last()
        val name = first.font?.name
        if (PageQuality.isLegacyFont(name)) legacyFont = true
        val lower = name.orEmpty().lowercase()
        val size = first.fontSizeInPt.takeIf { it > 0f } ?: first.heightDir
        runs += TextRun(
            text = text,
            x = first.xDirAdj,
            y = first.yDirAdj,
            width = last.xDirAdj + last.widthDirAdj - first.xDirAdj,
            size = size,
            bold = "bold" in lower || "black" in lower || first.font?.fontDescriptor?.isForceBold == true,
            italic = "italic" in lower || "oblique" in lower || first.font?.fontDescriptor?.isItalic == true,
        )
    }
}

/** Page par tasveerein aur unki jagah dhoondta hai. Drawing ka baaki kaam yahan nahi chahiye. */
private class PageImageFinder(private val target: PDPage) : PDFGraphicsStreamEngine(target) {
    private val found = mutableListOf<PlacedImage>()
    private val point = PointF()

    fun find(): List<PlacedImage> {
        processPage(target)
        return found
    }

    override fun drawImage(pdImage: PDImage) {
        val matrix = graphicsState.currentTransformationMatrix
        val width = abs(matrix.scaleX)
        val height = abs(matrix.scaleY)
        if (width < MIN_IMAGE_POINTS || height < MIN_IMAGE_POINTS) return
        val top = target.cropBox.height - matrix.translateY - height
        found += PlacedImage(pdImage, width, height, top)
    }

    override fun appendRectangle(p0: PointF, p1: PointF, p2: PointF, p3: PointF) = Unit

    override fun clip(fillType: Path.FillType) = Unit

    override fun moveTo(x: Float, y: Float) = point.set(x, y)

    override fun lineTo(x: Float, y: Float) = point.set(x, y)

    override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = point.set(x3, y3)

    override fun getCurrentPoint(): PointF = point

    override fun closePath() = Unit

    override fun endPath() = Unit

    override fun strokePath() = Unit

    override fun fillPath(fillType: Path.FillType) = Unit

    override fun fillAndStrokePath(fillType: Path.FillType) = Unit

    override fun shadingFill(shadingName: COSName) = Unit
}
