package app.kholo.reader.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF1C1C1E)
private val Paper = Color(0xFFFFFFFF)
private val Mist = Color(0xFFF2F2F4)
private val Line = Color(0xFFE5E5E7)
private val Slate = Color(0xFF6B6B70)

private val Night = Color(0xFF000000)
private val Coal = Color(0xFF121214)
private val Graphite = Color(0xFF1E1E20)
private val Ash = Color(0xFF2C2C2E)
private val Silver = Color(0xFFAEAEB2)
private val Fog = Color(0xFFE5E5EA)

/** Sirf safed aur syahi. Rang file type ke liye bachaya hai, Material ka baingani tint kahin nahi aana chahiye. */
val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = Mist,
    onPrimaryContainer = Ink,
    secondary = Slate,
    onSecondary = Paper,
    secondaryContainer = Mist,
    onSecondaryContainer = Ink,
    tertiary = Slate,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = Slate,
    surfaceTint = Paper,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Paper,
    surfaceContainer = Paper,
    surfaceContainerHigh = Mist,
    surfaceContainerHighest = Mist,
    outline = Slate,
    outlineVariant = Line,
    error = Color(0xFFB3261E),
)

/** Black ke saath grey. */
val DarkColors = darkColorScheme(
    primary = Fog,
    onPrimary = Ink,
    primaryContainer = Ash,
    onPrimaryContainer = Fog,
    secondary = Silver,
    onSecondary = Ink,
    secondaryContainer = Ash,
    onSecondaryContainer = Fog,
    tertiary = Silver,
    background = Night,
    onBackground = Fog,
    surface = Coal,
    onSurface = Fog,
    surfaceVariant = Graphite,
    onSurfaceVariant = Silver,
    surfaceTint = Coal,
    surfaceContainerLowest = Night,
    surfaceContainerLow = Coal,
    surfaceContainer = Coal,
    surfaceContainerHigh = Graphite,
    surfaceContainerHighest = Ash,
    outline = Silver,
    outlineVariant = Ash,
    error = Color(0xFFFFB4AB),
)

val ReaderBackgroundLight = Color(0xFFEDEDEF)
val ReaderBackgroundDark = Night
val FavoriteStar = Color(0xFFE8A200)

/** File type ka rang: dekhte hi pata chale kaunsi file hai. */
fun fileTypeColor(label: String, dark: Boolean): Color = when (label) {
    "PDF" -> if (dark) Color(0xFFFF8A80) else Color(0xFFC62828)
    "DOC" -> if (dark) Color(0xFF8AB4F8) else Color(0xFF1A5FB4)
    "XLS" -> if (dark) Color(0xFF81C995) else Color(0xFF1B7F3B)
    "PPT" -> if (dark) Color(0xFFFDB57A) else Color(0xFFB95000)
    else -> if (dark) Silver else Slate
}
