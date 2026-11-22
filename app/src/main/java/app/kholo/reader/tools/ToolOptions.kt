package app.kholo.reader.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.kholo.reader.R
import app.kholo.reader.ui.adaptive.rememberWindowInfo
import app.kholo.reader.ui.components.ChoiceChips
import app.kholo.reader.ui.theme.Spacing

private val OPTIONS_HEIGHT = 240.dp
private val SHORT_OPTIONS_HEIGHT = 96.dp

/** Button ke saath wala doosra kaam: aur file jodna ya file badalna. */
internal class SideAction(val label: Int, val onClick: () -> Unit)

@Composable
internal fun ToolOptions(viewModel: ToolsViewModel, state: ToolUiState, onPick: () -> Unit) {
    val kind = viewModel.kind
    val canAdd = kind.multipleInputs && state.inputs.size < kind.maxInputs
    val more = if (canAdd) SideAction(pickLabel(kind, hasInputs = true), onPick) else null
    val run = viewModel::run
    when (kind) {
        ToolKind.Photos -> ToolPanel(R.string.tool_make_pdf, { run(PhotosRunner("photos")) }, more)
        ToolKind.Scan -> ToolPanel(R.string.tool_make_pdf, { run(PhotosRunner("")) }, more)
        ToolKind.IdCard -> ToolPanel(R.string.tool_make_pdf, { run(IdCardRunner) }, more) {
            OptionNote(if (state.inputs.size < ID_CARD_SIDES) R.string.id_card_add_back else R.string.id_card_ready)
        }
        ToolKind.Merge -> ToolPanel(R.string.tool_merge, { run(MergeRunner) }, more, state.inputs.size >= 2) {
            if (state.inputs.size < 2) OptionNote(R.string.tool_merge_need_two)
        }
        ToolKind.Split -> SplitOptions(viewModel, state.pageCount)
        ToolKind.Extract -> ExtractOptions(viewModel, state.pageCount)
        ToolKind.Organize -> ToolPanel(
            label = R.string.action_save,
            onRun = { run(OrganizeRunner) },
            side = SideAction(R.string.action_change, onPick),
            enabled = state.pageOrder.isNotEmpty(),
        ) { if (state.pageOrder.isNotEmpty()) PageActions(viewModel, state) }
        ToolKind.Compress -> CompressOptions(viewModel)
        ToolKind.Lock -> PasswordOption(R.string.tool_lock) { run(LockRunner(it)) }
        ToolKind.Unlock -> PasswordOption(R.string.tool_unlock) { run(UnlockRunner(it)) }
        ToolKind.Watermark -> WatermarkOptions(viewModel)
        ToolKind.PageNumbers -> PageNumberOptions(viewModel)
        ToolKind.CleanScan -> CleanScanOptions(viewModel)
        ToolKind.Text -> ToolPanel(R.string.tool_text, { run(TextRunner) })
        ToolKind.PdfToWord -> ToolPanel(R.string.tool_pdf_to_word, { run(PdfToWordRunner) })
        ToolKind.WordToPdf -> ToolPanel(R.string.tool_word_to_pdf, { run(WordToPdfRunner) })
        ToolKind.TextToPdf -> ToolPanel(R.string.tool_make_pdf, { run(TextToPdfRunner) })
        ToolKind.PdfToImages -> ToolPanel(R.string.tool_pdf_to_jpg, { run(PdfToImagesRunner) })
        ToolKind.ShrinkPhoto -> ExamPhotoOptions(viewModel)
    }
}

/** Keyboard ya chhoti screen par options apne andar scroll hote hain, par kaam ka button hamesha dikhta hai. */
@Composable
internal fun ToolPanel(
    label: Int,
    onRun: () -> Unit,
    side: SideAction? = null,
    enabled: Boolean = true,
    options: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val optionsHeight = if (rememberWindowInfo().isShortHeight) SHORT_OPTIONS_HEIGHT else OPTIONS_HEIGHT
    Column(Modifier.padding(Spacing.screenPadding), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        if (options != null) {
            Column(
                Modifier.heightIn(max = optionsHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                content = options,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (side != null) SideButton(side.label, Modifier.weight(1f), side.onClick)
            WideButton(label, Modifier.weight(1f), enabled, onRun)
        }
    }
}

@Composable
internal fun OptionNote(text: Int) = OptionNote(stringResource(text))

@Composable
internal fun OptionNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Ek jagah kaatne se do file banti hain, aur neeche pehle hi dikhta hai kaunse page kismein jayenge. */
@Composable
private fun SplitOptions(viewModel: ToolsViewModel, pageCount: Int) {
    var after by remember(pageCount) { mutableStateOf((pageCount / 2).coerceAtLeast(1).toString()) }
    val cut = after.toIntOrNull()
    val parts = cut?.let { splitParts(pageCount, it) }
    ToolPanel(
        label = R.string.tool_split,
        onRun = { if (cut != null) viewModel.run(SplitRunner(cut)) },
        enabled = parts != null,
    ) {
        if (pageCount == 1) {
            OptionNote(R.string.tool_split_one_page)
        } else {
            NumberField(R.string.tool_split_after, after, Modifier.fillMaxWidth()) { after = it }
            if (parts != null) {
                val (first, second) = parts
                Text(
                    text = stringResource(
                        R.string.tool_split_preview,
                        first.first + 1,
                        first.last + 1,
                        second.first + 1,
                        second.last + 1,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExtractOptions(viewModel: ToolsViewModel, pageCount: Int) {
    var from by remember { mutableStateOf("1") }
    var to by remember(pageCount) { mutableStateOf(pageCount.toString()) }
    val start = from.toIntOrNull()
    val end = to.toIntOrNull()
    val valid = start != null && end != null && start in 1..end && end <= pageCount
    ToolPanel(
        label = R.string.tool_extract,
        onRun = { if (start != null && end != null) viewModel.run(ExtractRunner(start, end)) },
        enabled = valid,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            NumberField(R.string.tool_from_page, from, Modifier.weight(1f)) { from = it }
            NumberField(R.string.tool_to_page, to, Modifier.weight(1f)) { to = it }
        }
    }
}

@Composable
internal fun NumberField(label: Int, value: String, modifier: Modifier, onChange: (String) -> Unit) {
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
    ToolPanel(R.string.tool_compress, { viewModel.run(CompressRunner(target)) }) {
        TargetChoices(CompressTarget.entries, target, { stringResource(it.label) }) { target = it }
    }
}

@Composable
internal fun <T> TargetChoices(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Text(stringResource(R.string.tool_target_size), style = MaterialTheme.typography.titleSmall)
    ChoiceChips(options, selected, label, onSelect)
}

@Composable
private fun PasswordOption(label: Int, onRun: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(false) }
    ToolPanel(label, { onRun(password) }, enabled = password.isNotEmpty()) {
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.password_label)) },
            singleLine = true,
            visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(onClick = { shown = !shown }) {
                    Text(stringResource(if (shown) R.string.action_hide else R.string.action_show))
                }
            },
        )
    }
}
