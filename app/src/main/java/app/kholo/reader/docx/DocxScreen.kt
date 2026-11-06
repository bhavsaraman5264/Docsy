package app.kholo.reader.docx

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
import app.kholo.reader.ui.components.BarTextButton
import app.kholo.reader.ui.components.FindAction
import app.kholo.reader.ui.components.highlighted
import app.kholo.reader.ui.components.pinchFontSize
import app.kholo.reader.ui.theme.Spacing
import app.kholo.reader.viewer.DocumentScaffold
import app.kholo.reader.viewer.ReaderActions
import app.kholo.reader.viewer.findBarOf
import kotlinx.coroutines.launch

private val READING_WIDTH = 680.dp
private val CELL_WIDTH = 160.dp
private const val DEFAULT_FONT_SP = 16

private const val LINE_HEIGHT_RATIO = 1.5

@Composable
fun DocxScreen(
    uri: Uri,
    type: DocumentType,
    name: String,
    size: Long?,
    container: AppContainer,
    actions: ReaderActions,
    content: OfficeContent? = null,
) {
    val viewModel: DocxViewModel = viewModel(
        factory = viewModelFactory { initializer { DocxViewModel(uri, type, size, container, content) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val findState by viewModel.find.state.collectAsStateWithLifecycle()
    val marks = remember(findState.hits, findState.current) { DocMarks(findState.hits, findState.currentHit) }
    // Badi file tukdon me aati hai, naya hissa aane par usme bhi dhoondho.
    LaunchedEffect(state) { viewModel.find.rescan() }
    var fontSize by rememberSaveable { mutableIntStateOf(DEFAULT_FONT_SP) }
    val pageMode by container.settings.wordPageView.collectAsStateWithLifecycle(initialValue = true)
    val scope = rememberCoroutineScope()
    val showPage = type == DocumentType.Docx && pageMode
    DocumentScaffold(
        title = name,
        actions = actions,
        barActions = {
            if (state is LoadState.Ready) FindAction(viewModel.find::open)
            if (type == DocumentType.Docx) {
                BarTextButton(onClick = { scope.launch { container.settings.setWordPageView(!pageMode) } }) {
                    Text(stringResource(if (pageMode) R.string.view_read else R.string.view_page))
                }
            }
        },
        findBar = findBarOf(viewModel.find, findState),
    ) { modifier ->
        CompositionLocalProvider(LocalDocMarks provides marks) {
            when (val current = state) {
                LoadState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is LoadState.Failed -> actions.failed(current.error)
                is LoadState.Ready -> if (showPage) {
                    DocxPageView(current.value, viewModel, modifier, findState.currentHit)
                } else {
                    val pinch = modifier.pinchFontSize({ fontSize }) { fontSize = it }
                    ReadingList(current.value, type, fontSize, viewModel, pinch, findState.currentHit)
                }
            }
        }
    }
}

@Composable
private fun ReadingList(
    document: ParsedDocument,
    type: DocumentType,
    fontSize: Int,
    viewModel: DocxViewModel,
    modifier: Modifier,
    hit: DocHit?,
) {
    val list = rememberLazyListState()
    LaunchedEffect(hit) {
        // Pehla item upar wala note hai.
        if (hit != null) list.scrollToItem(hit.block + 1)
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = list,
            modifier = Modifier.widthIn(max = READING_WIDTH).fillMaxWidth(),
            contentPadding = PaddingValues(Spacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item { ReadingViewNote(type) }
            items(document.blocks) { block -> Block(block, fontSize, viewModel) }
        }
    }
}

@Composable
private fun ReadingViewNote(type: DocumentType) {
    val slides = type == DocumentType.Pptx || type == DocumentType.Ppt
    val note = if (slides) R.string.note_slides else R.string.note_reading_view
    Text(
        stringResource(note),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Block(block: DocBlock, fontSize: Int, viewModel: DocxViewModel) {
    when (block) {
        is DocBlock.Paragraph -> ParagraphBlock(block, fontSize)
        is DocBlock.Table -> TableBlock(block, fontSize)
        is DocBlock.Image -> ImageBlock(block.path, viewModel)
        is DocBlock.SlideHeader -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            HorizontalDivider()
            Text(
                stringResource(R.string.slide_number, block.number),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        DocBlock.PageBreak -> Unit
        DocBlock.Skipped -> Text(
            stringResource(R.string.note_skipped),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ParagraphBlock(block: DocBlock.Paragraph, fontSize: Int) {
    val size = fontSize + when (block.heading) {
        1 -> 10
        2 -> 6
        3 -> 3
        0 -> 0
        else -> 1
    }
    val weight = if (block.heading > 0) FontWeight.Bold else null
    val line = (size * LINE_HEIGHT_RATIO).sp
    SelectionContainer {
        Row(Modifier.padding(start = (block.indent * 16).dp)) {
            block.marker?.let {
                Text("$it  ", fontSize = size.sp, lineHeight = line)
            }
            Text(
                block.text.highlighted(LocalDocMarks.current.ranges(block)),
                fontSize = size.sp,
                lineHeight = line,
                fontWeight = weight,
            )
        }
    }
}

/** Chaudi table apne andar scroll hoti hai, page ko nahi hilati. */
@Composable
private fun TableBlock(block: DocBlock.Table, fontSize: Int) {
    val marks = LocalDocMarks.current
    Surface(
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            block.rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider()
                Row {
                    row.forEach { cell ->
                        SelectionContainer {
                            Text(
                                text = AnnotatedString(cell.text).highlighted(marks.ranges(cell)),
                                modifier = Modifier.width(CELL_WIDTH).padding(Spacing.s),
                                fontSize = (fontSize - 2).sp,
                                fontWeight = if (index == 0) FontWeight.SemiBold else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageBlock(path: String, viewModel: DocxViewModel) {
    val bitmap by produceState<Bitmap?>(null, path) { value = viewModel.image(path) }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
    }
}
