package app.kholo.reader.recents

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val RECENTS_KEY = stringPreferencesKey("recents")

class RecentsRepository(private val store: DataStore<Preferences>) {
    val files: Flow<List<RecentFile>> = store.data.map { recentsFromJson(it[RECENTS_KEY]) }

    suspend fun record(file: RecentFile) = update { it.upsert(file) }

    suspend fun remove(uri: String) = update { list -> list.filterNot { it.uri == uri } }

    suspend fun toggleFavorite(uri: String) = update { it.toggleFavorite(uri) }

    private suspend fun update(change: (List<RecentFile>) -> List<RecentFile>) {
        store.edit { it[RECENTS_KEY] = change(recentsFromJson(it[RECENTS_KEY])).toJson() }
    }
}
