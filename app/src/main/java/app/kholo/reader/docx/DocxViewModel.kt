package app.kholo.reader.docx

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kholo.reader.core.AppContainer
import app.kholo.reader.core.AppError
import app.kholo.reader.core.LoadState
import app.kholo.reader.core.file.SafeZip
import app.kholo.reader.core.file.toAppError
import app.kholo.reader.office.OfficeContent
import app.kholo.reader.office.PptParser
import app.kholo.reader.office.WordParser
import app.kholo.reader.open.DocumentType
import app.kholo.reader.viewer.FindController
import app.kholo.reader.viewer.MAX_FIND_HITS
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParserException

private const val MAX_IMAGE_SIDE = 1400

class DocxViewModel(
    private val uri: Uri,
    private val type: DocumentType,
    private val size: Long?,
    private val container: AppContainer,
    private val content: OfficeContent? = null,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<ParsedDocument>>(LoadState.Loading)
    val state: StateFlow<LoadState<ParsedDocument>> = _state.asStateFlow()
    private var zip: SafeZip? = null
    private var tempFile: File? = null

    val find = FindController<DocHit>(viewModelScope, container.dispatchers.io, ::scan)

    init {
        viewModelScope.launch {
            _state.value = try {
                withContext(container.dispatchers.default) { LoadState.Ready(parse()) }
            } catch (e: IOException) {
                LoadState.Failed(e.toAppError())
            } catch (e: SecurityException) {
                LoadState.Failed(e.toAppError())
            } catch (_: XmlPullParserException) {
                LoadState.Failed(AppError.Corrupt)
            } catch (_: IllegalArgumentException) {
                LoadState.Failed(AppError.Corrupt)
            } catch (_: IndexOutOfBoundsException) {
                LoadState.Failed(AppError.Corrupt)
            }
        }
    }

    private fun parse(): ParsedDocument = when (content) {
        is OfficeContent.Word -> WordParser(content.document, content.table).parse()
        is OfficeContent.Slides -> PptParser(content.document).parse()
        else -> parseZip()
    }

    private fun parseZip(): ParsedDocument {
        val suffix = if (type == DocumentType.Pptx) ".pptx" else ".docx"
        // Password wali file pehle hi khul kar private cache me hai, aur use ViewerViewModel mitata hai.
        val file = (content as? OfficeContent.Package)?.file
            ?: container.tempFiles.copy(uri, suffix, size).also { tempFile = it }
        val opened = SafeZip(file).also { zip = it }
        return if (type == DocumentType.Pptx) {
            PptxParser(opened).parse()
        } else {
            DocxParser(opened).parse { partial -> _state.value = LoadState.Ready(partial) }
        }
    }

    private suspend fun scan(query: String, emit: (List<DocHit>) -> Unit) {
        val blocks = (_state.value as? LoadState.Ready)?.value?.blocks ?: return
        var found = 0
        for ((index, block) in blocks.withIndex()) {
            if (found >= MAX_FIND_HITS) break
            coroutineContext.ensureActive()
            val hits = blockHits(index, block, query)
            if (hits.isNotEmpty()) emit(hits)
            found += hits.size
        }
    }

    /** Tasveer dikhne par hi decode hoti hai, screen ke hisaab se chhoti karke. */
    suspend fun image(path: String): Bitmap? = withContext(container.dispatchers.io) {
        zip?.decodeImage(path, MAX_IMAGE_SIDE)
    }

    override fun onCleared() {
        try {
            zip?.close()
        } catch (_: IOException) {
            // Temp file safai niche hogi hi.
        }
        tempFile?.delete()
    }
}
