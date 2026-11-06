package app.kholo.reader.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kholo.reader.R
import app.kholo.reader.core.AppContainer
import app.kholo.reader.ui.components.AppTopBar
import app.kholo.reader.ui.components.ChoiceChips
import app.kholo.reader.ui.theme.Spacing
import kotlinx.coroutines.withContext

private val CATEGORIES = listOf("zoom", "open", "slow", "crash", "other")

@Composable
fun ReportScreen(container: AppContainer, category: String, onBack: () -> Unit, onSend: (String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(category) }
    var note by rememberSaveable { mutableStateOf("") }
    val crash by produceState<String?>(null) {
        value = withContext(container.dispatchers.io) { container.crashRecorder.pending() }
    }
    val report = remember(selected, note, crash) { container.bugReports.build(selected, note, crash) }
    Scaffold(topBar = { AppTopBar(stringResource(R.string.action_report), onBack = onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(Spacing.screenPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            ChoiceChips(
                options = CATEGORIES,
                selected = selected,
                label = { stringResource(labelFor(it)) },
                onSelect = { selected = it },
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.report_note)) },
            )
            Button(
                onClick = {
                    container.crashRecorder.clear()
                    onSend(report)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            ) { Text(stringResource(R.string.action_send)) }
            Text(stringResource(R.string.report_preview), style = MaterialTheme.typography.titleSmall)
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text(report, modifier = Modifier.padding(Spacing.m), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun labelFor(category: String): Int = when (category) {
    "zoom" -> R.string.report_zoom
    "open" -> R.string.report_open
    "slow" -> R.string.report_slow
    "crash" -> R.string.report_crash
    else -> R.string.report_other
}
