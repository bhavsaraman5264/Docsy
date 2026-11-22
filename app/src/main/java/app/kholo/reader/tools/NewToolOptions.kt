package app.kholo.reader.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.kholo.reader.R
import app.kholo.reader.ui.components.ChoiceChips
import app.kholo.reader.ui.theme.Spacing

private const val MAX_MARK_CHARS = 60
private const val MAX_NAME_CHARS = 40
private const val MAX_PHOTO_PX = 4000
private const val MAX_PHOTO_KB = 5000

/** Watermark ke aam text. Bhasha app ki hi hoti hai, aur Hindi bhi sahi chhapti hai. */
private val MARK_SUGGESTIONS = listOf(R.string.mark_kyc, R.string.mark_copy, R.string.mark_confidential)

@Composable
internal fun WatermarkOptions(viewModel: ToolsViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    val suggestions = MARK_SUGGESTIONS.map { stringResource(it) }
    ToolPanel(R.string.tool_watermark, { viewModel.run(WatermarkRunner(text)) }, enabled = text.isNotBlank()) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(MAX_MARK_CHARS) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.mark_text)) },
            singleLine = true,
        )
        ChoiceChips(suggestions, suggestions.firstOrNull { it == text }, { it }, { text = it })
    }
}

@Composable
internal fun PageNumberOptions(viewModel: ToolsViewModel) {
    var style by rememberSaveable { mutableStateOf(NumberStyle.Plain) }
    var place by rememberSaveable { mutableStateOf(NumberPlace.Center) }
    ToolPanel(R.string.tool_page_numbers, { viewModel.run(PageNumbersRunner(style, place)) }) {
        Text(stringResource(R.string.number_style), style = MaterialTheme.typography.titleSmall)
        ChoiceChips(NumberStyle.entries, style, { stringResource(it.label) }, onSelect = { style = it })
        Text(stringResource(R.string.number_place), style = MaterialTheme.typography.titleSmall)
        ChoiceChips(NumberPlace.entries, place, { stringResource(it.label) }, onSelect = { place = it })
    }
}

private val NumberStyle.label: Int
    get() = when (this) {
        NumberStyle.Plain -> R.string.number_plain
        NumberStyle.OfTotal -> R.string.number_of_total
    }

private val NumberPlace.label: Int
    get() = when (this) {
        NumberPlace.Center -> R.string.number_center
        NumberPlace.Right -> R.string.number_right
    }

/**
 * Exam ka preset, ya sirf KB, ya apna naap. Neeche hamesha likha rehta hai ki file kaisi banegi, taaki user form ki
 * notice se mila sake: niyam har saal badalte hain.
 */
@Composable
internal fun ExamPhotoOptions(viewModel: ToolsViewModel) {
    // null ka matlab "apna size".
    var preset by rememberSaveable { mutableStateOf<PhotoPreset?>(PhotoPreset.Kb50) }
    var custom by rememberSaveable(stateSaver = CustomSize.saver) { mutableStateOf(CustomSize()) }
    var name by rememberSaveable { mutableStateOf("") }
    val spec = preset?.spec ?: custom.spec()
    ToolPanel(
        label = R.string.tool_shrink_photo,
        onRun = { spec?.let { viewModel.run(ExamPhotoRunner(it, name)) } },
        enabled = spec != null,
    ) {
        Text(stringResource(R.string.photo_for_exam), style = MaterialTheme.typography.titleSmall)
        ChoiceChips(
            options = PhotoPreset.entries.filter { it.forExam },
            selected = preset,
            label = { stringResource(it.label) },
            onSelect = { preset = it },
        )
        Text(stringResource(R.string.photo_for_size), style = MaterialTheme.typography.titleSmall)
        val sizes = PhotoPreset.entries.filterNot { it.forExam } + listOf(null)
        ChoiceChips(sizes, preset, { stringResource(it?.label ?: R.string.photo_custom) }, onSelect = { preset = it })
        if (preset == null) CustomSizeFields(custom) { custom = it }
        spec?.let { OptionNote(it.summary()) }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MAX_NAME_CHARS) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.photo_name)) },
            singleLine = true,
        )
        OptionNote(R.string.photo_check_notice)
    }
}

/** User ke likhe naap. Khaali ya galat ho to [spec] null, aur button band rehta hai. */
internal data class CustomSize(
    val minKb: String = "",
    val maxKb: String = "50",
    val width: String = "",
    val height: String = "",
) {
    fun spec(): PhotoSpec? {
        val max = maxKb.toIntOrNull()?.takeIf { it in 1..MAX_PHOTO_KB }
        val min = minKb.toIntOrNull() ?: 0
        val w = width.toIntOrNull() ?: 0
        val h = height.toIntOrNull() ?: 0
        val noSize = w == 0 && h == 0
        val sized = w in 1..MAX_PHOTO_PX && h in 1..MAX_PHOTO_PX
        val sizeOk = noSize || sized
        return max?.takeIf { min < it && sizeOk }?.let { PhotoSpec(min, it, w, h) }
    }

    companion object {
        /** Phone ghumne par bhi likha hua bacha rahe. */
        val saver = listSaver<CustomSize, String>(
            save = { listOf(it.minKb, it.maxKb, it.width, it.height) },
            restore = { saved ->
                val (kb, size) = saved.chunked(2)
                CustomSize(kb[0], kb[1], size[0], size[1])
            },
        )
    }
}

@Composable
private fun CustomSizeFields(size: CustomSize, onChange: (CustomSize) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        NumberField(R.string.photo_min_kb, size.minKb, Modifier.weight(1f)) { onChange(size.copy(minKb = it)) }
        NumberField(R.string.photo_max_kb, size.maxKb, Modifier.weight(1f)) { onChange(size.copy(maxKb = it)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        NumberField(R.string.photo_width, size.width, Modifier.weight(1f)) { onChange(size.copy(width = it)) }
        NumberField(R.string.photo_height, size.height, Modifier.weight(1f)) { onChange(size.copy(height = it)) }
    }
}

@Composable
private fun PhotoSpec.summary(): String {
    val kb = if (minKb > 0) {
        stringResource(R.string.photo_kb_range, minKb, maxKb)
    } else {
        stringResource(R.string.photo_kb_max, maxKb)
    }
    return if (hasSize) "$kb · ${stringResource(R.string.photo_pixels, width, height)}" else kb
}
