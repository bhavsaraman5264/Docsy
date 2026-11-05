package app.kholo.reader.docx

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kholo.reader.ui.theme.LocalReaderBackground
import kotlin.math.roundToInt
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch

private const val PAGE_GAP_PT = 12f
private const val MAX_ZOOM_OVER_FIT = 5f
private const val TWO_FINGERS = 2
private const val LINE_HEIGHT = 1.25f
private const val BODY_SIZE_PT = 11f
private const val CELL_PADDING_PT = 4f
private val BORDER = Color(0xFFBFC4CA)

/** Word jaisa view: safed page, asli margin aur size. 1 point = 1 layout unit, phir poora page scale hota hai. */
@Composable
fun DocxPageView(document: ParsedDocument, viewModel: DocxViewModel, modifier: Modifier) {
    val page = document.page
    BoxWithConstraints(modifier.fillMaxSize().background(LocalReaderBackground.current)) {
        val density = LocalDensity.current
        val fit = maxWidth.value / (page.width + PAGE_GAP_PT * 2)
        var scale by remember(fit) { mutableFloatStateOf(fit) }
        val vertical = rememberScrollState()
        val horizontal = rememberScrollState()
        val scope = rememberCoroutineScope()
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(fit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= TWO_FINGERS) {
                                val focus = event.calculateCentroid()
                                val next = (scale * event.calculateZoom()).coerceIn(fit, fit * MAX_ZOOM_OVER_FIT)
                                val ratio = next / scale
                                val targetX = ((horizontal.value + focus.x) * ratio - focus.x).roundToInt()
                                val targetY = ((vertical.value + focus.y) * ratio - focus.y).roundToInt()
                                scale = next
                                scope.launch {
                                    awaitFrame()
                                    horizontal.scrollTo(targetX)
                                    vertical.scrollTo(targetY)
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .verticalScroll(vertical)
                .horizontalScroll(horizontal),
        ) {
            // Word ke point size tay hain: phone ki font setting se page ka layout nahi badalna chahiye.
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
                Box(Modifier.scaled(scale)) { Pages(document, viewModel) }
            }
        }
    }
}

/** Layout bhi scale hota hai, taaki scroll sahi chale aur text GPU par sharp rahe. */
private fun Modifier.scaled(scale: Float): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout((placeable.width * scale).roundToInt(), (placeable.height * scale).roundToInt()) {
        placeable.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}

private class PlacedItem(val placeable: Placeable, val y: Int, val page: Int)

@Composable
private fun Pages(document: ParsedDocument, viewModel: DocxViewModel) {
    val page = document.page
    val items = remember(document) { document.blocks.flatMap(::pageItems) }
    SubcomposeLayout { _ ->
        val pageWidth = page.width.dp.roundToPx()
        val pageHeight = page.height.dp.roundToPx()
        val gap = PAGE_GAP_PT.dp.roundToPx()
        val left = page.marginLeft.dp.roundToPx()
        val top = page.marginTop.dp.roundToPx()
        val contentWidth = pageWidth - left - page.marginRight.dp.roundToPx()
        val contentBottom = pageHeight - page.marginBottom.dp.roundToPx()
        val constraints = Constraints(maxWidth = contentWidth.coerceAtLeast(1))
        val placed = mutableListOf<PlacedItem>()
        var current = 0
        var y = top
        var empty = true
        items.forEachIndexed { index, item ->
            if (item == PageItem.Break) {
                if (!empty) {
                    current++
                    y = top
                    empty = true
                }
                return@forEachIndexed
            }
            subcompose(index) { PageItemContent(item, page, viewModel) }.forEach { measurable ->
                val placeable = measurable.measure(constraints)
                if (!empty && y + placeable.height > contentBottom) {
                    current++
                    y = top
                }
                placed += PlacedItem(placeable, y, current)
                y += placeable.height
                empty = false
            }
        }
        val pages = current + 1
        val papers = subcompose("paper") {
            repeat(pages) { Box(Modifier.background(Color.White)) }
        }.map { it.measure(Constraints.fixed(pageWidth, pageHeight)) }
        val width = pageWidth + gap * 2
        val height = (pageHeight + gap) * pages + gap
        layout(width, height) {
            papers.forEachIndexed { index, paper -> paper.place(gap, gap + index * (pageHeight + gap)) }
            placed.forEach { item ->
                item.placeable.place(gap + left, gap + item.page * (pageHeight + gap) + item.y)
            }
        }
    }
}

private sealed interface PageItem {
    data class Paragraph(val block: DocBlock.Paragraph) : PageItem

    data class TableRow(val cells: List<DocCell>, val grid: List<Float>) : PageItem

    data class Picture(val block: DocBlock.Image) : PageItem

    data object Break : PageItem
}

/** Table ki har row alag item hai, taaki lambi table page par sahi tute. */
private fun pageItems(block: DocBlock): List<PageItem> = when (block) {
    is DocBlock.Paragraph -> listOf(PageItem.Paragraph(block))
    is DocBlock.Table -> block.rows.map { PageItem.TableRow(it, block.grid) }
    is DocBlock.Image -> listOf(PageItem.Picture(block))
    DocBlock.PageBreak -> listOf(PageItem.Break)
    is DocBlock.SlideHeader, DocBlock.Skipped -> emptyList()
}

@Composable
private fun PageItemContent(item: PageItem, page: PageSetup, viewModel: DocxViewModel) {
    when (item) {
        is PageItem.Paragraph -> PageParagraph(item.block)
        is PageItem.TableRow -> PageTableRow(item, page)
        is PageItem.Picture -> PagePicture(item.block, page, viewModel)
        PageItem.Break -> Unit
    }
}

private val PAGE_TEXT = TextStyle(
    color = Color.Black,
    fontSize = BODY_SIZE_PT.sp,
    lineHeight = (
        BODY_SIZE_PT *
            LINE_HEIGHT
        ).sp,
)

@Composable
private fun PageParagraph(block: DocBlock.Paragraph) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = block.leftIndent.dp, top = block.spaceBefore.dp, bottom = block.spaceAfter.dp),
    ) {
        block.marker?.let { Text("$it  ", style = PAGE_TEXT) }
        Text(block.rich, modifier = Modifier.weight(1f), style = PAGE_TEXT.copy(textAlign = block.align))
    }
}

@Composable
private fun PageTableRow(item: PageItem.TableRow, page: PageSetup) {
    val available = page.width - page.marginLeft - page.marginRight
    // Merged cells ya kharab grid par column gin nahi milte. Tab barabar baantna hi sahi hai.
    val grid = item.grid.takeIf { it.size == item.cells.size && it.all { width -> width > 0f } }
    val total = grid?.sum() ?: 1f
    Row {
        item.cells.forEachIndexed { index, cell ->
            val share = grid?.get(index)?.div(total) ?: (1f / item.cells.size)
            val fill = cell.fill?.let(::parseColor) ?: Color.Transparent
            Box(
                Modifier
                    .width((available * share).dp)
                    .background(fill)
                    .border(0.5.dp, BORDER)
                    .padding(CELL_PADDING_PT.dp),
            ) { Text(cell.rich, style = PAGE_TEXT) }
        }
    }
}

@Composable
private fun PagePicture(block: DocBlock.Image, page: PageSetup, viewModel: DocxViewModel) {
    val available = page.width - page.marginLeft - page.marginRight
    val width = block.widthPt.takeIf { it > 0f }?.coerceAtMost(available) ?: available
    val bitmap by produceState<android.graphics.Bitmap?>(null, block.path) { value = viewModel.image(block.path) }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.width(width.dp),
            contentScale = ContentScale.FillWidth,
        )
    }
}
