package app.kholo.reader.pdf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.pdf.PdfDocument
import app.kholo.reader.core.AppContainer
import app.kholo.reader.core.log.AppLog
import app.kholo.reader.core.log.LogTag
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val SEARCH_DEBOUNCE_MS = 300L
private const val SEARCH_PAGE_CHUNK = 20
private const val MAX_HITS = 500
private const val FULL_TURN = 360
private const val QUARTER_TURN = 90

class PdfViewModel(
    private val document: PdfDocument,
    private val uri: String,
    start: ReadingState,
    private val container: AppContainer,
) : ViewModel() {
    private val _state = MutableStateFlow(
        PdfUiState(page = start.page, pageCount = document.pageCount, night = start.night, rotation = start.rotation),
    )
    val state: StateFlow<PdfUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    fun onPageChanged(page: Int) {
        if (page != _state.value.page) {
            _state.update { it.copy(page = page) }
            persist()
        }
    }

    fun onFitChanged(atFit: Boolean) {
        if (atFit != _state.value.atFit) _state.update { it.copy(atFit = atFit) }
    }

    fun toggleControls() = _state.update { it.copy(controlsVisible = !it.controlsVisible) }

    fun hideControls() {
        if (_state.value.search == null) _state.update { it.copy(controlsVisible = false) }
    }

    fun toggleNight() {
        _state.update { it.copy(night = !it.night) }
        persist()
    }

    fun rotate() {
        _state.update { it.copy(rotation = (it.rotation + QUARTER_TURN) % FULL_TURN) }
        persist()
    }

    fun showPageJump(show: Boolean) = _state.update { it.copy(showPageJump = show) }

    fun openSearch() = _state.update { it.copy(search = PdfSearchState(), controlsVisible = true) }

    fun closeSearch() {
        searchJob?.cancel()
        _state.update { it.copy(search = null) }
    }

    fun search(query: String) {
        searchJob?.cancel()
        _state.update { it.copy(search = PdfSearchState(query = query, searching = query.isNotBlank())) }
        if (query.isBlank()) return
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            runSearch(query)
        }
    }

    private suspend fun runSearch(query: String) {
        val found = mutableListOf<SearchHit>()
        var start = 0
        while (start < document.pageCount && found.size < MAX_HITS) {
            val end = minOf(start + SEARCH_PAGE_CHUNK, document.pageCount) - 1
            val results = document.searchDocument(query, start..end)
            for (i in 0 until results.size()) {
                val page = results.keyAt(i)
                results.valueAt(i).forEach { found += SearchHit(page, it.bounds) }
            }
            val snapshot = found.toList()
            _state.update { state ->
                val search = state.search ?: return@update state
                val current = if (search.current < 0 && snapshot.isNotEmpty()) 0 else search.current
                state.copy(search = search.copy(hits = snapshot, current = current))
            }
            start = end + 1
        }
        AppLog.d(LogTag.PDF) { "search done hits=${found.size}" }
        _state.update { state -> state.copy(search = state.search?.copy(searching = false)) }
    }

    fun nextHit(step: Int) = _state.update { state ->
        val search = state.search ?: return@update state
        if (search.hits.isEmpty()) return@update state
        val next = (search.current + step + search.hits.size) % search.hits.size
        state.copy(search = search.copy(current = next))
    }

    private fun persist() {
        val state = _state.value
        viewModelScope.launch {
            container.readingState.save(uri, ReadingState(state.page, state.rotation, state.night))
        }
    }
}
