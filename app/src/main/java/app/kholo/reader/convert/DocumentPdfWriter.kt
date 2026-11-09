package app.kholo.reader.convert

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import app.kholo.reader.docx.DocBlock
import app.kholo.reader.docx.DocCell
import app.kholo.reader.docx.PAGE_NUMBER_MARK
import app.kholo.reader.docx.PAGE_TOTAL_MARK
import app.kholo.reader.docx.PageSetup
import app.kholo.reader.docx.ParsedDocument
import app.kholo.reader.docx.parseColor
import java.io.File
import kotlin.math.roundToInt

private const val BODY_SIZE_PT = 11f
private const val LINE_SPACING = 1.1f
private const val CELL_PADDING_PT = 4f
private const val LIST_INDENT_PT = 18f
private const val MARKER_GAP_PT = 14f
private const val MAX_INDENT_SHARE = 0.6f
private const val MARGIN_BAND_PT = 30f
private const val BLOCK_GAP_PT = 6f
private const val BORDER_PT = 0.5f
private const val BORDER_COLOR = 0xFF9AA0A6.toInt()
private const val PICTURE_SIDE = 1600
private const val MEASURE_SIDE = 64
private const val PIXEL_TO_POINT = 0.75f
private const val KEEP_WITH_NEXT_LINES = 3
private const val PROGRESS_EVERY = 20
private const val TEST_PAGE_NUMBER = "99"

private val HEADING_SCALE = floatArrayOf(1.8f, 1.5f, 1.3f, 1.15f, 1.05f, 1f)

/** Office file ki tasveer. Na mile to null. */
fun interface ImageLoader {
    fun load(path: String, maxSide: Int): Bitmap?
}

/**
 * Document ko asli PDF pages me likhta hai. PDFBox ki jagah Android ka [PdfDocument] isliye, kyunki yeh
 * system font aur shaping use karta hai, aur Hindi ke akshar sahi judte hain. Size bhi nahi badhta.
 */
