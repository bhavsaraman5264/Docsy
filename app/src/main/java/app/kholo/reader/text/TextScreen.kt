package app.kholo.reader.text

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kholo.reader.R
import app.kholo.reader.core.AppContainer
import app.kholo.reader.core.LoadState
import app.kholo.reader.docx.FontSizeActions
import app.kholo.reader.ui.theme.Spacing
import app.kholo.reader.viewer.DocumentScaffold
import app.kholo.reader.viewer.ReaderActions

private val READING_WIDTH = 680.dp
private const val DEFAULT_FONT_SP = 16

@Composable
fun TextScreen(uri: Uri, name: String, container: AppContainer, actions: ReaderActions) {
    val viewModel: TextViewModel = viewModel(
        factory = viewModelFactory { initializer { TextViewModel(uri, container) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var fontSize by rememberSaveable { mutableIntStateOf(DEFAULT_FONT_SP) }
    DocumentScaffold(name, actions, barActions = { FontSizeActions(fontSize) { fontSize = it } }) { modifier ->
        when (val current = state) {
            LoadState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is LoadState.Failed -> actions.failed(current.error)
            is LoadState.Ready -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = READING_WIDTH).fillMaxWidth(),
                    contentPadding = PaddingValues(Spacing.screenPadding),
                ) {
                    items(current.value.chunks) { chunk ->
                        SelectionContainer {
                            Text(chunk, fontSize = fontSize.sp, lineHeight = (fontSize * 1.5).sp)
                        }
                    }
                    if (current.value.truncated) {
                        item {
                            Text(
                                stringResource(R.string.text_truncated),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
