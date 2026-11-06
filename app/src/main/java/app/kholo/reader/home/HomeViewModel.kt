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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val STOP_TIMEOUT_MS = 5_000L

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val pendingCrash = MutableStateFlow(false)

    /** Settings se lautne par wahi tab khule. */
    var lastPage = 0

    // Swipe ke beech dono lists ek saath dikhti hain, isliye dono hamesha taiyar rehti hain.
    val state: StateFlow<HomeUiState> = combine(container.recents.files, pendingCrash) { files, crash ->
        HomeUiState(files, files.favoritesNewestFirst(), crash)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    init {
        viewModelScope.launch {
            val crash = withContext(container.dispatchers.io) { container.crashRecorder.pending() }
            pendingCrash.value = crash != null
        }
    }

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
