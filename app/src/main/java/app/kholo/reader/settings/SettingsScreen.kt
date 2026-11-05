package app.kholo.reader.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kholo.reader.BuildConfig
import app.kholo.reader.R
import app.kholo.reader.ui.components.AppTopBar
import app.kholo.reader.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(settings: SettingsRepository, onBack: () -> Unit) {
    val theme by settings.theme.collectAsStateWithLifecycle(initialValue = ThemeMode.Light)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = LocalActivity.current
    var language by remember { mutableStateOf(LanguageStore.current(context)) }
    Scaffold(topBar = { AppTopBar(stringResource(R.string.action_settings), onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            RadioSection(
                title = stringResource(R.string.settings_theme),
                options = ThemeMode.entries,
                selected = theme,
                label = { stringResource(themeLabel(it)) },
                onSelect = { scope.launch { settings.setTheme(it) } },
            )
            RadioSection(
                title = stringResource(R.string.settings_language),
                options = AppLanguage.entries,
                selected = language,
                label = { stringResource(languageLabel(it)) },
                onSelect = { choice ->
                    language = choice
                    activity?.let { LanguageStore.set(it, choice) }
                },
            )
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun themeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.System -> R.string.same_as_phone
    ThemeMode.Light -> R.string.theme_light
    ThemeMode.Dark -> R.string.theme_dark
}

private fun languageLabel(language: AppLanguage): Int = when (language) {
    AppLanguage.System -> R.string.same_as_phone
    AppLanguage.English -> R.string.language_english
    AppLanguage.Hindi -> R.string.language_hindi
}

/** Radio list: poori row tap hoti hai, aur TalkBack ko pata chalta hai ki yeh ek hi chunne wali list hai. */
@Composable
private fun <T> RadioSection(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.selectableGroup().padding(vertical = Spacing.s)) {
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.minTouchTarget)
                        .selectable(selected = option == selected, role = Role.RadioButton, onClick = {
                            onSelect(option)
                        })
                        .padding(horizontal = Spacing.l),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == selected, onClick = null)
                    Text(label(option), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
