package app.kholo.reader.viewer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.pdf.PdfDocument
import app.kholo.reader.core.AppContainer
import app.kholo.reader.core.AppError
import app.kholo.reader.core.DocResult
import app.kholo.reader.core.file.FileInfo
import app.kholo.reader.core.file.toAppError
import app.kholo.reader.core.log.AppLog
import app.kholo.reader.core.log.LogTag
import app.kholo.reader.office.OfficeContent
import app.kholo.reader.office.OfficeDocument
import app.kholo.reader.office.OfficeFile
import app.kholo.reader.office.OfficeLock
import app.kholo.reader.office.OleResult
import app.kholo.reader.open.DocumentType
import app.kholo.reader.pdf.PdfCapabilities
import app.kholo.reader.recents.RecentFile
import app.kholo.reader.recents.fileIdentity
import app.kholo.reader.recents.isToolResult
import app.kholo.reader.unlock.DocKind
import app.kholo.reader.unlock.PasswordRules
import app.kholo.reader.unlock.UnlockDetails
import java.io.File
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
    private var lock: OfficeLock? = null
    private var decrypted: File? = null

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
            DocumentType.Doc, DocumentType.Xls, DocumentType.Ppt, DocumentType.LegacyOffice -> openOle(file)
            DocumentType.Unknown -> fail(AppError.Unsupported, file.name)
        }
    }

    private suspend fun detect(file: FileInfo): DocumentType {
        val type = DocumentType.detect(file.head, file.name, file.mime)
        val isZip = file.head.size > 1 && file.head[0] == 'P'.code.toByte() && file.head[1] == 'K'.code.toByte()
        if (type != DocumentType.Unknown || !isZip) return type
        return withContext(container.dispatchers.io) { container.uriReader.zipType(uri) }
    }

    /** OLE2 ke andar dekhe bina pata nahi chalta: purani file hai, ya password wali nayi. */
    private suspend fun openOle(file: FileInfo) {
        val name = file.name.orEmpty()
        val result = try {
            withContext(container.dispatchers.io) {
                OfficeFile(container.tempFiles::newFile).open(container.tempFiles.copy(uri, ".ole", file.size))
            }
        } catch (e: IOException) {
            fail(e.toAppError(), name)
            return
        } catch (e: SecurityException) {
            fail(e.toAppError(), name)
            return
        }
        when (result) {
            is OleResult.Ready -> readyOffice(result.document, name)
            is OleResult.Locked -> {
                lock = result.lock
                _state.value = ViewerUiState.NeedsPassword(name, failedAttempt = false)
            }
            OleResult.PasswordUnsupported -> fail(AppError.OfficePassword, name)
        }
    }

    private suspend fun readyOffice(opened: OfficeDocument, name: String) {
        val size = info?.size
        decrypted = (opened.content as? OfficeContent.Package)?.file
        closeLock()
        record(name, size ?: 0L, opened.type)
        _state.value = ViewerUiState.Other(opened.type, uri, name, size, opened.content)
    }

    /** Agile file ka password jaanchne me lakh se zyada hash lagte hain, isliye main thread se door. */
    private suspend fun tryOffice(office: OfficeLock, password: String): DocResult<OfficeDocument> = try {
        val opened = withContext(container.dispatchers.default) { office.unlock(password) }
        if (opened == null) DocResult.Failure(AppError.WrongPassword) else DocResult.Success(opened)
    } catch (e: IOException) {
        DocResult.Failure(e.toAppError())
    } catch (_: IndexOutOfBoundsException) {
        DocResult.Failure(AppError.Corrupt)
    } catch (_: IllegalArgumentException) {
        DocResult.Failure(AppError.Corrupt)
    }

    private suspend fun submitOffice(office: OfficeLock, password: String) {
        val name = info?.name.orEmpty()
        when (val result = tryOffice(office, password)) {
            is DocResult.Success -> readyOffice(result.value, name)
            is DocResult.Failure -> if (result.error == AppError.WrongPassword) {
                _state.value = ViewerUiState.NeedsPassword(name, failedAttempt = true)
            } else {
                fail(result.error, name)
            }
        }
    }

    private suspend fun unlockOffice(office: OfficeLock, kind: DocKind, details: UnlockDetails) {
        val name = info?.name.orEmpty()
        for (candidate in PasswordRules.candidates(kind, details)) {
            when (val result = tryOffice(office, candidate)) {
                is DocResult.Success -> {
                    readyOffice(result.value, name)
                    return
                }
                is DocResult.Failure -> if (result.error != AppError.WrongPassword) {
                    fail(result.error, name)
                    return
                }
            }
        }
        _state.value = ViewerUiState.NeedsPassword(name, failedAttempt = false, detailsFailed = true)
    }

    private fun closeLock() {
        try {
            lock?.close()
        } catch (_: IOException) {
            AppLog.w(LogTag.OPEN) { "lock close failed" }
        }
        lock = null
    }

    private suspend fun readyOther(type: DocumentType, file: FileInfo) {
        val name = file.name.orEmpty()
        record(name, file.size ?: 0L, type)
        _state.value = ViewerUiState.Other(type, uri, name, file.size)
    }

    fun submitPassword(password: String) {
        _state.value = ViewerUiState.Loading
        val office = lock
        viewModelScope.launch { if (office != null) submitOffice(office, password) else openPdf(password) }
    }

    /** User ke details se bane kuch passwords try karta hai. Details yahan se aage kahin nahi jate. */
    fun unlockWith(kind: DocKind, details: UnlockDetails) {
        val name = info?.name.orEmpty()
        _state.value = ViewerUiState.Loading
        viewModelScope.launch {
            AppLog.i(LogTag.UNLOCK) { "trying kind=$kind" }
            lock?.let { office ->
                unlockOffice(office, kind, details)
                return@launch
            }
            for (candidate in PasswordRules.candidates(kind, details)) {
                when (val result = container.pdfOpener.open(uri, candidate)) {
                    is DocResult.Success -> {
                        AppLog.i(LogTag.UNLOCK) { "unlocked kind=$kind" }
                        ready(result.value, name)
                        return@launch
                    }
                    is DocResult.Failure -> if (result.error != AppError.WrongPassword) {
                        fail(result.error, name)
                        return@launch
                    }
                }
            }
            AppLog.i(LogTag.UNLOCK) { "no match kind=$kind" }
            _state.value = ViewerUiState.NeedsPassword(name, failedAttempt = false, detailsFailed = true)
        }
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
        if (opened.pageCount <= 0) {
            closeQuietly(opened)
            fail(AppError.Corrupt, name)
            return
        }
        document = opened
        val key = fileIdentity(uri.toString(), name, info?.size ?: 0L)
        val reading = container.readingState.load(key)
        AppLog.i(LogTag.PDF) { "opened pages=${opened.pageCount}" }
        record(name, info?.size ?: 0L, DocumentType.Pdf)
        _state.value = ViewerUiState.Ready(OpenDocument(opened, name, key, reading))
    }

    private suspend fun record(name: String, size: Long, type: DocumentType) {
        if (isToolResult(uri.toString())) return
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

    @Suppress("TooGenericExceptionCaught") // Engine ka process band ho chuka ho to close kuch bhi phenk sakta hai.
    private fun closeQuietly(target: PdfDocument) {
        try {
            target.close()
        } catch (_: IOException) {
            AppLog.w(LogTag.PDF) { "close failed" }
        } catch (_: RuntimeException) {
            AppLog.w(LogTag.PDF) { "close failed" }
        }
    }

    /** Band karna doosre process ko call hai. Back ki animation us par rukni nahi chahiye. */
    override fun onCleared() {
        Thread {
            document?.let(::closeQuietly)
            closeLock()
            decrypted?.delete()
        }.start()
    }
}
