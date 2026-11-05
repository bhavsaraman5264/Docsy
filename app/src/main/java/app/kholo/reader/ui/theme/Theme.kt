package app.kholo.reader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kholo.reader.settings.SettingsRepository
import app.kholo.reader.settings.ThemeMode

val LocalReaderBackground = staticCompositionLocalOf { ReaderBackgroundLight }

/** Dynamic color jaan-boojh kar band hai, taaki har phone par app ek jaisa dikhe. */
@Composable
fun KholoTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val readerBackground: Color = if (darkTheme) ReaderBackgroundDark else ReaderBackgroundLight
    CompositionLocalProvider(LocalReaderBackground provides readerBackground) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = KholoTypography,
            shapes = KholoShapes,
            content = content,
        )
    }
}

/** Settings me chuni theme ke saath [KholoTheme]. */
@Composable
fun AppTheme(settings: SettingsRepository, content: @Composable () -> Unit) {
    val mode by settings.theme.collectAsStateWithLifecycle(initialValue = ThemeMode.System)
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    KholoTheme(darkTheme = dark, content = content)
}
