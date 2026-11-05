package app.kholo.reader.text

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kholo.reader.core.AppContainer
import app.kholo.reader.core.LoadState
import app.kholo.reader.core.file.readUpTo
import app.kholo.reader.core.file.toAppError
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_BYTES = 16 * 1024 * 1024
private const val LINES_PER_CHUNK = 100

class TextDocument(val chunks: List<String>, val truncated: Boolean)

object TextDecoder {
    fun decode(bytes: ByteArray): String {
        val (charset, skip) = charsetOf(bytes)
        return String(bytes, skip, bytes.size - skip, charset)
    }

    private fun charsetOf(bytes: ByteArray): Pair<Charset, Int> = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
            Charsets.UTF_8 to 3
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE to 2
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE to 2
        isUtf8(bytes) -> Charsets.UTF_8 to 0
        else -> Charset.forName("windows-1252") to 0
    }

    private fun isUtf8(bytes: ByteArray): Boolean = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes))
        true
    } catch (_: CharacterCodingException) {
        false
    }

    fun chunks(text: String): List<String> =
        text.lineSequence().chunked(LINES_PER_CHUNK).map { it.joinToString("\n") }.toList()
}

class TextViewModel(private val uri: Uri, private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<TextDocument>>(LoadState.Loading)
    val state: StateFlow<LoadState<TextDocument>> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = try {
                withContext(container.dispatchers.io) { LoadState.Ready(load()) }
            } catch (e: IOException) {
                LoadState.Failed(e.toAppError())
            } catch (e: SecurityException) {
                LoadState.Failed(e.toAppError())
            }
        }
    }

    private fun load(): TextDocument {
        val stream = container.contentResolver.openInputStream(uri) ?: throw java.io.FileNotFoundException()
        return stream.use {
            val bytes = it.readUpTo(MAX_BYTES + 1)
            val truncated = bytes.size > MAX_BYTES
            val data = if (truncated) bytes.copyOf(MAX_BYTES) else bytes
            TextDocument(TextDecoder.chunks(TextDecoder.decode(data)), truncated)
        }
    }
}