class DocumentPdfWriter(private val images: ImageLoader?, private val progress: (Int, Int) -> Unit) {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = BODY_SIZE_PT
        color = Color.BLACK
        // Chhote size par bina iske akshar ki doori PDF me bigad jati hai.
        isSubpixelText = true
        isLinearText = true
    }

    /** True ka matlab kuch content PDF me nahi aa paya, aur user ko batana hai. */
    fun write(document: ParsedDocument, out: File): Boolean {
        val marks = document.header + document.footer
        val total = if (marks.any { PAGE_TOTAL_MARK in it.text.text }) Pass(document, null, 0).run().pages else 0
        val pdf = PdfDocument()
        try {
            val pass = Pass(document, pdf, total).run()
            out.outputStream().use(pdf::writeTo)
            return pass.lost
        } finally {
            pdf.close()
        }
    }

    /** Ek poora chakkar. Bina [pdf] ke sirf pages ginta hai, kyunki "Page 2 of 9" ke liye kul pehle chahiye. */
    private inner class Pass(
        private val document: ParsedDocument,
        private val pdf: PdfDocument?,
        private val total: Int,
    ) {
        private val setup: PageSetup = document.page
        private val left = setup.marginLeft
        private val width = setup.width - setup.marginLeft - setup.marginRight
        private val headerHeight = bandHeight(document.header)
        private val footerHeight = bandHeight(document.footer)
        private val top = maxOf(setup.marginTop, MARGIN_BAND_PT + headerHeight + BLOCK_GAP_PT)
        private val bottom = minOf(
            setup.height - setup.marginBottom,
            setup.height - MARGIN_BAND_PT - footerHeight - BLOCK_GAP_PT,
        )
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = top
        var pages = 0
            private set
        var lost = false
            private set

        fun run(): Pass {
            newPage()
            val blocks = document.blocks
            blocks.forEachIndexed { index, block ->
                if (index % PROGRESS_EVERY == 0) progress(index, blocks.size)
                when (block) {
                    is DocBlock.Paragraph -> paragraph(block)
                    is DocBlock.Table -> table(block)
                    is DocBlock.Image -> picture(block)
                    is DocBlock.SlideHeader, DocBlock.PageBreak -> if (y > top) newPage()
                    DocBlock.Skipped -> lost = true
                }
            }
            if (document.notes.isNotEmpty()) {
                y += BLOCK_GAP_PT
                document.notes.forEach(::paragraph)
            }
            finishPage()
            return this
        }

        private fun newPage() {
            finishPage()
            pages++
            y = top
            val target = pdf ?: return
            val info = PdfDocument.PageInfo.Builder(setup.width.roundToInt(), setup.height.roundToInt(), pages).create()
            val started = target.startPage(info)
            page = started
            canvas = started.canvas
            drawBand(document.header, MARGIN_BAND_PT)
            drawBand(document.footer, setup.height - MARGIN_BAND_PT - footerHeight)
        }

        private fun finishPage() {
            page?.let { pdf?.finishPage(it) }
            page = null
            canvas = null
        }

        private fun bandLayouts(band: List<DocBlock.Paragraph>): List<StaticLayout> = band.map { block ->
            val number = if (pages > 0) pages.toString() else TEST_PAGE_NUMBER
            val pagesText = if (total > 0) total.toString() else TEST_PAGE_NUMBER
            layout(styled(block).withPageNumbers(number, pagesText), width, block.align)
        }

        private fun bandHeight(band: List<DocBlock.Paragraph>): Float = bandLayouts(band).sumOf { it.height }.toFloat()

        private fun drawBand(band: List<DocBlock.Paragraph>, start: Float) {
            val target = canvas ?: return
            var at = start
            bandLayouts(band).forEach { line ->
                target.save()
                target.translate(left, at)
                line.draw(target)
                target.restore()
                at += line.height
            }
        }

        private fun paragraph(block: DocBlock.Paragraph) {
            if (y > top) y += block.spaceBefore
            val base = if (block.leftIndent > 0f) block.leftIndent else block.indent * LIST_INDENT_PT
            val listShift = if (block.marker != null && block.leftIndent <= 0f) LIST_INDENT_PT else 0f
            val indent = (base + listShift).coerceAtMost(width * MAX_INDENT_SHARE)
            val text = layout(styled(block), width - indent, block.align)
            if (block.heading > 0 && text.lineCount > 0) {
                val needed = text.getLineBottom(0) * KEEP_WITH_NEXT_LINES
                if (y + needed > bottom && y > top) newPage()
            }
            flow(text, left + indent, block.marker)
            y += block.spaceAfter
        }

        /** Paragraph page ke beech se bhi toot sakta hai, par hamesha line par. Koi line aadhi nahi kat-ti. */
        private fun flow(text: StaticLayout, x: Float, marker: String? = null) {
            var line = 0
            while (line < text.lineCount) {
                val from = text.getLineTop(line)
                var end = line
                while (end < text.lineCount && text.getLineBottom(end) - from <= bottom - y) end++
                if (end == line) {
                    if (y > top) {
                        newPage()
                        continue
                    }
                    end = line + 1
                }
                val to = text.getLineBottom(end - 1)
                canvas?.let { target ->
                    if (line == 0 && marker != null) {
                        target.drawText(marker, x - MARKER_GAP_PT, y + text.getLineBaseline(0), paint)
                    }
                    target.save()
                    target.translate(x, y - from)
                    target.clipRect(0f, from.toFloat(), width + 1f, to.toFloat())
                    text.draw(target)
                    target.restore()
                }
                y += to - from
                line = end
                if (line < text.lineCount) newPage()
            }
        }

        private fun picture(block: DocBlock.Image) {
            val bitmap = images?.load(block.path, if (pdf == null) MEASURE_SIDE else PICTURE_SIDE)
            if (bitmap == null || bitmap.width <= 0) {
                lost = true
                return
            }
            var shown = (block.widthPt.takeIf { it > 0f } ?: (bitmap.width * PIXEL_TO_POINT)).coerceAtMost(width)
            var tall = shown * bitmap.height / bitmap.width
            val room = bottom - top
            if (tall > room) {
                shown *= room / tall
                tall = room
            }
            if (y + tall > bottom) newPage()
            canvas?.drawBitmap(bitmap, null, RectF(left, y, left + shown, y + tall), PICTURE_PAINT)
            bitmap.recycle()
            y += tall + BLOCK_GAP_PT
        }

        private fun table(block: DocBlock.Table) {
            block.rows.forEach { row -> tableRow(row, block.grid) }
            y += BLOCK_GAP_PT
        }

        private fun columnWidths(row: List<DocCell>, grid: List<Float>): List<Float> {
            val columns = row.sumOf { it.span }
            val trusted = grid.size >= columns && grid.all { it > 0f }
            if (!trusted) return row.map { width * it.span / columns }
            val gridTotal = grid.sum()
            // Chhoti table chhoti hi rahe, par page se bahar nahi jaye.
            val scale = minOf(1f, width / gridTotal)
            var column = 0
            return row.map { cell ->
                val share = grid.subList(column, column + cell.span).sum()
                column += cell.span
                share * scale
            }
        }

        private fun tableRow(row: List<DocCell>, grid: List<Float>) {
            if (row.isEmpty()) return
            val widths = columnWidths(row, grid)
            val texts = row.mapIndexed { index, cell ->
                layout(cell.rich.toSpanned(), widths[index] - 2 * CELL_PADDING_PT, TextAlign.Start)
            }
            val rowHeight = texts.maxOf { it.height } + 2 * CELL_PADDING_PT
            if (rowHeight > bottom - top) {
                // Page se bada cell ek hi jagah nahi aa sakta. Text bachana table ke aakar se zaroori hai.
                texts.forEach { flow(it, left + CELL_PADDING_PT) }
                return
            }
            if (y + rowHeight > bottom) newPage()
            var x = left
            row.forEachIndexed { index, cell ->
                val cellWidth = widths[index]
                canvas?.let { target -> drawCell(target, cell, texts[index], x, cellWidth, rowHeight) }
                x += cellWidth
            }
            y += rowHeight
        }

        private fun drawCell(
            target: Canvas,
            cell: DocCell,
            text: StaticLayout,
            x: Float,
            cellWidth: Float,
            tall: Float,
        ) {
            cell.fill?.let(::parseColor)?.let { fill ->
                FILL_PAINT.color = fill.toArgb()
                target.drawRect(x, y, x + cellWidth, y + tall, FILL_PAINT)
            }
            if (!cell.mergedUp) target.drawLine(x, y, x + cellWidth, y, BORDER_PAINT)
            target.drawLine(x, y + tall, x + cellWidth, y + tall, BORDER_PAINT)
            target.drawLine(x, y, x, y + tall, BORDER_PAINT)
            target.drawLine(x + cellWidth, y, x + cellWidth, y + tall, BORDER_PAINT)
            target.save()
            target.translate(x + CELL_PADDING_PT, y + CELL_PADDING_PT)
            text.draw(target)
            target.restore()
        }
    }

    private fun layout(text: CharSequence, width: Float, align: TextAlign): StaticLayout {
        val alignment = when (align) {
            TextAlign.Center -> Layout.Alignment.ALIGN_CENTER
            TextAlign.End, TextAlign.Right -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(alignment)
            .setIncludePad(false)
            .setLineSpacing(0f, LINE_SPACING)
            .setUseLineSpacingFromFallbacks(true)
        if (align == TextAlign.Justify) builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
        return builder.build()
    }

    /** Purani `.doc` aur text file me size nahi hota, tab heading ka size yahin se aata hai. */
    private fun styled(block: DocBlock.Paragraph): SpannableString {
        val spanned = block.rich.toSpanned()
        val hasSize = block.rich.spanStyles.any { it.item.fontSize.isSp }
        if (block.heading > 0 && !hasSize && spanned.isNotEmpty()) {
            val scale = HEADING_SCALE[(block.heading - 1).coerceIn(HEADING_SCALE.indices)]
            spanned.setSpan(RelativeSizeSpan(scale), 0, spanned.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            spanned.setSpan(StyleSpan(Typeface.BOLD), 0, spanned.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return spanned
    }

    private fun SpannableString.withPageNumbers(number: String, pages: String): CharSequence {
        val text = SpannableStringBuilder(this)
        for (index in text.length - 1 downTo 0) {
            when (text[index]) {
                PAGE_NUMBER_MARK -> text.replace(index, index + 1, number)
                PAGE_TOTAL_MARK -> text.replace(index, index + 1, pages)
            }
        }
        return text
    }

    private fun AnnotatedString.toSpanned(): SpannableString {
        val out = SpannableString(text)
        spanStyles.forEach { range ->
            val style = range.item
            val start = range.start.coerceIn(0, text.length)
            val end = range.end.coerceIn(start, text.length)
            if (end > start) {
                val spans = buildList {
                    if (style.fontSize.isSp) add(RelativeSizeSpan(style.fontSize.value / BODY_SIZE_PT))
                    if (style.color.isSpecified) add(ForegroundColorSpan(style.color.toArgb()))
                    if (style.fontWeight == FontWeight.Bold) add(StyleSpan(Typeface.BOLD))
                    if (style.fontStyle == FontStyle.Italic) add(StyleSpan(Typeface.ITALIC))
                    if (style.textDecoration?.contains(TextDecoration.Underline) == true) add(UnderlineSpan())
                }
                spans.forEach { out.setSpan(it, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            }
        }
        return out
    }

    private companion object {
        val PICTURE_PAINT = Paint(Paint.FILTER_BITMAP_FLAG)
        val FILL_PAINT = Paint().apply { style = Paint.Style.FILL }
        val BORDER_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = BORDER_PT
            color = BORDER_COLOR
        }
    }
}
