package app.kholo.reader.docx

import androidx.compose.runtime.staticCompositionLocalOf
import app.kholo.reader.ui.components.FindRange
import app.kholo.reader.viewer.findAll
import java.util.IdentityHashMap

/** Word ya PowerPoint me ek milan. `owner` wahi Paragraph ya DocCell hai jisme yeh text hai. */
class DocHit(val block: Int, val owner: Any, val start: Int, val end: Int)

fun blockHits(index: Int, block: DocBlock, query: String): List<DocHit> = when (block) {
    is DocBlock.Paragraph -> textHits(index, block, block.text.text, query)
    is DocBlock.Table -> block.rows.flatMap { row -> row.flatMap { textHits(index, it, it.text, query) } }
    else -> emptyList()
}

private fun textHits(index: Int, owner: Any, text: String, query: String): List<DocHit> =
    text.findAll(query).map { DocHit(index, owner, it, it + query.length) }

/** Rows ko apne text ke highlight yahin se milte hain. Ek jaise do paragraph alag rahen, isliye identity. */
class DocMarks(hits: List<DocHit>, private val current: DocHit?) {
    private val byOwner = IdentityHashMap<Any, MutableList<DocHit>>().also { map ->
        hits.forEach { hit -> map.getOrPut(hit.owner) { ArrayList() } += hit }
    }

    fun ranges(owner: Any): List<FindRange> =
        byOwner[owner].orEmpty().map { FindRange(it.start, it.end, it === current) }
}

val LocalDocMarks = staticCompositionLocalOf { DocMarks(emptyList(), null) }

/** Page view me milan wala page dhoondhne ke liye. */
fun PageItem.owns(owner: Any): Boolean = when (this) {
    is PageItem.Paragraph -> block === owner
    is PageItem.TableRow -> cells.any { it === owner }
    is PageItem.Picture -> false
}
