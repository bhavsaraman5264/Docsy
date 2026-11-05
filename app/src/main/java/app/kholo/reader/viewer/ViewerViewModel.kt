package app.kholo.reader.viewer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.pdf.PdfDocument
import app.kholo.reader.core.AppContainer
import app.kholo.reader.core.AppError
import app.kholo.reader.core.DocResult
import app.kholo.reader.core.file.FileInfo
import app.kholo.reader.core.log.AppLog
import app.kholo.reader.core.log.LogTag
import app.kholo.reader.open.DocumentType
import app.kholo.reader.pdf.PdfCapabilities
import app.kholo.reader.recents.RecentFile
import app.kholo.reader.recents.fileIdentity
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val MODERN_OFFICE = setOf("docx", "xlsx", "xlsm", "pptx")

class ViewerViewModel(private val uri: Uri, private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<ViewerUiState>(ViewerUiState.Loading)
    val state: StateFlow<ViewerUiState> = _state.asStateFlow()

    private var info: FileInfo? = null
    private var document: PdfDocument? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val inspected = withContext(container.dispatchers.io) { container.uriReader.inspect(uri) }
        when (inspected) {
            is DocResult.Failure -> fail(inspected.error, null)
            is DocResult.Success -> route(inspected.value)
        }
    }

    private suspend fun route(file: FileInfo) {
        info = file
        val type = detect(file)
        AppLog.i(LogTag.OPEN) { "open type=$type mime=${file.mime} size=${file.size}" }
        when (type) {
            DocumentType.Pdf -> openPdf(password = null)
            DocumentType.Text, DocumentType.Csv, DocumentType.Docx, DocumentType.Xlsx, DocumentType.Pptx ->
                readyOther(type, file)
            DocumentType.Empty -> fail(AppError.Empty, file.name)
            DocumentType.LegacyOffice -> fail(legacyError(file.name), file.name)
            DocumentType.Unknown -> fail(AppError.Unsupported, file.name)
        }
    }

    private suspend fun detect(file: FileInfo): DocumentType {
        val type = DocumentType.detect(file.head, file.name, file.mime)
        val isZip = file.head.size > 1 && file.head[0] == 'P'.code.toByte() && file.head[1] == 'K'.code.toByte()
        if (type != DocumentType.Unknown || !isZip) return type
        return withContext(container.dispatchers.io) { container.uriReader.zipType(uri) }
    }

    /** `.xlsx` naam ki OLE2 file asal me password wali hoti hai, kharab nahi. */
    private fun legacyError(name: String?): AppError {
        val extension = name?.substringAfterLast('.', "")?.lowercase()
        return if (extension in MODERN_OFFICE) AppError.OfficePassword else AppError.Unsupported
    }

    private suspend fun readyOther(type: DocumentType, file: FileInfo) {
        val name = file.name.orEmpty()
        record(name, file.size ?: 0L, type)
        _state.value = ViewerUiState.Other(type, uri, name, file.size)
    }

    fun submitPassword(password: String) {
        _state.value = ViewerUiState.Loading
        viewModelScope.launch { openPdf(password) }
    }

    private suspend fun openPdf(password: String?) {
        val name = info?.name.orEmpty()
        when (val result = container.pdfOpener.open(uri, password)) {
            is DocResult.Success -> ready(result.value, name)
            is DocResult.Failure -> when {
                result.error != AppError.WrongPassword -> fail(result.error, name)
                // Fallback engine sahi password par bhi yahi error deta hai, isliye password maangna bekaar hai.
                !PdfCapabilities.current().fullFeatures -> fail(AppError.PasswordUnsupported, name)
                else -> _state.value = ViewerUiState.NeedsPassword(name, failedAttempt = password != null)
            }
        }
    }

    private suspend fun ready(opened: PdfDocument, name: String) {
        document = opened
        val key = fileIdentity(uri.toString(), name, info?.size ?: 0L)
        val reading = container.readingState.load(key)
        AppLog.i(LogTag.PDF) { "opened pages=${opened.pageCount}" }
        record(name, info?.size ?: 0L, DocumentType.Pdf)
        _state.value = ViewerUiState.Ready(OpenDocument(opened, name, key, reading))
    }

    private suspend fun record(name: String, size: Long, type: DocumentType) {
        val keeps = withContext(container.dispatchers.io) { container.uriReader.hasKeptAccess(uri) }
        container.recents.record(
            RecentFile(
                uri = uri.toString(),
                name = name,
                size = size,
                typeLabel = type.label,
                openedAt = System.currentTimeMillis(),
                keepsAccess = keeps,
            ),
        )
    }

    private suspend fun fail(error: AppError, name: String?) {
        AppLog.w(LogTag.OPEN) { "open failed error=${error.javaClass.simpleName}" }
        if (error == AppError.FileGone) container.recents.removeUnavailable(uri.toString())
        _state.value = ViewerUiState.Failed(error, name)
    }

    override fun onCleared() {
        try {
            document?.close()
        } catch (_: IOException) {
            AppLog.w(LogTag.PDF) { "close failed" }
        }
    }
}
