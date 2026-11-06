package app.kholo.reader.viewer

import android.net.Uri
import androidx.pdf.PdfDocument
import app.kholo.reader.core.AppError
import app.kholo.reader.office.OfficeContent
import app.kholo.reader.open.DocumentType
import app.kholo.reader.pdf.ReadingState

class OpenDocument(val document: PdfDocument, val name: String, val uri: String, val reading: ReadingState)

sealed interface ViewerUiState {
    data object Loading : ViewerUiState

    data class NeedsPassword(
        val name: String,
        val failedAttempt: Boolean,
        val detailsFailed: Boolean = false,
    ) : ViewerUiState

    data class Ready(val doc: OpenDocument) : ViewerUiState

    /** PDF ke alawa har file: uski apni screen use kholti hai. */
    data class Other(
        val type: DocumentType,
        val uri: Uri,
        val name: String,
        val size: Long?,
        val content: OfficeContent? = null,
    ) : ViewerUiState

    data class Failed(val error: AppError, val name: String?) : ViewerUiState
}
