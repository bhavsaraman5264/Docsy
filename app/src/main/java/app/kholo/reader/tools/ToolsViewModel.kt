package app.kholo.reader.tools

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kholo.reader.convert.WordLocked
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
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParserException

/** Teen column ke grid me bade phone par bhi thumbnail dhundhla na lage. */
private const val THUMB_WIDTH = 240
private const val PHOTO_THUMB_SIDE = 256
private const val THUMB_CACHE_BYTES = 16 * 1024 * 1024

private const val POINTS_PER_INCH = 72
private const val PERCENT = 100
private const val MAX_BITMAP_SIDE = 4096

data class ToolInput(val uri: Uri, val name: String, val size: Long)

/** PDF to JPG jaise kaam me [files] me har page ki alag file hoti hai. Bina iske sirf [file]. */
data class ToolResult(
    val file: File,
    val name: String,
    val mime: String,
    val note: Int? = null,
    val before: Long = 0L,
    val files: List<File> = listOf(file),
) {
    val canOpen: Boolean get() = !mime.startsWith("image/")
    val totalSize: Long get() = files.sumOf(File::length)
    val typeLabel: String
        get() = when (mime) {
            PDF_MIME -> "PDF"
            DOCX_MIME -> "DOC"
            TEXT_MIME -> "TXT"
            else -> "JPG"
        }
}

