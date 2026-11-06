package app.kholo.reader.tools

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kholo.reader.R
import app.kholo.reader.core.AppContainer
import app.kholo.reader.core.AppError
import app.kholo.reader.core.DocResult
import app.kholo.reader.core.file.toAppError
import app.kholo.reader.core.log.AppLog
import app.kholo.reader.core.log.LogTag
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val THUMB_WIDTH = 160
private const val PDF_MIME = "application/pdf"
private const val TEXT_MIME = "text/plain"
private const val MAX_BITMAP_SIDE = 4096

data class ToolInput(val uri: Uri, val name: String, val size: Long)

data class ToolResult(val file: File, val name: String, val mime: String, val note: Int? = null, val before: Long = 0L)

data class ToolUiState(
    val inputs: List<ToolInput> = emptyList(),
    val pageOrder: List<Int> = emptyList(),
    val pageCount: Int = 0,
    val busy: Boolean = false,
    val result: ToolResult? = null,
    val error: AppError? = null,
)

/** Naam doosre app se aata hai, isliye folder ke bahar jaane wale akshar nahi rakhte. */
private val UNSAFE_NAME = Regex("[^\\p{L}\\p{M}\\p{N} ._()-]")
private const val MAX_NAME_CHARS = 80

/** Result ka naam file system ke liye surakshit banata hai, Hindi akshar bachte hain. */
internal fun safeFileBase(name: String?): String =
    name.orEmpty().substringBeforeLast('.').replace(UNSAFE_NAME, "_").take(MAX_NAME_CHARS).trim('.', ' ')
        .ifEmpty { "kholo" }

