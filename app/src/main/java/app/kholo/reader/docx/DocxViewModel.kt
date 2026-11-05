package app.kholo.reader.docx

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kholo.reader.core.AppContainer
import app.kholo.reader.core.AppError
import app.kholo.reader.core.LoadState
import app.kholo.reader.core.file.SafeZip
import app.kholo.reader.core.file.readUpTo
import app.kholo.reader.core.file.toAppError
import app.kholo.reader.open.DocumentType
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParserException

private const val MAX_IMAGE_SIDE = 1400
private const val MAX_IMAGE_BYTES = 32 * 1024 * 1024

class DocxViewModel(
    private val uri: Uri,
    private val type: DocumentType,
    private val size: Long?,
    private val container: AppContainer,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<List<DocBlock>>>(LoadState.Loading)
    val state: StateFlow<LoadState<List<DocBlock>>> = _state.asStateFlow()
    private var zip: SafeZip? = null
    private var tempFile: File? = null

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
            }
        }
    }

    private fun parse(): List<DocBlock> {
        val suffix = if (type == DocumentType.Pptx) ".pptx" else ".docx"
        val file = container.tempFiles.copy(uri, suffix, size).also { tempFile = it }
        val opened = SafeZip(file).also { zip = it }
        return if (type == DocumentType.Pptx) PptxParser(opened).parse() else DocxParser(opened).parse()
    }

    /** Tasveer dikhne par hi decode hoti hai, screen ke hisaab se chhoti karke. */
    suspend fun image(path: String): Bitmap? = withContext(container.dispatchers.io) {
        try {
            val bytes = zip?.open(path)?.use { it.readUpTo(MAX_IMAGE_BYTES) } ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_SIDE) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: IOException) {
            null
        }
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
