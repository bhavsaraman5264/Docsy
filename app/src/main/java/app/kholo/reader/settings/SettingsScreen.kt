package app.kholo.reader.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kholo.reader.BuildConfig
import app.kholo.reader.R
import app.kholo.reader.ui.components.AppTopBar
import app.kholo.reader.ui.components.ChoiceChips
import app.kholo.reader.ui.theme.Spacing
import kotlinx.coroutines.launch

private const val ANDROID_T = 33

@Composable
fun SettingsScreen(settings: SettingsRepository, onBack: () -> Unit, onReport: () -> Unit) {
    val theme by settings.theme.collectAsStateWithLifecycle(initialValue = ThemeMode.System)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Scaffold(topBar = { AppTopBar(stringResource(R.string.action_settings), onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Column(Modifier.padding(Spacing.screenPadding), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
                ChoiceChips(
                    options = ThemeMode.entries,
                    selected = theme,
                    label = { mode ->
                        stringResource(
                            when (mode) {
                                ThemeMode.System -> R.string.theme_system
                                ThemeMode.Light -> R.string.theme_light
                                ThemeMode.Dark -> R.string.theme_dark
                            },
                        )
                    },
                    onSelect = { scope.launch { settings.setTheme(it) } },
                )
            }
            HorizontalDivider()
            if (Build.VERSION.SDK_INT >= ANDROID_T) {
                SettingRow(stringResource(R.string.settings_language)) {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                }
            }
            SettingRow(stringResource(R.string.action_report), onClick = onReport)
            HorizontalDivider()
            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                modifier = Modifier.padding(Spacing.screenPadding),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(Spacing.screenPadding),
        style = MaterialTheme.typography.bodyLarge,
    )
}