data class ToolUiState(
    val inputs: List<ToolInput> = emptyList(),
    val pageOrder: List<Int> = emptyList(),
    val pageCount: Int = 0,
    val pageTurns: Map<Int, Int> = emptyMap(),
    val selectedPage: Int? = null,
    val busy: Boolean = false,
    val percent: Int? = null,
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

    /** Pipeline ke threads bhi isme likhte hain. */
    private val temps = CopyOnWriteArrayList<File>()

    /** Scroll me wapas aaya page dobara render nahi hota. Bitmap yahan se gaya to bhi screen use kar sakti hai. */
    private val thumbs = object : LruCache<Any, Bitmap>(THUMB_CACHE_BYTES) {
        override fun sizeOf(key: Any, value: Bitmap): Int = value.allocationByteCount
    }

    /** Ek samay ek hi kaam: render, band karna aur file badalna ek saath chale to PdfRenderer gir jata hai. */
    private val renderGuard = Any()
    private var cleared = false
    private var renderer: PdfRenderer? = null
    private var rendererFd: ParcelFileDescriptor? = null
    private var sourceFile: File? = null
    private var running: Job? = null

    init {
        if (kind != ToolKind.ShrinkPhoto) {
            viewModelScope.launch(container.dispatchers.io) {
                try {
                    tools.warmUp()
                } catch (_: IOException) {
                    // Sirf taiyari thi. Asli kaam apni galti khud batayega.
                }
            }
        }
    }

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
            _state.update { it.copy(inputs = (keep + added).take(kind.maxInputs)) }
            if (!kind.multipleInputs) added.firstOrNull()?.let { prepareSingle(it) }
        }
    }

    /** Password wali file ka pata yahin chal jaye, kaam dabane ke baad nahi. */
    private fun prepareSingle(input: ToolInput) {
        val file = temp.copy(input.uri, kind.input.suffix, input.size).also(temps::add)
        synchronized(renderGuard) {
            closeRenderer()
            thumbs.evictAll()
            sourceFile = file
        }
        _state.update { it.copy(pageCount = 0, pageOrder = emptyList(), pageTurns = emptyMap(), selectedPage = null) }
        if (kind == ToolKind.Unlock || kind.input != ToolInputType.Pdf) return
        val pages = tools.pageCount(file)
        _state.update { it.copy(pageCount = pages, pageOrder = List(pages) { index -> index }, selectedPage = 0) }
    }

    fun removeInput(index: Int) {
        _state.value.inputs.getOrNull(index)?.let { temp.deleteOwnCache(it.uri) }
        _state.update { state -> state.copy(inputs = state.inputs.filterIndexed { i, _ -> i != index }) }
    }

    fun moveInput(index: Int, step: Int) = _state.update { it.copy(inputs = it.inputs.moved(index, step)) }

    fun movePage(index: Int, step: Int) = _state.update { it.copy(pageOrder = it.pageOrder.moved(index, step)) }

    /** Hatane ke baad agla page chuna rehta hai, taaki lagatar kai page hataye ja saken. */
    fun removePage(index: Int) = _state.update { state ->
        val order = state.pageOrder.filterIndexed { i, _ -> i != index }
        state.copy(pageOrder = order, selectedPage = order.getOrNull(index) ?: order.lastOrNull())
    }

    fun selectPage(page: Int) = _state.update { it.copy(selectedPage = page) }

    fun rotatePage(page: Int) = _state.update { state ->
        val turn = ((state.pageTurns[page] ?: 0) + QUARTER_TURN) % FULL_TURN
        state.copy(pageTurns = state.pageTurns + (page to turn))
    }

    private fun <T> List<T>.moved(index: Int, step: Int): List<T> {
        val target = index + step
        if (target !in indices) return this
        return toMutableList().also { it.add(target, it.removeAt(index)) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun newScanFile(): File = temp.newFile(".jpg").also(temps::add)

    /** Scanner ki photos app ke cache me rehti hain. PDF ban jaye ya screen band ho, tab woh bhi jaani chahiye. */
    private fun deleteScannerCopies() {
        _state.value.inputs.forEach { temp.deleteOwnCache(it.uri) }
    }

    /**
     * Tool ka kaam chalata hai. Nateeja ek file ho to use asli naam milta hai, jaise `bill-small.pdf`.
     * Cancel ya galti par is kaam ki har file mit jati hai.
     */
    internal fun run(runner: ToolRunner) = launchWork {
        val made = CopyOnWriteArrayList<File>()
        val files = object : ToolFiles {
            override fun newFile(extension: String) = temp.newFile(extension).also(::keep).also(made::add)

            override fun copy(input: ToolInput, extension: String) =
                temp.copy(input.uri, extension, input.size).also(::keep).also(made::add)

            override fun track(file: File) {
                keep(file)
                made += file
            }
        }
        val snapshot = _state.value
        val base = kind.fixedName ?: safeFileBase(snapshot.inputs.firstOrNull()?.name)
        val job = ToolJob(snapshot, base, tools, container.contentResolver, sourceFile, files, ::pageImage)
        var finished = false
        val output = try {
            runner.run(job, ::step).also { finished = true }
        } finally {
            if (!finished) made.forEach(File::delete)
        }
        val single = output.files.singleOrNull()
        val result = if (single != null && output.name == null) {
            val tail = if (runner.suffix.isEmpty()) "" else "-${runner.suffix}"
            val named = job.named(single, "$base$tail${runner.extension}")
            val before = if (kind.comparesSize) snapshot.inputs.sumOf { it.size } else 0L
            ToolResult(named, named.name, runner.mime, output.note, before)
        } else {
            val first = output.files.firstOrNull() ?: throw IOException("nothing made")
            ToolResult(first, output.name ?: first.name, runner.mime, output.note, files = output.files)
        }
        AppLog.i(LogTag.OPEN) { "tool=$kind done size=${result.totalSize}" }
        _state.update { it.copy(result = result) }
    }

    private fun keep(file: File) {
        temps += file
    }

    /** Cancel dabane par agle page se pehle hi kaam rukta hai. */
    private fun step(done: Int, total: Int) {
        if (running?.isActive == false) throw CancellationException()
        val percent = done * PERCENT / total.coerceAtLeast(1)
        if (percent != _state.value.percent) _state.update { it.copy(percent = percent) }
    }

    fun cancelWork() {
        running?.cancel()
    }

    /** Files par kaam background me. Har expected galti saaf message banti hai. */
    private fun launchWork(work: () -> Unit) {
        _state.update { it.copy(busy = true, percent = null, error = null) }
        running = viewModelScope.launch {
            var error: AppError? = null
            try {
                error = withContext(container.dispatchers.io) { attempt(work) }
            } finally {
                // Cancel par bhi screen form par wapas aani chahiye.
                _state.update { it.copy(busy = false, percent = null, error = error) }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun attempt(work: () -> Unit): AppError? = try {
        work()
        null
    } catch (e: CancellationException) {
        // Yeh RuntimeException bhi hai. Neeche gaya to Cancel "file kharab hai" dikhega.
        throw e
    } catch (_: InvalidPasswordException) {
        if (kind == ToolKind.Unlock) AppError.WrongPassword else AppError.PdfLocked
    } catch (_: WordLocked) {
        AppError.WordLocked
    } catch (_: XmlPullParserException) {
        AppError.Corrupt
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
    suspend fun pageBitmap(index: Int): Bitmap? {
        val scope = currentCoroutineContext()
        return render { pdf ->
            // Tez scroll me kataar lambi ho jati hai. Jo page screen se ja chuka, use render nahi karna.
            scope.ensureActive()
            pdf.openPage(index).use { page -> drawPage(page, THUMB_WIDTH) }.also { thumbs.put(index, it) }
        }
    }

    fun cachedPage(index: Int): Bitmap? = thumbs[index]

    fun cachedPhoto(uri: Uri): Bitmap? = thumbs[uri]

    /** File list ki chhoti tasveer. Na padhi jaye to list me sirf naam dikhta hai. */
    suspend fun photoThumb(uri: Uri): Bitmap? = withContext(container.dispatchers.io) {
        try {
            val resolver = container.contentResolver
            loadPhoto({ resolver.openInputStream(uri) }, PHOTO_THUMB_SIDE)?.also { thumbs.put(uri, it) }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    /** Tool ke background kaam ke liye, asli size par. Yeh pehle se IO thread par chalta hai. */
    private fun pageImage(index: Int, dpi: Int): Bitmap? = renderNow { pdf ->
        pdf.openPage(index).use { page -> drawPage(page, page.width * dpi / POINTS_PER_INCH) }
    }

    private fun drawPage(page: PdfRenderer.Page, width: Int): Bitmap {
        val fullHeight = width.toLong() * page.height / page.width.coerceAtLeast(1)
        val shrink = minOf(1f, MAX_BITMAP_SIDE.toFloat() / maxOf(width.toLong(), fullHeight))
        val bitmap = blankBitmap((width * shrink).toInt(), (fullHeight * shrink).toInt())
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    private fun blankBitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.WHITE)
        }

    private suspend fun <T> render(block: (PdfRenderer) -> T): T? =
        withContext(container.dispatchers.io) { renderNow(block) }

    private fun <T> renderNow(block: (PdfRenderer) -> T): T? = synchronized(renderGuard) {
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
        deleteScannerCopies()
    }
}
