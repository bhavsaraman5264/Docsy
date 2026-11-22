package app.kholo.reader.tools

import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.File
import java.io.InputStream

private const val POINTS_PER_MM = 72f / 25.4f

/** Aadhaar, PAN aur ATM card ID-1 naap ke hain: 85.6 × 54 mm. Print par card asli naap ka aata hai, Xerox jaisa. */
private const val CARD_LONG_MM = 85.6f
private const val TOP_MM = 25f
private const val GAP_MM = 12f
private const val CUT_LINE = 0.5f
private const val CUT_GREY = 0.75f

/** 86 mm par ~470 DPI. Print me saaf, aur photo bekaar badi nahi. */
private const val CARD_PHOTO_SIDE = 1600
private const val CARD_QUALITY = 90
private const val CARD_WORKERS = 2

/** Card ka aage aur peeche ek A4 page par, upar se neeche. Har card ke charon taraf kaatne ke liye halki line. */
internal fun PdfTools.idCardSheet(photos: List<() -> InputStream?>, out: File, progress: ToolProgress) {
    create(out) { doc ->
        val sheet = PDRectangle.A4
        val page = PDPage(sheet)
        doc.addPage(page)
        val long = CARD_LONG_MM * POINTS_PER_MM
        var top = TOP_MM * POINTS_PER_MM
        PDPageContentStream(doc, page).use { stream ->
            pipeline(photos, CARD_WORKERS, { encodePhoto(it, CARD_QUALITY, CARD_PHOTO_SIDE) }) { index, photo ->
                val landscape = photo.width >= photo.height
                val width = if (landscape) long else long * photo.width / photo.height
                val height = if (landscape) long * photo.height / photo.width else long
                val x = (sheet.width - width) / 2
                val y = sheet.height - top - height
                stream.drawImage(JPEGFactory.createFromByteArray(doc, photo.jpeg), x, y, width, height)
                stream.setStrokingColor(CUT_GREY, CUT_GREY, CUT_GREY)
                stream.setLineWidth(CUT_LINE)
                stream.addRect(x, y, width, height)
                stream.stroke()
                top += height + GAP_MM * POINTS_PER_MM
                progress(index + 1, photos.size)
            }
        }
    }
}
