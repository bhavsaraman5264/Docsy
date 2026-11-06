package app.kholo.reader.tools

import android.graphics.Bitmap
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kholo.reader.R
import app.kholo.reader.ui.adaptive.rememberWindowInfo
import app.kholo.reader.ui.components.AppTopBar
import app.kholo.reader.ui.components.ErrorState
import app.kholo.reader.ui.theme.Spacing

private val THUMB_SIZE = 56.dp

class ToolActions(
    val onBack: () -> Unit,
    val onPick: () -> Unit,
    val onSave: (ToolResult) -> Unit,
    val onShare: (ToolResult) -> Unit,
    val onOpen: (ToolResult) -> Unit,
)

private val OPTIONS_HEIGHT = 360.dp
private val SHORT_OPTIONS_HEIGHT = 160.dp

@Composable
fun ToolScreen(viewModel: ToolsViewModel, actions: ToolActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val kind = viewModel.kind
    Scaffold(topBar = { AppTopBar(stringResource(kind.title), onBack = actions.onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val result = state.result
            val error = state.error
            when {
                state.busy -> Busy()
                result != null -> ResultView(result, actions)
                error != null -> ErrorState(error) {
                    Button(onClick = viewModel::clearError, shape = MaterialTheme.shapes.small) {
                        Text(stringResource(R.string.action_back))
                    }
                }
                else -> ToolForm(viewModel, state, actions)
            }
        }
    }
}

@Composable
private fun Busy() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.tool_working), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ResultView(result: ToolResult, actions: ToolActions) {
    val context = LocalContext.current
    val size = Formatter.formatShortFileSize(context, result.file.length())
    Column(
        Modifier.fillMaxSize().padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(stringResource(R.string.tool_done), style = MaterialTheme.typography.titleMedium)
        Text(result.name, style = MaterialTheme.typography.bodyLarge)
        val detail = if (result.before > 0L) {
            stringResource(R.string.tool_size_change, Formatter.formatShortFileSize(context, result.before), size)
        } else {
            size
        }
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        result.note?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
        Button(
            onClick = { actions.onSave(result) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
        ) { Text(stringResource(R.string.action_save)) }
        OutlinedButton(
            onClick = { actions.onShare(result) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
        ) { Text(stringResource(R.string.action_share)) }
        OutlinedButton(
            onClick = { actions.onOpen(result) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
        ) { Text(stringResource(R.string.action_open)) }
    }
}

@Composable
private fun ToolForm(viewModel: ToolsViewModel, state: ToolUiState, actions: ToolActions) {
    val kind = viewModel.kind
    val optionsHeight = if (rememberWindowInfo().isShortHeight) SHORT_OPTIONS_HEIGHT else OPTIONS_HEIGHT
    Column(Modifier.fillMaxSize().imePadding()) {
        Text(
            text = stringResource(kind.hint),
            modifier = Modifier.padding(Spacing.screenPadding),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (kind == ToolKind.Organize && state.pageOrder.isNotEmpty()) {
            PageList(viewModel, state, Modifier.weight(1f))
        } else {
            InputList(viewModel, state, Modifier.weight(1f))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Chhoti screen ya keyboard par bhi file list dikhti rahe, options apne andar scroll hote hain.
        Column(
            Modifier
                .heightIn(max = optionsHeight)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            if (state.inputs.isNotEmpty()) ToolOptions(viewModel, state)
            val addLabel = when {
                kind == ToolKind.Scan -> R.string.tool_add_page
                kind.multipleInputs -> R.string.tool_add_more
                else -> R.string.tool_pick_file
            }
            OutlinedButton(
                onClick = actions.onPick,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            ) { Text(stringResource(addLabel)) }
        }
    }
}

@Composable
private fun InputList(viewModel: ToolsViewModel, state: ToolUiState, modifier: Modifier) {
    LazyColumn(modifier.fillMaxWidth()) {
        itemsIndexed(state.inputs) { index, input ->
            Row(
                Modifier.fillMaxWidth().padding(start = Spacing.screenPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = input.name,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (viewModel.kind.multipleInputs) {
                    MoveButtons(
                        onUp = { viewModel.moveInput(index, -1) },
                        onDown = { viewModel.moveInput(index, 1) },
                        onRemove = { viewModel.removeInput(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MoveButtons(onUp: () -> Unit, onDown: () -> Unit, onRemove: () -> Unit) {
    IconButton(onClick = onUp) {
        Icon(painterResource(R.drawable.ic_keyboard_arrow_up), stringResource(R.string.action_move_up))
    }
    IconButton(onClick = onDown) {
        Icon(painterResource(R.drawable.ic_keyboard_arrow_down), stringResource(R.string.action_move_down))
    }
    IconButton(onClick = onRemove) {
        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_remove))
    }
}

@Composable
private fun PageList(viewModel: ToolsViewModel, state: ToolUiState, modifier: Modifier) {
    LazyColumn(modifier.fillMaxWidth()) {
        itemsIndexed(state.pageOrder, key = { _, page -> page }) { index, page ->
            Row(
                Modifier.fillMaxWidth().padding(start = Spacing.screenPadding, top = Spacing.xs, bottom = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PageThumb(viewModel, page)
                Text(stringResource(R.string.tool_page, page + 1), modifier = Modifier.weight(1f))
                MoveButtons(
                    onUp = { viewModel.movePage(index, -1) },
                    onDown = { viewModel.movePage(index, 1) },
                    onRemove = { viewModel.removePage(index) },
                )
            }
        }
    }
}

@Composable
private fun PageThumb(viewModel: ToolsViewModel, page: Int) {
    val bitmap by produceState<Bitmap?>(null, page) { value = viewModel.pageBitmap(page) }
    Box(Modifier.size(THUMB_SIZE)) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
private fun ToolOptions(viewModel: ToolsViewModel, state: ToolUiState) {
    when (viewModel.kind) {
        ToolKind.Photos, ToolKind.Scan -> RunButton(R.string.tool_make_pdf, viewModel::runPhotos)
        ToolKind.Merge -> if (state.inputs.size >= 2) RunButton(R.string.tool_merge, viewModel::runMerge)
        ToolKind.Split -> SplitOptions(viewModel, state.pageCount)
        ToolKind.Organize -> if (state.pageOrder.isNotEmpty()) RunButton(R.string.action_save, viewModel::runOrganize)
        ToolKind.Compress -> CompressOptions(viewModel)
        ToolKind.Lock -> PasswordOption(R.string.tool_lock, viewModel::runLock)
        ToolKind.Unlock -> PasswordOption(R.string.tool_unlock, viewModel::runUnlock)
        ToolKind.Text -> RunButton(R.string.tool_text, viewModel::runText)
    }
}

@Composable
private fun RunButton(label: Int, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
        Text(stringResource(label))
    }
}

@Composable
private fun SplitOptions(viewModel: ToolsViewModel, pageCount: Int) {
    var from by remember { mutableStateOf("1") }
    var to by remember(pageCount) { mutableStateOf(pageCount.toString()) }
    val start = from.toIntOrNull()
    val end = to.toIntOrNull()
    val valid = start != null && end != null && start in 1..end && end <= pageCount
    Text(stringResource(R.string.tool_total_pages, pageCount), style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        NumberField(R.string.tool_from_page, from, Modifier.weight(1f)) { from = it }
        NumberField(R.string.tool_to_page, to, Modifier.weight(1f)) { to = it }
    }
    Button(
        onClick = { if (start != null && end != null) viewModel.runSplit(start, end) },
        enabled = valid,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
    ) { Text(stringResource(R.string.tool_split)) }
}

@Composable
private fun NumberField(label: Int, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit)) },
        modifier = modifier,
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun CompressOptions(viewModel: ToolsViewModel) {
    var target by remember { mutableStateOf(CompressTarget.Kb200) }
    Column(Modifier.selectableGroup()) {
        CompressTarget.entries.forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(option == target, role = Role.RadioButton, onClick = { target = option })
                    .padding(vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == target, onClick = null)
                Text(stringResource(option.label))
            }
        }
    }
    RunButton(R.string.tool_compress) { viewModel.runCompress(target) }
}

@Composable
private fun PasswordOption(label: Int, onRun: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.password_label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
    Button(
        onClick = { onRun(password) },
        enabled = password.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
    ) { Text(stringResource(label)) }
}
