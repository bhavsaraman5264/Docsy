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
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        val type = DocumentType.detect(file.head, file.name, file.mime)
        AppLog.i(LogTag.OPEN) { "open type=$type mime=${file.mime} size=${file.size}" }
        when (type) {
            DocumentType.Pdf -> openPdf(password = null)
            DocumentType.Empty -> fail(AppError.Empty, file.name)
            else -> fail(AppError.Unsupported, file.name)
        }
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
        val key = uri.toString()
        val reading = container.readingState.load(key)
        AppLog.i(LogTag.PDF) { "opened pages=${opened.pageCount}" }
        container.recents.record(
            RecentFile(key, name, info?.size ?: 0L, "PDF", System.currentTimeMillis()),
        )
        _state.value = ViewerUiState.Ready(OpenDocument(opened, name, key, reading))
    }

    private suspend fun fail(error: AppError, name: String?) {
        AppLog.w(LogTag.OPEN) { "open failed error=${error.javaClass.simpleName}" }
        if (error == AppError.FileGone) container.recents.remove(uri.toString())
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
