package app.kholo.reader.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

private const val MEDIUM_WIDTH_DP = 600
private const val EXPANDED_WIDTH_DP = 840
private const val SHORT_HEIGHT_DP = 480

enum class WidthClass { Compact, Medium, Expanded }

@Immutable
data class WindowInfo(
    val widthClass: WidthClass,
    val isShortHeight: Boolean,
    val isLandscape: Boolean,
)

fun windowInfoFor(widthDp: Int, heightDp: Int): WindowInfo = WindowInfo(
    widthClass = when {
        widthDp < MEDIUM_WIDTH_DP -> WidthClass.Compact
        widthDp < EXPANDED_WIDTH_DP -> WidthClass.Medium
        else -> WidthClass.Expanded
    },
    isShortHeight = heightDp < SHORT_HEIGHT_DP,
    isLandscape = widthDp > heightDp,
)

/** Split-screen aur foldable me yeh window ka size deta hai, poore screen ka nahi. */
@Composable
fun rememberWindowInfo(): WindowInfo {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    return windowInfoFor((size.width / density).toInt(), (size.height / density).toInt())
}
