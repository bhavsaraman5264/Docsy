package app.kholo.reader.text

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kholo.reader.core.AppContainer
import app.kholo.reader.core.LoadState
import app.kholo.reader.table.FastScrollbar
import app.kholo.reader.ui.components.FindAction
import app.kholo.reader.ui.components.FindRange
import app.kholo.reader.ui.components.highlighted
import app.kholo.reader.ui.components.pinchFontSize
import app.kholo.reader.ui.theme.Spacing
import app.kholo.reader.viewer.DocumentScaffold
import app.kholo.reader.viewer.ReaderActions
import app.kholo.reader.viewer.findBarOf
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private val READING_WIDTH = 680.dp
private val PLACEHOLDER_HEIGHT = 2400.dp
private const val DEFAULT_FONT_SP = 16
private const val LINE_RATIO = 1.5
private const val LAYOUT_WAIT_MS = 1_000L

@Composable
fun TextScreen(uri: Uri, name: String, container: AppContainer, actions: ReaderActions) {
    val viewModel: TextViewModel = viewModel(
        factory = viewModelFactory { initializer { TextViewModel(uri, container) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var fontSize by rememberSaveable { mutableIntStateOf(DEFAULT_FONT_SP) }
    val findState by viewModel.find.state.collectAsStateWithLifecycle()
    val hit = findState.currentHit
    val hitsByChunk = remember(findState.hits) { findState.hits.groupBy { it.chunk } }
    DocumentScaffold(
        title = name,
        actions = actions,
        barActions = {
            if (state is LoadState.Ready) FindAction(viewModel.find::open)
        },
        findBar = findBarOf(viewModel.find, findState),
    ) { modifier ->
        when (val current = state) {
            LoadState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is LoadState.Failed -> actions.failed(current.error)
            is LoadState.Ready -> Box(
                modifier.fillMaxSize().pinchFontSize({ fontSize }) { fontSize = it },
                contentAlignment = Alignment.TopCenter,
            ) {
                val list = rememberLazyListState()
                val layouts = remember { mutableStateMapOf<Int, TextLayoutResult>() }
                LaunchedEffect(hit) { if (hit != null) showHit(hit, list, layouts) }
                LazyColumn(
                    state = list,
                    modifier = Modifier.widthIn(max = READING_WIDTH).fillMaxWidth(),
                    contentPadding = PaddingValues(Spacing.screenPadding),
                ) {
                    items(current.value.chunkCount) { index ->
                        val stamp = viewModel.stamp(index)
                        val text = remember(index, stamp) { viewModel.chunk(index) }
                        if (text == null) {
                            Box(Modifier.fillMaxWidth().height(PLACEHOLDER_HEIGHT))
                        } else {
                            DisposableEffect(index) { onDispose { layouts.remove(index) } }
                            val ranges = hitsByChunk[index].orEmpty().map { FindRange(it.start, it.end, it == hit) }
                            SelectionContainer {
                                Text(
                                    text = AnnotatedString(text).highlighted(ranges),
                                    fontSize = fontSize.sp,
                                    lineHeight = (fontSize * LINE_RATIO).sp,
                                    onTextLayout = { layouts[index] = it },
                                )
                            }
                        }
                    }
                }
                FastScrollbar(list, current.value.chunkCount, Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

/** Ek tukda screen se lamba ho sakta hai, isliye tukde tak jaakar milan wali line tak bhi jao. */
private suspend fun showHit(hit: TextHit, list: LazyListState, layouts: Map<Int, TextLayoutResult>) {
    list.scrollToItem(hit.chunk)
    val layout = withTimeoutOrNull(LAYOUT_WAIT_MS) {
        snapshotFlow { layouts[hit.chunk] }.filterNotNull().first()
    } ?: return
    val offset = hit.start.coerceAtMost(layout.layoutInput.text.length)
    list.scrollToItem(hit.chunk, layout.getLineTop(layout.getLineForOffset(offset)).toInt())
}
