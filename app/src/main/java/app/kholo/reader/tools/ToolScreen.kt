package app.kholo.reader.tools

import android.graphics.Bitmap
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kholo.reader.R
import app.kholo.reader.ui.components.AppTopBar
import app.kholo.reader.ui.components.ErrorState
import app.kholo.reader.ui.theme.Spacing
import app.kholo.reader.ui.theme.ToolTone

private val DONE_ICON = 64.dp
private val PICK_ICON = 32.dp
private val PICK_CIRCLE = 72.dp
private val CARD_LINE = 1.dp
private val PHOTO_THUMB = 40.dp
private val BUSY_WIDTH = 320.dp
private const val DONE_TINT = 0.14f
private const val PERCENT_FULL = 100f

class ToolActions(
    val onBack: () -> Unit,
    val onPick: () -> Unit,
    val onSave: (ToolResult) -> Unit,
    val onShare: (ToolResult) -> Unit,
    val onOpen: (ToolResult) -> Unit,
)

@Composable
fun ToolScreen(viewModel: ToolsViewModel, actions: ToolActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val kind = viewModel.kind
    Scaffold(topBar = { AppTopBar(stringResource(kind.title), onBack = actions.onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val result = state.result
            val error = state.error
            when {
                state.busy -> Busy(kind, state.percent, viewModel::cancelWork)
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
private fun Busy(kind: ToolKind, percent: Int?, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ToolBadge(kind)
        Text(stringResource(R.string.tool_working), style = MaterialTheme.typography.titleMedium)
        val barModifier = Modifier.fillMaxWidth().widthIn(max = BUSY_WIDTH)
        if (percent != null) {
            LinearProgressIndicator(progress = { percent / PERCENT_FULL }, modifier = barModifier)
            Text(stringResource(R.string.tool_percent, percent), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onCancel, shape = MaterialTheme.shapes.small) {
                Text(stringResource(R.string.action_cancel))
            }
        } else {
            LinearProgressIndicator(modifier = barModifier)
        }
    }
}

@Composable
private fun ResultView(result: ToolResult, actions: ToolActions) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DoneMark()
        Text(stringResource(R.string.tool_done), style = MaterialTheme.typography.headlineSmall)
        if (result.files.size > 1 && result.canOpen) {
            PartCards(result, actions.onOpen)
        } else {
            ResultCard(result)
        }
        result.note?.let {
            Text(
                text = stringResource(it),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
                    .padding(Spacing.m),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        WideButton(R.string.action_save) { actions.onSave(result) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            SideButton(R.string.action_share, Modifier.weight(1f)) { actions.onShare(result) }
            if (result.canOpen && result.files.size == 1) {
                SideButton(R.string.action_open, Modifier.weight(1f)) { actions.onOpen(result) }
            }
        }
    }
}

@Composable
private fun DoneMark() {
    val color = ToolTone.Green.color()
    Box(
        Modifier
            .padding(top = Spacing.xl)
            .size(DONE_ICON)
            .background(color.copy(alpha = DONE_TINT), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = color)
    }
}

@Composable
private fun ResultCard(result: ToolResult) {
    val context = LocalContext.current
    val size = Formatter.formatShortFileSize(context, result.totalSize)
    val detail = when {
        result.files.size > 1 -> stringResource(R.string.tool_image_count, result.files.size, size)
        result.before > 0L ->
            stringResource(R.string.tool_size_change, Formatter.formatShortFileSize(context, result.before), size)
        else -> size
    }
    val smaller = sizeSavedPercent(result.before, result.totalSize)?.let { stringResource(R.string.tool_smaller, it) }
    FileCard(
        leading = { TypeChip(result.typeLabel) },
        title = result.name,
        detail = listOfNotNull(detail, smaller).joinToString(" · "),
    )
}

/** Split ke hisse: har file ka naam, size aur use kholne ka button. */
@Composable
private fun PartCards(result: ToolResult, onOpen: (ToolResult) -> Unit) {
    val context = LocalContext.current
    result.files.forEach { file ->
        FileCard(
            leading = { TypeChip(result.typeLabel) },
            title = file.name,
            detail = Formatter.formatShortFileSize(context, file.length()),
            trailing = {
                TextButton(onClick = { onOpen(result.copy(file = file, name = file.name, files = listOf(file))) }) {
                    Text(stringResource(R.string.action_open))
                }
            },
        )
    }
}

@Composable
internal fun FileCard(
    leading: @Composable () -> Unit,
    title: String,
    detail: String,
    trailing: @Composable () -> Unit = {},
) {
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceBright, shape)
            .border(CARD_LINE, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(start = Spacing.m, end = Spacing.xs, top = Spacing.s, bottom = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}

@Composable
private fun ToolForm(viewModel: ToolsViewModel, state: ToolUiState, actions: ToolActions) {
    val kind = viewModel.kind
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolBadge(kind)
            Text(
                text = stringResource(kind.hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            state.inputs.isEmpty() -> PickPrompt(kind, actions.onPick, Modifier.weight(1f))
            kind == ToolKind.Organize && state.pageOrder.isNotEmpty() -> PageGrid(viewModel, state, Modifier.weight(1f))
            else -> InputList(viewModel, state, actions.onPick, Modifier.weight(1f))
        }
        if (state.inputs.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ToolOptions(viewModel, state, actions.onPick)
        }
    }
}

/** Abhi koi file nahi: poori jagah hi button hai, taaki agla kadam dhoondhna na pade. */
@Composable
private fun PickPrompt(kind: ToolKind, onPick: () -> Unit, modifier: Modifier) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.screenPadding, end = Spacing.screenPadding, bottom = Spacing.screenPadding)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceBright)
            .border(CARD_LINE, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(role = Role.Button, onClick = onPick)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.l, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(PICK_CIRCLE).background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (kind == ToolKind.Scan) R.drawable.ic_camera else R.drawable.ic_add),
                contentDescription = null,
                modifier = Modifier.size(PICK_ICON),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = stringResource(if (kind == ToolKind.Scan) R.string.tool_add_page else R.string.tool_pick_file),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

internal fun pickLabel(kind: ToolKind): Int = when {
    kind == ToolKind.Scan -> R.string.tool_add_page
    kind.multipleInputs -> R.string.tool_add_more
    else -> R.string.tool_pick_file
}

@Composable
private fun InputList(viewModel: ToolsViewModel, state: ToolUiState, onPick: () -> Unit, modifier: Modifier) {
    val kind = viewModel.kind
    val context = LocalContext.current
    val pages = if (state.pageCount > 0) stringResource(R.string.tool_total_pages, state.pageCount) else null
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = Spacing.screenPadding, end = Spacing.screenPadding, bottom = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        itemsIndexed(state.inputs) { index, input ->
            val size = if (input.size > 0L) Formatter.formatShortFileSize(context, input.size) else null
            FileCard(
                leading = {
                    if (kind.input == ToolInputType.Image) {
                        PhotoThumb(viewModel, input.uri)
                    } else {
                        TypeChip(kind.input.label)
                    }
                },
                title = input.name,
                detail = listOfNotNull(size, pages).joinToString(" · "),
                trailing = {
                    if (kind.multipleInputs) {
                        MoveButtons(
                            onUp = { viewModel.moveInput(index, -1) },
                            onDown = { viewModel.moveInput(index, 1) },
                            onRemove = { viewModel.removeInput(index) },
                        )
                    } else {
                        TextButton(onClick = onPick) { Text(stringResource(R.string.action_change)) }
                    }
                },
            )
        }
    }
}

@Composable
private fun PhotoThumb(viewModel: ToolsViewModel, uri: Uri) {
    val bitmap by produceState<Bitmap?>(viewModel.cachedPhoto(uri), uri) {
        if (value == null) value = viewModel.photoThumb(uri)
    }
    val shape = MaterialTheme.shapes.small
    Box(Modifier.size(PHOTO_THUMB).clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)) {
        bitmap?.let {
            Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun MoveButtons(onUp: () -> Unit, onDown: () -> Unit, onRemove: () -> Unit) {
    Row {
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
}

private val BUTTON_HEIGHT = 52.dp

@Composable
internal fun WideButton(
    label: Int,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = BUTTON_HEIGHT),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
    ) { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
internal fun SideButton(label: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = BUTTON_HEIGHT),
        shape = MaterialTheme.shapes.medium,
    ) { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) }
}
