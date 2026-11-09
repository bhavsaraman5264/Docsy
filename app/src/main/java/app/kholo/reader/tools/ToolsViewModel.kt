package app.kholo.reader.tools

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kholo.reader.R
import app.kholo.reader.convert.DocumentLoader
import app.kholo.reader.convert.DocumentPdfWriter
import app.kholo.reader.convert.PdfToWord
import app.kholo.reader.convert.WordLocked
import app.kholo.reader.core.AppContainer
import app.kholo.reader.core.AppError
import app.kholo.reader.core.DocResult
import app.kholo.reader.core.file.COPY_BUFFER
import app.kholo.reader.core.file.SafeZip
import app.kholo.reader.core.file.toAppError
import app.kholo.reader.core.log.AppLog
import app.kholo.reader.core.log.LogTag
import app.kholo.reader.docx.DocBlock
import app.kholo.reader.docx.ParsedDocument
import app.kholo.reader.text.TextDecoder
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

/** Render aur JPEG saath chalte hain. Har page ki tasveer ~15 MB hai, isliye do se zyada nahi. */
private const val IMAGE_WORKERS = 2
private const val PDF_MIME = "application/pdf"
private const val TEXT_MIME = "text/plain"
private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
private const val JPEG_MIME = "image/jpeg"
private const val POINTS_PER_INCH = 72
private const val IMAGE_DPI = 200
private const val WORD_PICTURE_DPI = 150
private const val JPEG_QUALITY = 88
private const val PERCENT = 100
private const val MAX_TEXT_BYTES = 32L * 1024 * 1024
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
            _state.update { it.copy(inputs = keep + added) }
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

    fun removeInput(index: Int) = _state.update { state ->
        state.copy(inputs = state.inputs.filterIndexed { i, _ -> i != index })
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

    fun runMerge() = produce("merged", PDF_MIME) { out ->
        val inputs = _state.value.inputs
        val total = inputs.size * 2
        val files = inputs.mapIndexed { index, input ->
            step(index, total)
            temp.copy(input.uri, ".pdf", input.size).also(temps::add)
        }
        tools.merge(files, out) { done, _ -> step(inputs.size + done, total) }
        null
    }

    fun runPhotos() = produce(if (kind == ToolKind.Scan) "" else "photos", PDF_MIME) { out ->
        val resolver = container.contentResolver
        step(0, 1)
        tools.photosToPdf(_state.value.inputs.map { input -> { resolver.openInputStream(input.uri) } }, out, ::step)
        null
    }

    fun runSplit(after: Int) = launchWork {
        val parts = splitParts(_state.value.pageCount, after) ?: throw IOException("bad split")
        val base = safeFileBase(_state.value.inputs.firstOrNull()?.name)
        val made = parts.map { temp.newFile(".pdf").also(temps::add) }
        try {
            step(0, parts.size)
            tools.split(requireSource(), parts.map(IntRange::toList), made, ::step)
        } catch (e: CancellationException) {
            made.forEach(File::delete)
            throw e
        }
        val files = made.mapIndexed { index, file ->
            val named = File(file.parentFile, "$base-part-${index + 1}.pdf").also(temps::add)
            if (file.renameTo(named)) named else file
        }
        _state.update { it.copy(result = ToolResult(files.first(), "$base-split", PDF_MIME, files = files)) }
    }

    fun runExtract(from: Int, to: Int) = produce("pages-$from-$to", PDF_MIME) { out ->
        tools.rearrange(requireSource(), (from - 1 until to).toList(), out)
        null
    }

    fun runOrganize() = produce("organized", PDF_MIME) { out ->
        tools.rearrange(requireSource(), _state.value.pageOrder, out, _state.value.pageTurns)
        null
    }

    fun runCompress(target: CompressTarget) = produce("small", PDF_MIME) { out ->
        step(0, 1)
        val result = tools.compress(requireSource(), target.bytes, out, ::step)
        when {
            result.alreadySmall -> R.string.compress_already_small
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
        if (tools.unlock(requireSource(), password, out)) null else R.string.unlock_not_locked
    }

    fun runText() = produce("text", TEXT_MIME, ".txt") { out ->
        step(0, 1)
        if (tools.extractText(requireSource(), out, ::step)) null else R.string.text_none_found
    }

    fun runWordToPdf() = produce("", PDF_MIME) { out ->
        DocumentLoader(temp::newFile).load(requireSource()).use { document ->
            val lost = DocumentPdfWriter(document.images, ::step).write(document.parsed, out)
            if (lost) R.string.convert_some_skipped else null
        }
    }

    fun runTextToPdf() = produce("", PDF_MIME) { out ->
        val source = requireSource()
        if (source.length() > MAX_TEXT_BYTES) throw SafeZip.TooBig()
        val lines = TextDecoder.decode(source.readBytes()).lineSequence()
        val blocks = lines.map { DocBlock.Paragraph(AnnotatedString(it)) }.toList()
        DocumentPdfWriter(null, ::step).write(ParsedDocument(blocks), out)
        null
    }

    fun runPdfToWord() = produce("", DOCX_MIME, ".docx") { out ->
        val result = PdfToWord(tools) { temp.newFile(".xml").also(temps::add) }
            .convert(requireSource(), out, { pageImage(it, WORD_PICTURE_DPI) }, ::step)
        when {
            result.pictures > 0 -> R.string.word_some_pictures
            result.lostImages -> R.string.convert_some_skipped
            else -> null
        }
    }

    fun runShrinkPhoto(target: PhotoTarget) = produce("small", JPEG_MIME, ".jpg") { out ->
        val input = _state.value.inputs.firstOrNull() ?: throw IOException("no input")
        val resolver = container.contentResolver
        // Dobara JPEG banane se chhoti photo badi ho sakti hai, aur saaf bhi kam hoti hai.
        if (input.size in 1..target.bytes && resolver.getType(input.uri) == JPEG_MIME) {
            val source = resolver.openInputStream(input.uri) ?: throw FileNotFoundException()
            source.use { stream -> out.outputStream().use { stream.copyTo(it, COPY_BUFFER) } }
            R.string.compress_already_small
        } else {
            val reached = shrinkPhoto({ resolver.openInputStream(input.uri) }, target.bytes, out)
            if (reached) null else R.string.compress_missed
        }
    }

    fun runImages() = launchWork {
        val pages = _state.value.pageCount
        val base = safeFileBase(_state.value.inputs.firstOrNull()?.name)
        val files = mutableListOf<File>()
        try {
            step(0, pages)
            pipeline((0 until pages).toList(), IMAGE_WORKERS, { writePageImage(it, base) }) { index, file ->
                files += file
                step(index + 1, pages)
            }
        } catch (e: CancellationException) {
            files.forEach(File::delete)
            throw e
        }
        val name = files.singleOrNull()?.name ?: "$base-images"
        _state.update { it.copy(result = ToolResult(files.first(), name, JPEG_MIME, files = files)) }
    }

    private fun writePageImage(page: Int, base: String): File {
        val bitmap = pageImage(page, IMAGE_DPI) ?: throw IOException("unreadable page")
        val file = temp.newFile(".jpg").also(temps::add)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        bitmap.recycle()
        val named = File(file.parentFile, "$base-page-${page + 1}.jpg").also(temps::add)
        return if (file.renameTo(named)) named else file
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

    private fun requireSource(): File = sourceFile ?: throw IOException("no input")

    private fun produce(suffix: String, mime: String, extension: String = ".pdf", work: (File) -> Int?) = launchWork {
        val work1 = temp.newFile(extension).also(temps::add)
        val note = try {
            work(work1)
        } catch (e: CancellationException) {
            work1.delete()
            throw e
        }
        val first = _state.value.inputs.firstOrNull()
        // Camera ki photo ka naam temp file ka hota hai, woh user ko nahi dikhana.
        val base = if (kind == ToolKind.Scan) "scan" else safeFileBase(first?.name)
        val name = if (suffix.isEmpty()) "$base$extension" else "$base-$suffix$extension"
        val named = File(work1.parentFile, name).also(temps::add)
        val out = if (work1.renameTo(named)) named else work1
        val before = if (kind.comparesSize) _state.value.inputs.sumOf { it.size } else 0L
        AppLog.i(LogTag.OPEN) { "tool=$kind done size=${out.length()}" }
        _state.update { it.copy(result = ToolResult(out, name, mime, note, before)) }
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
    }
}
