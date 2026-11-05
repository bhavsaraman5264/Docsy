package app.kholo.reader.ui.theme

import android.app.UiModeManager
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kholo.reader.settings.SettingsRepository
import app.kholo.reader.settings.ThemeMode
import kotlinx.coroutines.flow.map

private const val ANDROID_S = 31

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

/**
 * Settings me chuni theme ke saath [KholoTheme]. Default Light hai, chahe phone dark ho.
 * Android 12+ ko bhi batate hain, taaki launch ka background aur status bar usi theme ke hon.
 */
@Composable
fun AppTheme(settings: SettingsRepository, content: @Composable () -> Unit) {
    // Saved theme aane se pehle null: yahan koi default dena restart ka chakkar bana deta hai,
    // kyunki setApplicationNightMode badalte hi Activity dobara banti hai.
    val savedMode = remember(settings) { settings.theme.map<ThemeMode, ThemeMode?> { it } }
    val mode by savedMode.collectAsStateWithLifecycle(initialValue = null)
    val dark = when (mode) {
        null, ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val window = LocalActivity.current?.window
    val view = LocalView.current
    LaunchedEffect(mode) {
        val chosen = mode
        if (chosen != null && Build.VERSION.SDK_INT >= ANDROID_S) {
            context.getSystemService(UiModeManager::class.java).setApplicationNightMode(nightModeFor(chosen))
        }
    }
    SideEffect {
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    KholoTheme(darkTheme = dark, content = content)
}

private fun nightModeFor(mode: ThemeMode): Int = when (mode) {
    ThemeMode.System -> UiModeManager.MODE_NIGHT_AUTO
    ThemeMode.Light -> UiModeManager.MODE_NIGHT_NO
    ThemeMode.Dark -> UiModeManager.MODE_NIGHT_YES
}
