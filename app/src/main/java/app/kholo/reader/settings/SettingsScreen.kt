package app.kholo.reader.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kholo.reader.BuildConfig
import app.kholo.reader.R
import app.kholo.reader.ui.components.AppTopBar
import app.kholo.reader.ui.theme.Spacing
import kotlinx.coroutines.launch

private val ROW_HEIGHT = 56.dp

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
                .verticalScroll(rememberScrollState()),
        ) {
            DropdownRow(
                title = stringResource(R.string.settings_theme),
                options = ThemeMode.entries,
                selected = theme,
                label = { stringResource(themeLabel(it)) },
                onSelect = { scope.launch { settings.setTheme(it) } },
            )
            DropdownRow(
                title = stringResource(R.string.settings_language),
                options = AppLanguage.entries,
                selected = language,
                label = { stringResource(languageLabel(it)) },
                onSelect = { choice ->
                    language = choice
                    activity?.let { LanguageStore.set(it, choice) }
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                modifier = Modifier.padding(horizontal = Spacing.screenPadding, vertical = Spacing.l),
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

/** Ek line: left me naam, right me chuni hui value. Tap par wahin chhoti list khulti hai. */
@Composable
private fun <T> DropdownRow(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .clickable(role = Role.DropdownList) { open = true }
            .padding(horizontal = Spacing.screenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label(selected),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                options.forEach { option ->
                    val chosen = option == selected
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = label(option),
                                fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            open = false
                            if (!chosen) onSelect(option)
                        },
                    )
                }
            }
        }
    }
}
