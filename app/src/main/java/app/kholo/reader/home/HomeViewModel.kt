package app.kholo.reader.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kholo.reader.core.AppContainer
import app.kholo.reader.recents.RecentFile
import app.kholo.reader.recents.favoritesNewestFirst
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val STOP_TIMEOUT_MS = 5_000L

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val tab = MutableStateFlow(HomeTab.Recent)
    private val pendingCrash = MutableStateFlow(false)

    val state: StateFlow<HomeUiState> = combine(container.recents.files, tab, pendingCrash) { files, tab, crash ->
        val shown = if (tab == HomeTab.Favorites) files.favoritesNewestFirst() else files
        HomeUiState(tab, shown, crash)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    init {
        viewModelScope.launch {
            val crash = withContext(container.dispatchers.io) { container.crashRecorder.pending() }
            pendingCrash.value = crash != null
        }
    }

    fun selectTab(selected: HomeTab) = tab.update { selected }

    fun toggleFavorite(uri: String) {
        viewModelScope.launch { container.recents.toggleFavorite(uri) }
    }

    fun remove(file: RecentFile) {
        viewModelScope.launch {
            container.recents.remove(file.uri)
            container.readingState.clear(file.identity)
            withContext(container.dispatchers.io) { container.uriReader.releaseAccess(Uri.parse(file.uri)) }
        }
    }

    fun dismissCrash() {
        pendingCrash.value = false
        viewModelScope.launch(container.dispatchers.io) { container.crashRecorder.clear() }
    }

    fun acknowledgeCrash() {
        pendingCrash.value = false
    }
}
