package app.kholo.reader.docx

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kholo.reader.ui.components.highlighted
import app.kholo.reader.ui.theme.LocalReaderBackground
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGE_GAP_PT = 12f
private const val MAX_ZOOM_OVER_FIT = 5f
private const val TWO_FINGERS = 2
private const val LINE_HEIGHT = 1.25f
private const val BODY_SIZE_PT = 11f
private const val CELL_PADDING_PT = 4f
private val BORDER = Color(0xFFBFC4CA)

/** Word jaisa view. Lazy list hai: 10 page ho ya 5000, banta sirf wahi hai jo dikh raha hai. */
@Composable
fun DocxPageView(document: ParsedDocument, viewModel: DocxViewModel, modifier: Modifier, hit: DocHit? = null) {
    val latest by rememberUpdatedState(document)
    // Naya hissa aane par pichhla hisaab cancel nahi hota, warna file poori hone tak screen khaali rehti.
    val pages by produceState<List<List<PageItem>>?>(null) {
        snapshotFlow { latest }.conflate().collect { current ->
            value = withContext(Dispatchers.Default) { Paginator.paginate(current) }
        }
    }
    val page = document.page
    BoxWithConstraints(modifier.fillMaxSize().background(LocalReaderBackground.current)) {
        val density = LocalDensity.current
        val fit = maxWidth.value / (page.width + PAGE_GAP_PT * 2)
        var scale by remember(fit) { mutableFloatStateOf(fit) }
        val list = rememberLazyListState()
        val horizontal = rememberScrollState()
        val scope = rememberCoroutineScope()
        val loaded = pages
        if (loaded == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
            return@BoxWithConstraints
        }
        LaunchedEffect(hit, loaded) {
            val target = hit?.let { found -> loaded.indexOfFirst { items -> items.any { it.owns(found.owner) } } } ?: -1
            if (target >= 0) {
                // Fit par poora page dikhta hai, isliye milan screen se bahar nahi rehta.
                scale = fit
                horizontal.scrollTo(0)
                list.scrollToItem(target)
            }
        }
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
                                val deltaY = (list.firstVisibleItemScrollOffset + focus.y) * (ratio - 1f)
                                scale = next
                                scope.launch {
                                    awaitFrame()
                                    horizontal.scrollTo(targetX)
                                    list.dispatchRawDelta(deltaY)
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .horizontalScroll(horizontal),
        ) {
            // Word ke point size tay hain: phone ki font setting se page ka layout nahi badalna chahiye.
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
                LazyColumn(
                    state = list,
                    modifier = Modifier.width(((page.width + PAGE_GAP_PT * 2) * scale).dp).fillMaxHeight(),
                    contentPadding = PaddingValues(vertical = (PAGE_GAP_PT * scale).dp),
                    verticalArrangement = Arrangement.spacedBy((PAGE_GAP_PT * scale).dp),
                ) {
                    itemsIndexed(loaded) { _, items ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.scaled(scale)) { PageSheet(items, page, viewModel) }
                        }
                    }
                }
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

@Composable
private fun PageSheet(items: List<PageItem>, page: PageSetup, viewModel: DocxViewModel) {
    Column(
        Modifier
            .width(page.width.dp)
            .heightIn(min = page.height.dp)
            .background(Color.White)
            .padding(
                start = page.marginLeft.dp,
                end = page.marginRight.dp,
                top = page.marginTop.dp,
                bottom = page.marginBottom.dp,
            ),
    ) {
        items.forEach { item ->
            when (item) {
                is PageItem.Paragraph -> PageParagraph(item.block)
                is PageItem.TableRow -> PageTableRow(item, page)
                is PageItem.Picture -> PagePicture(item.block, page, viewModel)
            }
        }
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
        Text(
            block.rich.highlighted(LocalDocMarks.current.ranges(block)),
            modifier = Modifier.weight(1f),
            style = PAGE_TEXT.copy(textAlign = block.align),
        )
    }
}

@Composable
private fun PageTableRow(item: PageItem.TableRow, page: PageSetup) {
    val marks = LocalDocMarks.current
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
            ) { Text(cell.rich.highlighted(marks.ranges(cell)), style = PAGE_TEXT) }
        }
    }
}

@Composable
private fun PagePicture(block: DocBlock.Image, page: PageSetup, viewModel: DocxViewModel) {
    val available = page.width - page.marginLeft - page.marginRight
    val width = block.widthPt.takeIf { it > 0f }?.coerceAtMost(available) ?: available
    val bitmap by produceState<Bitmap?>(null, block.path) { value = viewModel.image(block.path) }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.width(width.dp),
            contentScale = ContentScale.FillWidth,
        )
    }
}
