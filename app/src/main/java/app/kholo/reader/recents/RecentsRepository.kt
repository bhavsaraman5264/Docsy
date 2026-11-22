package app.kholo.reader.recents

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

private val RECENTS_KEY = stringPreferencesKey("recents")

class RecentsRepository(private val store: DataStore<Preferences>) {
    /**
     * Yahi store settings aur padhne ki jagah bhi rakhta hai. Pehle unke har badlav par, jaise file band karke
     * Home par lautna, poori list main thread par dobara parse hoti thi aur Home atak jata tha.
     * Ab sirf recent badalne par, aur parse background me.
     */
    val files: Flow<List<RecentFile>> = store.data
        .map { it[RECENTS_KEY] }
        .distinctUntilChanged()
        .map(::recentsFromJson)
        .flowOn(Dispatchers.Default)

    suspend fun record(file: RecentFile) = update { it.upsert(file) }

    suspend fun remove(uri: String) = update { list -> list.filterNot { it.uri == uri } }

    suspend fun toggleFavorite(uri: String) = update { it.toggleFavorite(uri, System.currentTimeMillis()) }

    /** Jo file ab nahi khulti wo recent se hat jaye, par user ka favorite chupchaap na mite. */
    suspend fun removeUnavailable(uri: String) = update { list ->
        list.filterNot { it.uri == uri && !it.favorite }
    }

    private suspend fun update(change: (List<RecentFile>) -> List<RecentFile>) {
        store.edit { it[RECENTS_KEY] = change(recentsFromJson(it[RECENTS_KEY])).toJson() }
    }
}
