package app.kholo.reader.home

import app.kholo.reader.recents.RecentFile

enum class HomeTab { Recent, Favorites, Tools }

data class HomeUiState(
    val recents: List<RecentFile> = emptyList(),
    val favorites: List<RecentFile> = emptyList(),
    val pendingCrash: Boolean = false,
)
