package app.kholo.reader.text

import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kholo.reader.core.AppContainer
import app.kholo.reader.core.LoadState
import app.kholo.reader.core.PageStamps
import app.kholo.reader.core.file.readUpTo
import app.kholo.reader.core.file.toAppError
import app.kholo.reader.viewer.FindController
import app.kholo.reader.viewer.MAX_FIND_HITS
import app.kholo.reader.viewer.findAll
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SAMPLE_BYTES = 64 * 1024
private const val CACHED_CHUNKS = 64
private const val MULTIBYTE_TAIL = 4

object TextDecoder {
    fun decode(bytes: ByteArray): String {
        val (charset, skip) = charsetOf(bytes)
        return String(bytes, skip, bytes.size - skip, charset)
    }

    fun charsetOf(bytes: ByteArray): Pair<Charset, Int> = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
            Charsets.UTF_8 to 3
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE to 2
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE to 2
        isUtf8(bytes) -> Charsets.UTF_8 to 0
        else -> Charset.forName("windows-1252") to 0
    }

    /** Sample beech me kat sakta hai, isliye aakhir ke kuch bytes par galti nahi maante. */
    private fun isUtf8(bytes: ByteArray): Boolean {
        val checked = if (bytes.size >= SAMPLE_BYTES) bytes.copyOf(bytes.size - MULTIBYTE_TAIL) else bytes
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(checked))
            true
        } catch (_: CharacterCodingException) {
            bytes.size >= SAMPLE_BYTES
        }
    }
}

/** Tukde ke andar milan ki jagah. */
data class TextHit(val chunk: Int, val start: Int, val end: Int)

class TextViewModel(private val uri: Uri, private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<TextPager>>(LoadState.Loading)
    val state: StateFlow<LoadState<TextPager>> = _state.asStateFlow()

    private val stamps = PageStamps()

    private val chunks = LruCache<Int, String>(CACHED_CHUNKS)
    private val loading = mutableSetOf<Int>()
    private var pager: TextPager? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var tempFile: File? = null

    val find = FindController<TextHit>(viewModelScope, container.dispatchers.io, ::scan)

    init {
        viewModelScope.launch {
            _state.value = try {
                withContext(container.dispatchers.io) { LoadState.Ready(open()) }
            } catch (e: IOException) {
                LoadState.Failed(e.toAppError())
            } catch (e: SecurityException) {
                LoadState.Failed(e.toAppError())
            }
        }
    }

    private fun open(): TextPager {
        val resolver = container.contentResolver
        val sample = resolver.openInputStream(uri)?.use { it.readUpTo(SAMPLE_BYTES) }
            ?: throw java.io.FileNotFoundException()
        val (charset, skip) = TextDecoder.charsetOf(sample)
        val direct = resolver.openFileDescriptor(uri, "r") ?: throw java.io.FileNotFoundException()
        val utf16 = charset == Charsets.UTF_16LE || charset == Charsets.UTF_16BE
        // Pipe ya UTF-16 me seek se kaam nahi chalta, tab ek baar private copy.
        if (direct.statSize >= 0 && !utf16) {
            descriptor = direct
            return TextPager(FileInputStream(direct.fileDescriptor), direct.statSize, charset, skip.toLong()).also {
                pager =
                    it
            }
        }
        direct.close()
        val file = container.tempFiles.copy(uri, ".txt", null)
        tempFile = file
        val data = if (utf16) convertToUtf8(file, charset, skip) else file
        return TextPager(
            FileInputStream(data),
            data.length(),
            if (utf16) Charsets.UTF_8 else charset,
            if (utf16) 0L else skip.toLong(),
        )
            .also { pager = it }
    }

    private fun convertToUtf8(file: File, charset: Charset, skip: Int): File {
        val converted = container.tempFiles.newFile(".txt")
        file.inputStream().use { stream ->
            stream.skip(skip.toLong())
            stream.reader(charset).use { reader -> converted.writer(Charsets.UTF_8).use { reader.copyTo(it) } }
        }
        file.delete()
        tempFile = converted
        return converted
    }

    /** Tukde ek-ek karke padhte hain, isliye 1 GB ki file bhi memory me nahi aati. */
    private suspend fun scan(query: String, emit: (List<TextHit>) -> Unit) {
        val source = pager ?: return
        var found = 0
        try {
            for (index in 0 until source.chunkCount) {
                if (found >= MAX_FIND_HITS) break
                coroutineContext.ensureActive()
                val hits = source.chunk(index).findAll(query).map { TextHit(index, it, it + query.length) }
                if (hits.isNotEmpty()) emit(hits)
                found += hits.size
            }
        } catch (_: IOException) {
            // Jitna mila utna dikhao. Viewer band hone par bhi yahi hota hai.
        }
    }

    /** Tukde ka nishan: composition me padho. Sirf iska text aane par badalta hai. */
    fun stamp(index: Int): Int = stamps.read(index)

    fun chunk(index: Int): String? {
        val cached = chunks.get(index)
        if (cached == null) load(index)
        return cached
    }

    private fun load(index: Int) {
        val source = pager
        if (source == null || !loading.add(index)) return
        viewModelScope.launch {
            val text = withContext(container.dispatchers.io) {
                try {
                    source.chunk(index)
                } catch (_: IOException) {
                    ""
                }
            }
            loading.remove(index)
            chunks.put(index, text)
            stamps.bump(index)
        }
    }

    override fun onCleared() {
        try {
            pager?.close()
            descriptor?.close()
        } catch (_: IOException) {
            // Band ho hi raha hai.
        }
        tempFile?.delete()
    }
}
