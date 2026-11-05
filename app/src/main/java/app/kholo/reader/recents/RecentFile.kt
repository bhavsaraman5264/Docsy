package app.kholo.reader.recents

import org.json.JSONArray
import org.json.JSONObject

private const val MAX_RECENTS = 100

data class RecentFile(
    val uri: String,
    val name: String,
    val size: Long,
    val typeLabel: String,
    val openedAt: Long,
    val favorite: Boolean = false,
)

/** Nayi file upar aati hai. Pehle se favorite ho to favorite hi rehti hai. */
fun List<RecentFile>.upsert(file: RecentFile): List<RecentFile> {
    val existing = firstOrNull { it.uri == file.uri }
    val merged = file.copy(favorite = existing?.favorite ?: file.favorite)
    val rest = filterNot { it.uri == file.uri }
    val kept = rest.filter { it.favorite } + rest.filterNot { it.favorite }.take(MAX_RECENTS)
    return (listOf(merged) + kept).sortedByDescending { it.openedAt }
}

fun List<RecentFile>.toggleFavorite(uri: String): List<RecentFile> =
    map { if (it.uri == uri) it.copy(favorite = !it.favorite) else it }

internal fun List<RecentFile>.toJson(): String = JSONArray().also { array ->
    forEach { file ->
        array.put(
            JSONObject()
                .put("uri", file.uri)
                .put("name", file.name)
                .put("size", file.size)
                .put("type", file.typeLabel)
                .put("openedAt", file.openedAt)
                .put("favorite", file.favorite),
        )
    }
}.toString()

internal fun recentsFromJson(json: String?): List<RecentFile> {
    if (json.isNullOrEmpty()) return emptyList()
    val array = JSONArray(json)
    return List(array.length()) { index ->
        val item = array.getJSONObject(index)
        RecentFile(
            uri = item.getString("uri"),
            name = item.getString("name"),
            size = item.getLong("size"),
            typeLabel = item.getString("type"),
            openedAt = item.getLong("openedAt"),
            favorite = item.getBoolean("favorite"),
        )
    }
}
