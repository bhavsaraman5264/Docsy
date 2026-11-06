package app.kholo.reader.docx

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.text.AnnotatedString

private const val BODY_SIZE_PT = 11f
private const val LINE_RATIO = 1.25f
private const val CELL_PADDING_PT = 8f
private const val IMAGE_RATIO = 0.75f

sealed interface PageItem {
    data class Paragraph(val block: DocBlock.Paragraph) : PageItem

    data class TableRow(val cells: List<DocCell>, val grid: List<Float>) : PageItem

    data class Picture(val block: DocBlock.Image) : PageItem
}

/**
 * Pages ka hisaab background me hota hai, taaki sirf dikhne wale pages hi bane. Yeh andaza hai,
 * isliye page thoda lamba bhi ho sakta hai, par text kabhi katta nahi.
 */
object Paginator {
    fun paginate(document: ParsedDocument): List<List<PageItem>> {
        val page = document.page
        val width = page.width - page.marginLeft - page.marginRight
        val height = page.height - page.marginTop - page.marginBottom
        val pages = mutableListOf<List<PageItem>>()
        var current = mutableListOf<PageItem>()
        var used = 0f
        val flush = {
            if (current.isNotEmpty()) {
                pages += current
                current = mutableListOf()
                used = 0f
            }
        }
        val place = { item: PageItem, itemHeight: Float ->
            if (used + itemHeight > height) flush()
            current += item
            used += itemHeight
        }
        document.blocks.forEach { block ->
            when (block) {
                is DocBlock.Paragraph -> place(PageItem.Paragraph(block), paragraphHeight(block, width))
                is DocBlock.Table -> block.rows.forEach { row ->
                    place(PageItem.TableRow(row, block.grid), rowHeight(row, block.grid, width))
                }
                is DocBlock.Image -> {
                    val shown = block.widthPt.takeIf { it > 0f }?.coerceAtMost(width) ?: width
                    place(PageItem.Picture(block), shown * IMAGE_RATIO)
                }
                DocBlock.PageBreak -> flush()
                is DocBlock.SlideHeader, DocBlock.Skipped -> Unit
            }
        }
        flush()
        return pages.ifEmpty { listOf(emptyList()) }
    }

    private fun paragraphHeight(block: DocBlock.Paragraph, width: Float): Float {
        val available = (width - block.leftIndent).coerceAtLeast(1f)
        return textHeight(block.rich, available) + block.spaceBefore + block.spaceAfter
    }

    private fun rowHeight(row: List<DocCell>, grid: List<Float>, width: Float): Float {
        val total = grid.sum()
        val trusted = grid.size == row.size && grid.all { it > 0f }
        return row.mapIndexed { index, cell ->
            val share = if (trusted) grid[index] / total else 1f / row.size
            textHeight(cell.rich, (width * share - CELL_PADDING_PT).coerceAtLeast(1f)) + CELL_PADDING_PT
        }.maxOrNull() ?: 0f
    }

    private fun textHeight(text: AnnotatedString, width: Float): Float {
        val size = text.spanStyles
            .map { it.item.fontSize }
            .filter { it.isSp }
            .maxOfOrNull { it.value } ?: BODY_SIZE_PT
        val paint = TextPaint().apply { textSize = size }
        val layout = StaticLayout.Builder.obtain(text.text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
        return layout.lineCount * size * LINE_RATIO
    }
}
