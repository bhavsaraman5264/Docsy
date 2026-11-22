package app.kholo.reader.table

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kholo.reader.R
import app.kholo.reader.core.AppContainer
import app.kholo.reader.core.LoadState
import app.kholo.reader.office.OfficeContent
import app.kholo.reader.open.DocumentType
import app.kholo.reader.ui.components.FindAction
import app.kholo.reader.ui.components.FindCurrentColor
import app.kholo.reader.ui.components.FindHitColor
import app.kholo.reader.ui.components.ListScroller
import app.kholo.reader.ui.components.RowJump
import app.kholo.reader.ui.theme.Spacing
import app.kholo.reader.viewer.DocumentScaffold
import app.kholo.reader.viewer.FindState
import app.kholo.reader.viewer.ReaderActions
import app.kholo.reader.viewer.findBarOf
import kotlinx.coroutines.delay

private val ROW_HEIGHT = 40.dp
private val COLUMN_WIDTH = 132.dp
private val NUMBER_WIDTH = 56.dp
private const val MAX_CELL_CHARS = 256
private const val REFRESH_MS = 700L
private const val GRID_ROW = 0

@Composable
fun GridScreen(
    uri: Uri,
    type: DocumentType,
    name: String,
    size: Long?,
    container: AppContainer,
    actions: ReaderActions,
    content: OfficeContent? = null,
) {
    val viewModel: GridViewModel = viewModel(
        factory = viewModelFactory { initializer { GridViewModel(uri, type, size, container, content) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val findState by viewModel.find.state.collectAsStateWithLifecycle()
    DocumentScaffold(
        title = name,
        actions = actions,
        barActions = { if (state is LoadState.Ready) FindAction(viewModel.find::open) },
        findBar = findBarOf(viewModel.find, findState),
    ) { modifier ->
        when (val current = state) {
            LoadState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is LoadState.Failed -> actions.failed(current.error)
            is LoadState.Ready -> Table(current.value, viewModel, findState, modifier)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Table(source: TableSource, viewModel: GridViewModel, find: FindState<GridHit>, modifier: Modifier) {
    val info by source.info.collectAsStateWithLifecycle()
    var sheet by remember { mutableIntStateOf(0) }
    var opened by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(info.finished, sheet) {
        while (!info.finished) {
            delay(REFRESH_MS)
            viewModel.refresh()
        }
        viewModel.refresh()
    }
    Column(modifier.fillMaxSize()) {
        Text(
            text = rowLabel(info),
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val list = rememberLazyListState()
        val horizontal = rememberScrollState()
        val columnPx = with(LocalDensity.current) { COLUMN_WIDTH.toPx() }
        val hit = find.currentHit
        LaunchedEffect(hit) {
            if (hit != null) {
                list.scrollToItem(hit.row)
                horizontal.scrollTo((hit.column * columnPx).toInt())
            }
        }
        val marks = remember(find.hits, find.current) {
            find.hits.groupBy { it.row }.mapValues { (_, hits) -> hits.associate { it.column to (it == hit) } }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            GridBody(info, list, horizontal, viewModel, marks) { opened = it }
            ListScroller(list, info.rowCount, jump = RowJump) {
                stringResource(R.string.page_indicator, it + 1, info.rowCount)
            }
        }
        if (source.sheets.size > 1) {
            PrimaryScrollableTabRow(selectedTabIndex = sheet, modifier = Modifier.navigationBarsPadding()) {
                source.sheets.forEachIndexed { index, title ->
                    Tab(
                        selected = index == sheet,
                        onClick = {
                            sheet = index
                            viewModel.selectSheet(index)
                        },
                        text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
    }
    opened?.let { text ->
        ModalBottomSheet(onDismissRequest = { opened = null }) {
            SelectionContainer {
                Text(
                    text = text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.screenPadding)
                        .navigationBarsPadding(),
                )
            }
        }
    }
}

@Composable
private fun rowLabel(info: TableInfo): String = when {
    info.failed -> stringResource(R.string.table_partial)
    info.finished && info.rowCount == 0 -> stringResource(R.string.table_empty)
    info.finished -> pluralStringResource(R.plurals.table_rows, info.rowCount, info.rowCount)
    else -> stringResource(R.string.table_loading, info.rowCount)
}

@Composable
private fun GridBody(
    info: TableInfo,
    list: LazyListState,
    horizontal: ScrollState,
    viewModel: GridViewModel,
    marks: Map<Int, Map<Int, Boolean>>,
    onCell: (String) -> Unit,
) {
    val measurer = rememberTextMeasurer(cacheSize = 512)
    val density = LocalDensity.current
    // Cell ka text phone ki font setting ke saath badhta hai, isliye row bhi.
    val rowHeight = ROW_HEIGHT * density.fontScale.coerceAtLeast(1f)
    val columnPx = with(density) { COLUMN_WIDTH.toPx() }
    val numberPx = with(density) { NUMBER_WIDTH.toPx() }
    val total = NUMBER_WIDTH + COLUMN_WIDTH * info.columnCount.coerceAtLeast(1)
    val scheme = MaterialTheme.colorScheme
    // Import ke dauran info baar-baar badalta hai. Naye object se har dikhti row dobara draw hoti.
    val colors = remember(scheme) {
        GridColors(
            text = scheme.onSurface,
            muted = scheme.onSurfaceVariant,
            line = scheme.outlineVariant,
            frozen = scheme.surface,
            header = scheme.primaryContainer,
        )
    }
    val metrics = remember(columnPx, numberPx) { GridMetrics(columnPx, numberPx) }
    Box(Modifier.fillMaxSize().horizontalScroll(horizontal)) {
        Column(Modifier.width(total).fillMaxHeight()) {
            Canvas(Modifier.fillMaxWidth().height(rowHeight).background(colors.header)) {
                drawRow(info.header, "", horizontal.value, metrics, measurer, colors, bold = true)
            }
            LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                items(info.rowCount, contentType = { GRID_ROW }) { index ->
                    val stamp = viewModel.stamp(index)
                    val row = remember(index, stamp) { viewModel.row(index) }
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .pointerInput(row) {
                                detectTapGestures { tap ->
                                    val column = ((tap.x - numberPx) / columnPx).toInt()
                                    row?.cells?.getOrNull(column)?.takeIf { it.isNotEmpty() }?.let(onCell)
                                }
                            },
                    ) {
                        drawRow(
                            cells = row?.cells.orEmpty(),
                            number = row?.number?.toString().orEmpty(),
                            scroll = horizontal.value,
                            metrics = metrics,
                            measurer = measurer,
                            colors = colors,
                            bold = false,
                            marks = marks[index],
                        )
                    }
                }
            }
        }
    }
}

/** Text styles yahin ek baar bante hain. Pehle scroll ke har frame par har row naye style banati thi. */
private class GridColors(text: Color, muted: Color, val line: Color, val frozen: Color, val header: Color) {
    val plain = TextStyle(color = text, fontSize = 14.sp, fontWeight = FontWeight.Normal)
    val bold = plain.copy(fontWeight = FontWeight.SemiBold)
    val plainMarked = plain.copy(color = Color.Black)
    val boldMarked = bold.copy(color = Color.Black)
    val number = plain.copy(color = muted, fontSize = 12.sp)
}

private class GridMetrics(val column: Float, val number: Float)

/** Poori row ek hi Canvas: har cell ka alag composable nahi, aur sirf dikhne wale columns draw hote hain. */
private fun DrawScope.drawRow(
    cells: List<String>,
    number: String,
    scroll: Int,
    metrics: GridMetrics,
    measurer: TextMeasurer,
    colors: GridColors,
    bold: Boolean,
    marks: Map<Int, Boolean>? = null,
) {
    val style = if (bold) colors.bold else colors.plain
    val padding = 8.dp.toPx()
    val first = ((scroll - metrics.number) / metrics.column).toInt().coerceAtLeast(0)
    val last = ((scroll + size.width) / metrics.column).toInt() + 1
    val cellConstraints = Constraints(maxWidth = (metrics.column - padding * 2).toInt())
    for (i in first..minOf(last, cells.lastIndex)) {
        val text = cells[i].take(MAX_CELL_CHARS).replace('\n', ' ')
        if (text.isEmpty()) continue
        val mark = marks?.get(i)
        if (mark != null) {
            drawRect(
                color = if (mark) FindCurrentColor else FindHitColor,
                topLeft = Offset(metrics.number + i * metrics.column, 0f),
                size = Size(metrics.column, size.height),
            )
        }
        val layout = measurer.measure(
            text = text,
            style = when {
                mark == null -> style
                bold -> colors.boldMarked
                else -> colors.plainMarked
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            constraints = cellConstraints,
        )
        val x = metrics.number + i * metrics.column + padding
        drawText(layout, topLeft = Offset(x, (size.height - layout.size.height) / 2))
    }
    drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
    // Row number chipka rehta hai, isliye uski jagah scroll ke saath chalti hai.
    val left = scroll.toFloat()
    drawRect(colors.frozen, Offset(left, 0f), Size(metrics.number, size.height))
    drawLine(
        colors.line,
        Offset(left + metrics.number, 0f),
        Offset(left + metrics.number, size.height),
        strokeWidth = 1f,
    )
    if (number.isNotEmpty()) {
        val layout = measurer.measure(number, colors.number, maxLines = 1)
        drawText(layout, topLeft = Offset(left + padding, (size.height - layout.size.height) / 2))
    }
}
