package app.kholo.reader.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val Espresso = Color(0xFF3B2A20)
private val Biscuit = Color(0xFFEFE3D4)
private val Latte = Color(0xFFF5EDE3)
private val Oat = Color(0xFFEBDFD0)
private val Line = Color(0xFFDCCBB8)
private val Mocha = Color(0xFF76604F)
private val Coffee = Color(0xFF6F4E37)
private val Foam = Color(0xFFFFF8F0)

private val Roast = Color(0xFF17100C)
private val Bean = Color(0xFF1F1611)
private val Grounds = Color(0xFF2B1F18)
private val Husk = Color(0xFF3A2A21)
private val DarkCoffee = Color(0xFF4A3226)
private val Caramel = Color(0xFFC4AE9B)
private val Crema = Color(0xFFD7B899)
private val Steam = Color(0xFFF2E6D9)
private val DeepRoast = Color(0xFF2B1A12)

/** Coffee wali halki theme: latte kagaz, espresso syahi. Tez rang sirf file type aur tools ke liye hai. */
val LightColors = lightColorScheme(
    primary = Coffee,
    onPrimary = Foam,
    primaryContainer = Oat,
    onPrimaryContainer = Espresso,
    secondary = Mocha,
    onSecondary = Foam,
    secondaryContainer = Oat,
    onSecondaryContainer = Espresso,
    tertiary = Mocha,
    background = Latte,
    onBackground = Espresso,
    surface = Latte,
    onSurface = Espresso,
    surfaceVariant = Oat,
    onSurfaceVariant = Mocha,
    surfaceTint = Latte,
    surfaceBright = Foam,
    surfaceContainerLowest = Latte,
    surfaceContainerLow = Latte,
    surfaceContainer = Biscuit,
    surfaceContainerHigh = Oat,
    surfaceContainerHighest = Oat,
    outline = Mocha,
    outlineVariant = Line,
    error = Color(0xFFB3261E),
)

/** Wahi coffee, gehri bhuni hui. */
val DarkColors = darkColorScheme(
    primary = Crema,
    onPrimary = DeepRoast,
    primaryContainer = Husk,
    onPrimaryContainer = Steam,
    secondary = Caramel,
    onSecondary = DeepRoast,
    secondaryContainer = Husk,
    onSecondaryContainer = Steam,
    tertiary = Caramel,
    background = Roast,
    onBackground = Steam,
    surface = Bean,
    onSurface = Steam,
    surfaceVariant = Grounds,
    onSurfaceVariant = Caramel,
    surfaceTint = Bean,
    surfaceBright = Grounds,
    surfaceContainerLowest = Roast,
    surfaceContainerLow = Bean,
    surfaceContainer = Grounds,
    surfaceContainerHigh = Husk,
    surfaceContainerHighest = Husk,
    outline = Caramel,
    outlineVariant = Husk,
    error = Color(0xFFFFB4AB),
)

/** Top bar, tabs aur status bar ka coffee rang. Dono theme me bar gehra hai, isliye icons hamesha halke. */
data class BarColors(val container: Color, val content: Color, val quiet: Color)

val LightBar = BarColors(container = Coffee, content = Foam, quiet = Color(0xFFE6D5C3))
val DarkBar = BarColors(container = DarkCoffee, content = Color(0xFFF5E6D3), quiet = Color(0xFFD9C4B0))

val ReaderBackgroundLight = Color(0xFFEDE2D6)
val ReaderBackgroundDark = Roast
val FavoriteStar = Color(0xFFE8A200)

/** File type ka rang: dekhte hi pata chale kaunsi file hai. */
fun fileTypeColor(label: String, dark: Boolean): Color = when (label) {
    "PDF" -> if (dark) Color(0xFFFF8A80) else Color(0xFFC62828)
    "DOC" -> if (dark) Color(0xFF8AB4F8) else Color(0xFF1A5FB4)
    "XLS" -> if (dark) Color(0xFF81C995) else Color(0xFF1B7F3B)
    "PPT" -> if (dark) Color(0xFFFDB57A) else Color(0xFFB95000)
    "JPG" -> if (dark) Color(0xFFD7AEFB) else Color(0xFF7B3F8C)
    else -> if (dark) Caramel else Mocha
}

/** Tools tab ke icon ka rang, taaki grid me har tool door se pehchana jaye. */
enum class ToolTone(val light: Color, val dark: Color) {
    Red(Color(0xFFC62828), Color(0xFFFF8A80)),
    Blue(Color(0xFF1A5FB4), Color(0xFF8AB4F8)),
    Green(Color(0xFF1B7F3B), Color(0xFF81C995)),
    Orange(Color(0xFFB95000), Color(0xFFFDB57A)),
    Plum(Color(0xFF7B3F8C), Color(0xFFD7AEFB)),
    Teal(Color(0xFF00796B), Color(0xFF80CBC4)),
}
