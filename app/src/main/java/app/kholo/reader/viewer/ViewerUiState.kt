package app.kholo.reader.viewer

import androidx.pdf.PdfDocument
import app.kholo.reader.core.AppError
import app.kholo.reader.pdf.ReadingState

class OpenDocument(val document: PdfDocument, val name: String, val uri: String, val reading: ReadingState)

sealed interface ViewerUiState {
    data object Loading : ViewerUiState

    data class NeedsPassword(val name: String, val failedAttempt: Boolean) : ViewerUiState

    data class Ready(val doc: OpenDocument) : ViewerUiState

    data class Failed(val error: AppError, val name: String?) : ViewerUiState
}
