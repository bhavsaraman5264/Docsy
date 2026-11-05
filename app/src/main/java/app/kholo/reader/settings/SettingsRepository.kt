package app.kholo.reader.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val THEME_KEY = stringPreferencesKey("theme")
private val WORD_PAGE_VIEW_KEY = booleanPreferencesKey("word_page_view")

enum class ThemeMode { System, Light, Dark }

class SettingsRepository(private val store: DataStore<Preferences>) {
    val theme: Flow<ThemeMode> = store.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[THEME_KEY] } ?: ThemeMode.Light
    }

    /** Word file page jaisi khule ya reading view me. Default page. */
    val wordPageView: Flow<Boolean> = store.data.map { it[WORD_PAGE_VIEW_KEY] ?: true }

    suspend fun setWordPageView(enabled: Boolean) {
        store.edit { it[WORD_PAGE_VIEW_KEY] = enabled }
    }

    suspend fun setTheme(mode: ThemeMode) {
        store.edit { it[THEME_KEY] = mode.name }
    }
}
