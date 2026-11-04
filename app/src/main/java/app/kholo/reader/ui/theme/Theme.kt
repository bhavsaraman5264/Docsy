package app.kholo.reader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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