class ToolsViewModel(val kind: ToolKind, private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ToolUiState())
    val state: StateFlow<ToolUiState> = _state.asStateFlow()
    private val tools = container.pdfTools
    private val temp = container.tempFiles
    private val temps = mutableListOf<File>()

    /** Ek samay ek hi kaam: render, band karna aur file badalna ek saath chale to PdfRenderer gir jata hai. */
    private val renderGuard = Any()
    private var cleared = false
    private var renderer: PdfRenderer? = null
    private var rendererFd: ParcelFileDescriptor? = null
    private var sourceFile: File? = null

    fun addInputs(uris: List<Uri>) {
        if (uris.isEmpty()) return
        launchWork {
            val added = uris.mapNotNull { uri ->
                when (val info = container.uriReader.inspect(uri)) {
                    is DocResult.Success -> ToolInput(uri, info.value.name.orEmpty(), info.value.size ?: 0L)
                    is DocResult.Failure -> null
                }
            }
            if (added.isEmpty()) throw FileNotFoundException()
            val keep = if (kind.multipleInputs) _state.value.inputs else emptyList()
            _state.update { it.copy(inputs = keep + added) }
            if (!kind.multipleInputs) added.firstOrNull()?.let { prepareSingle(it) }
        }
    }

    /** Password wali file ka pata yahin chal jaye, kaam dabane ke baad nahi. */
    private fun prepareSingle(input: ToolInput) {
        val file = temp.copy(input.uri, ".pdf", input.size).also(temps::add)
        synchronized(renderGuard) {
            closeRenderer()
            sourceFile = file
        }
        _state.update { it.copy(pageCount = 0, pageOrder = emptyList()) }
        if (kind == ToolKind.Unlock) return
        val pages = tools.pageCount(file)
        _state.update { it.copy(pageCount = pages, pageOrder = List(pages) { index -> index }) }
    }

    fun removeInput(index: Int) = _state.update { state ->
        state.copy(inputs = state.inputs.filterIndexed { i, _ -> i != index })
    }

    fun moveInput(index: Int, step: Int) = _state.update { it.copy(inputs = it.inputs.moved(index, step)) }

    fun movePage(index: Int, step: Int) = _state.update { it.copy(pageOrder = it.pageOrder.moved(index, step)) }

    fun removePage(index: Int) = _state.update { state ->
        state.copy(pageOrder = state.pageOrder.filterIndexed { i, _ -> i != index })
    }

    private fun <T> List<T>.moved(index: Int, step: Int): List<T> {
        val target = index + step
        if (target !in indices) return this
        return toMutableList().also { it.add(target, it.removeAt(index)) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun newScanFile(): File = temp.newFile(".jpg").also(temps::add)

    fun runMerge() = produce("merged", PDF_MIME) { out ->
        val files = _state.value.inputs.map { input -> temp.copy(input.uri, ".pdf", input.size).also(temps::add) }
        tools.merge(files, out)
        null
    }

    fun runPhotos() = produce("photos", PDF_MIME) { out ->
        val resolver = container.contentResolver
        tools.photosToPdf(_state.value.inputs.map { input -> { resolver.openInputStream(input.uri) } }, out)
        null
    }

    fun runSplit(from: Int, to: Int) = produce("pages-$from-$to", PDF_MIME) { out ->
        tools.rearrange(requireSource(), (from - 1 until to).toList(), out)
        null
    }

    fun runOrganize() = produce("organized", PDF_MIME) { out ->
        tools.rearrange(requireSource(), _state.value.pageOrder, out)
        null
    }

    fun runCompress(target: CompressTarget) = produce("small", PDF_MIME) { out ->
        val result = tools.compress(requireSource(), target.bytes, out)
        when {
            !result.hadImages -> R.string.compress_no_images
            target != CompressTarget.Smallest && !result.reachedTarget -> R.string.compress_missed
            else -> null
        }
    }

    fun runLock(password: String) = produce("locked", PDF_MIME) { out ->
        tools.lock(requireSource(), password, out)
        null
    }

    fun runUnlock(password: String) = produce("unlocked", PDF_MIME) { out ->
        tools.unlock(requireSource(), password, out)
        null
    }

    fun runText() = produce("text", TEXT_MIME, ".txt") { out ->
        if (tools.extractText(requireSource(), out)) null else R.string.text_none_found
    }

    private fun requireSource(): File = sourceFile ?: throw IOException("no input")

    private fun produce(suffix: String, mime: String, extension: String = ".pdf", work: (File) -> Int?) = launchWork {
        val work1 = temp.newFile(extension).also(temps::add)
        val note = work(work1)
        val first = _state.value.inputs.firstOrNull()
        val base = safeFileBase(first?.name)
        val named = File(work1.parentFile, "$base-$suffix$extension").also(temps::add)
        val out = if (work1.renameTo(named)) named else work1
        val before = _state.value.inputs.sumOf { it.size }
        AppLog.i(LogTag.OPEN) { "tool=$kind done size=${out.length()}" }
        _state.update { it.copy(result = ToolResult(out, "$base-$suffix$extension", mime, note, before)) }
    }

    /** Files par kaam background me. Har expected galti saaf message banti hai. */
    private fun launchWork(work: () -> Unit) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val error = withContext(container.dispatchers.io) { attempt(work) }
            _state.update { it.copy(busy = false, error = error) }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun attempt(work: () -> Unit): AppError? = try {
        work()
        null
    } catch (_: InvalidPasswordException) {
        if (kind == ToolKind.Unlock) AppError.WrongPassword else AppError.PdfLocked
    } catch (_: PdfRestricted) {
        AppError.PdfRestricted
    } catch (_: PdfHasNoPages) {
        AppError.Empty
    } catch (e: IOException) {
        e.toAppError()
    } catch (_: SecurityException) {
        AppError.FileGone
    } catch (_: RuntimeException) {
        // Kharab PDF par PDFBox kuch bhi phenk sakta hai: NullPointer, ClassCast, aur bhi.
        AppError.Corrupt
    } catch (_: StackOverflowError) {
        AppError.Corrupt
    } catch (_: OutOfMemoryError) {
        AppError.TooLarge
    }

    /**
     * Thumbnail ke liye page ki tasveer. [PdfRenderer] ek samay ek hi page dekh sakta hai.
     * Rasid jaisa bahut lamba page bhi memory me aa jaye, isliye lamba kinara simit hai.
     */
    suspend fun pageBitmap(index: Int, width: Int = THUMB_WIDTH): Bitmap? = render { pdf ->
        pdf.openPage(index).use { page ->
            val fullHeight = width.toLong() * page.height / page.width.coerceAtLeast(1)
            val shrink = minOf(1f, MAX_BITMAP_SIDE.toFloat() / maxOf(width.toLong(), fullHeight))
            val bitmap = blankBitmap((width * shrink).toInt(), (fullHeight * shrink).toInt())
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    private fun blankBitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.WHITE)
        }

    private suspend fun <T> render(block: (PdfRenderer) -> T): T? = withContext(container.dispatchers.io) {
        synchronized(renderGuard) {
            try {
                if (cleared) return@synchronized null
                val pdf = renderer ?: openRenderer() ?: return@synchronized null
                block(pdf)
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: IllegalStateException) {
                null
            } catch (_: OutOfMemoryError) {
                null
            }
        }
    }

    private fun openRenderer(): PdfRenderer? {
        val file = sourceFile ?: return null
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        rendererFd = fd
        return PdfRenderer(fd).also { renderer = it }
    }

    private fun closeRenderer() {
        try {
            renderer?.close()
            rendererFd?.close()
        } catch (_: IOException) {
            // Temp file hai, band na bhi ho to delete ho jayegi.
        }
        renderer = null
        rendererFd = null
    }

    override fun onCleared() {
        synchronized(renderGuard) {
            cleared = true
            closeRenderer()
        }
        temps.forEach(File::delete)
    }
}
