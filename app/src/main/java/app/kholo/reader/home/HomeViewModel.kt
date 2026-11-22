package app.kholo.reader.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kholo.reader.core.AppContainer
import app.kholo.reader.recents.RecentFile
import app.kholo.reader.recents.favoritesNewestFirst
import app.kholo.reader.recents.starred
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val STOP_TIMEOUT_MS = 5_000L

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val pendingCrash = MutableStateFlow(false)

    /** Settings se lautne par wahi tab khule. */
    var lastPage = 0

    /** Star dabate hi badle: uri se chaha gaya favorite, jab tak store me likha na jaye. */
    private val pendingStars = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    // Swipe ke beech dono lists ek saath dikhti hain, isliye dono hamesha taiyar rehti hain.
    // Sorting background me, taaki star ya tab badalte waqt main thread khaali rahe.
    val state: StateFlow<HomeUiState> =
        combine(container.recents.files, pendingStars, pendingCrash) { saved, stars, crash ->
            val files = if (stars.isEmpty()) saved else saved.map { stars[it.uri]?.let(it::starred) ?: it }
            HomeUiState(files, files.favoritesNewestFirst(), crash)
        }.flowOn(container.dispatchers.default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    init {
        // Store me likha ja chuka star ab pending nahi. Pehle hata dete to star ek pal ke liye purana dikhta.
        viewModelScope.launch {
            container.recents.files.collect { saved ->
                pendingStars.update { stars ->
                    stars.filterNot { (uri, wanted) -> saved.any { it.uri == uri && it.favorite == wanted } }
                }
            }
        }
        viewModelScope.launch {
            val crash = withContext(container.dispatchers.io) { container.crashRecorder.pending() }
            pendingCrash.value = crash != null
        }
    }

    fun toggleFavorite(uri: String) {
        val now = state.value.recents.firstOrNull { it.uri == uri }?.favorite ?: return
        pendingStars.update { it + (uri to !now) }
        viewModelScope.launch {
            try {
                container.recents.toggleFavorite(uri)
            } catch (e: IOException) {
                // Likha nahi gaya: star wapas waisa hi jaisa store me hai.
                pendingStars.update { it - uri }
                throw e
            }
        }
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
