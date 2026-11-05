package app.kholo.reader.pdf

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.security.MessageDigest
import kotlinx.coroutines.flow.first

data class ReadingState(val page: Int = 0, val rotation: Int = 0, val night: Boolean = false)

/** Last page aur rotate har file ke liye. Key URI ka hash hai, taaki yahan file ka naam na rahe. */
class ReadingStateStore(private val store: DataStore<Preferences>) {
    suspend fun load(uri: String): ReadingState {
        val saved = store.data.first()[keyFor(uri)] ?: return ReadingState()
        val parts = saved.split(':')
        return ReadingState(
            page = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            rotation = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            night = parts.getOrNull(2) == "1",
        )
    }

    suspend fun save(uri: String, state: ReadingState) {
        val night = if (state.night) "1" else "0"
        store.edit { it[keyFor(uri)] = "${state.page}:${state.rotation}:$night" }
    }

    suspend fun clear(uri: String) {
        store.edit { it.remove(keyFor(uri)) }
    }

    private fun keyFor(uri: String): Preferences.Key<String> {
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray())
        return stringPreferencesKey("read_" + digest.joinToString("") { "%02x".format(it) })
    }
}
