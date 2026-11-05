package app.kholo.reader.home

import app.kholo.reader.recents.RecentFile

enum class HomeTab { Recent, Favorites }

data class HomeUiState(
    val tab: HomeTab = HomeTab.Recent,
    val files: List<RecentFile> = emptyList(),
    val pendingCrash: Boolean = false,
)
