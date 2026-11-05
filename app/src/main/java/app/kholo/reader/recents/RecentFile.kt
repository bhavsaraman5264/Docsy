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
    val favoritedAt: Long = 0L,
    /** Is URI ki access app restart ke baad bhi rahegi. WhatsApp ke URI temporary hote hain. */
    val keepsAccess: Boolean = false,
) {
    val identity: String get() = fileIdentity(uri, name, size)
}

/**
 * Ek hi file ko WhatsApp har baar naya URI deta hai, isliye pehchan naam aur size se hai.
 * Size pata na ho to sirf URI par bharosa kar sakte hain.
 */
fun fileIdentity(uri: String, name: String, size: Long): String = if (size > 0L) "$name|$size" else uri

private fun RecentFile.mergeOlder(older: RecentFile): RecentFile {
    val useOlderUri = older.keepsAccess && !keepsAccess
    return copy(
        uri = if (useOlderUri) older.uri else uri,
        keepsAccess = keepsAccess || older.keepsAccess,
        favorite = favorite || older.favorite,
        favoritedAt = maxOf(favoritedAt, older.favoritedAt),
    )
}

/** Har file ek hi baar, nayi kholi hui sabse upar. Favorite ka nishaan kabhi nahi khota. */
fun List<RecentFile>.deduped(): List<RecentFile> = sortedByDescending { it.openedAt }
    .groupBy { it.identity }
    .values
    .map { copies -> copies.drop(1).fold(copies.first()) { newest, older -> newest.mergeOlder(older) } }

fun List<RecentFile>.upsert(file: RecentFile): List<RecentFile> {
    val unique = (this + file).deduped()
    val kept = unique.filter { it.favorite } + unique.filterNot { it.favorite }.take(MAX_RECENTS)
    return kept.sortedByDescending { it.openedAt }
}

fun List<RecentFile>.toggleFavorite(uri: String, now: Long): List<RecentFile> = map { file ->
    when {
        file.uri != uri -> file
        file.favorite -> file.copy(favorite = false, favoritedAt = 0L)
        else -> file.copy(favorite = true, favoritedAt = now)
    }
}

/** Favorites tab: jo sabse baad me star hui, wo sabse upar. */
fun List<RecentFile>.favoritesNewestFirst(): List<RecentFile> =
    filter { it.favorite }.sortedByDescending { it.favoritedAt }

internal fun List<RecentFile>.toJson(): String = JSONArray().also { array ->
    forEach { file ->
        array.put(
            JSONObject()
                .put("uri", file.uri)
                .put("name", file.name)
                .put("size", file.size)
                .put("type", file.typeLabel)
                .put("openedAt", file.openedAt)
                .put("favorite", file.favorite)
                .put("favoritedAt", file.favoritedAt)
                .put("keepsAccess", file.keepsAccess),
        )
    }
}.toString()

/** Purani saved list me duplicate ho sakte hain, isliye padhte waqt bhi saaf karte hain. */
internal fun recentsFromJson(json: String?): List<RecentFile> {
    if (json.isNullOrEmpty()) return emptyList()
    val array = JSONArray(json)
    return List(array.length()) { index ->
        val item = array.getJSONObject(index)
        val openedAt = item.getLong("openedAt")
        val favorite = item.getBoolean("favorite")
        RecentFile(
            uri = item.getString("uri"),
            name = item.getString("name"),
            size = item.getLong("size"),
            typeLabel = item.getString("type"),
            openedAt = openedAt,
            favorite = favorite,
            favoritedAt = item.optLong("favoritedAt", if (favorite) openedAt else 0L),
            keepsAccess = item.optBoolean("keepsAccess", false),
        )
    }.deduped()
}
