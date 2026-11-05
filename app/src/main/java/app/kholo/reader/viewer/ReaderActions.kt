package app.kholo.reader.viewer

import androidx.compose.runtime.Composable
import app.kholo.reader.core.AppError

/** Har non-PDF reader ke common actions. */
class ReaderActions(
    val onBack: () -> Unit,
    val onShare: () -> Unit,
    val onOpenElsewhere: () -> Unit,
    val onReport: () -> Unit,
    val failed: @Composable (AppError) -> Unit,
)
