package app.kholo.reader.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.kholo.reader.R
import app.kholo.reader.ui.components.ChoiceChips
import app.kholo.reader.ui.theme.Spacing

/** Kya saaf karna hai (kai chip ek saath) aur nateeja rangeen ya kaala-safed. */
@Composable
internal fun CleanScanOptions(viewModel: ToolsViewModel) {
    // Chip ek Int ke bit me: phone ghumne par bhi bachte hain, bina kisi saver ke.
    var fixes by rememberSaveable { mutableIntStateOf(CleanFix.defaults) }
    var blackWhite by rememberSaveable { mutableStateOf(false) }
    ToolPanel(
        label = R.string.tool_clean_scan,
        onRun = { viewModel.run(CleanScanRunner(CleanChoice(fixes, blackWhite))) },
        enabled = fixes != 0 || blackWhite,
    ) {
        Text(stringResource(R.string.clean_what), style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            CleanFix.entries.forEach { fix ->
                FilterChip(
                    selected = fixes and fix.bit != 0,
                    onClick = { fixes = fixes xor fix.bit },
                    label = { Text(stringResource(fix.label)) },
                )
            }
        }
        Text(stringResource(R.string.clean_result), style = MaterialTheme.typography.titleSmall)
        ChoiceChips(
            options = listOf(false, true),
            selected = blackWhite,
            label = { stringResource(if (it) R.string.clean_black_white else R.string.clean_color) },
            onSelect = { blackWhite = it },
        )
        OptionNote(if (blackWhite) R.string.clean_black_white_note else R.string.clean_note)
    }
}

private val CleanFix.label: Int
    get() = when (this) {
        CleanFix.Crop -> R.string.clean_crop
        CleanFix.Edges -> R.string.clean_edges
        CleanFix.Holes -> R.string.clean_holes
        CleanFix.Light -> R.string.clean_light
        CleanFix.Straighten -> R.string.clean_straighten
        CleanFix.Dots -> R.string.clean_dots
        CleanFix.Blank -> R.string.clean_blank
    }
