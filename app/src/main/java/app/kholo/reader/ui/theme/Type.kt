package app.kholo.reader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Base = Typography()

private fun TextStyle.plain() = copy(letterSpacing = 0.sp)

private fun TextStyle.heading() = copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)

/**
 * System font, Devanagari bhi isi se, app me font file nahi. Material ki default letter spacing
 * akshar door-door kar deti hai aur har app ek jaisa lagta hai, isliye use hataya hai.
 */
val KholoTypography = Typography(
    headlineSmall = Base.headlineSmall.heading(),
    titleLarge = Base.titleLarge.heading(),
    titleMedium = Base.titleMedium.heading(),
    titleSmall = Base.titleSmall.heading(),
    bodyLarge = Base.bodyLarge.plain(),
    bodyMedium = Base.bodyMedium.plain(),
    bodySmall = Base.bodySmall.plain(),
    labelLarge = Base.labelLarge.plain(),
    labelMedium = Base.labelMedium.plain(),
    labelSmall = Base.labelSmall.plain(),
)
