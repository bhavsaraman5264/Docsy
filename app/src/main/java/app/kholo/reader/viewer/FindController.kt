package app.kholo.reader.viewer

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TYPING_PAUSE_MS = 250L

/** Itne milne ke baad ruk jao: 1 GB ki file me "a" dhoondhne par memory nahi bharni. */
const val MAX_FIND_HITS = 2000

/** Har milan ki shuruaat, chhote-bade akshar ka fark chhod kar. */
fun String.findAll(query: String): List<Int> {
    if (query.isEmpty()) return emptyList()
    val found = ArrayList<Int>()
    var at = indexOf(query, 0, ignoreCase = true)
    while (at >= 0) {
        found += at
        at = indexOf(query, at + query.length, ignoreCase = true)
    }
    return found
}

data class FindState<H>(
    val open: Boolean = false,
    val query: String = "",
    val hits: List<H> = emptyList(),
    val current: Int = -1,
    val searching: Boolean = false,
) {
    val currentHit: H? get() = hits.getOrNull(current)
}

/**
 * Word, Excel aur TXT teeno ka search. Format ko sirf [scan] pata hai. Har naya akshar pichhli khoj
 * cancel karta hai, aur nateeje aate hi dikhte hain, poori file ka intezaar nahi.
 */
class FindController<H>(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val scan: suspend (query: String, emit: (List<H>) -> Unit) -> Unit,
) {
    private val _state = MutableStateFlow(FindState<H>())
    val state: StateFlow<FindState<H>> = _state.asStateFlow()
    private var job: Job? = null

    fun open() = _state.update { it.copy(open = true) }

    fun close() {
        job?.cancel()
        _state.value = FindState()
    }

    fun query(text: String) {
        _state.update { it.copy(query = text) }
        restart(pause = true)
    }

    /** File badal gayi, jaise doosri sheet khuli ya baaki document load hua. */
    fun rescan() {
        if (_state.value.open) restart(pause = false)
    }

    private fun restart(pause: Boolean) {
        job?.cancel()
        val text = _state.value.query
        if (text.isBlank()) {
            _state.update { it.copy(hits = emptyList(), current = -1, searching = false) }
            return
        }
        _state.update { it.copy(hits = emptyList(), current = -1, searching = true) }
        job = scope.launch(dispatcher) {
            if (pause) delay(TYPING_PAUSE_MS)
            scan(text) { found ->
                _state.update { state ->
                    val hits = (state.hits + found).take(MAX_FIND_HITS)
                    state.copy(hits = hits, current = if (state.current < 0 && hits.isNotEmpty()) 0 else state.current)
                }
            }
            _state.update { it.copy(searching = false) }
        }
    }

    fun step(delta: Int) = _state.update { state ->
        if (state.hits.isEmpty()) {
            state
        } else {
            state.copy(current = (state.current + delta).mod(state.hits.size))
        }
    }
}
