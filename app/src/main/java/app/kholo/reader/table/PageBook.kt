package app.kholo.reader.table

import kotlin.collections.MutableMap.MutableEntry

/**
 * Grid ke pages ka hisaab: kaunse yaad hain, kaunse adhoore (import abhi chal raha hai) aur kaunse aa rahe hain.
 * Adhoora page bhi dikhta hai. Import aage badhne par sirf wahi dobara padha jata hai, poora cache nahi.
 * Sheet badalne par [generation] badalta hai, taaki purani sheet ka der se aaya page na chipke.
 */
internal class PageBook(private val capacity: Int) {
    private val pages = object : LinkedHashMap<Int, List<TableRow>>(capacity, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableEntry<Int, List<TableRow>>?): Boolean = size > capacity
    }
    private val partial = HashSet<Int>()
    private val loading = HashSet<Int>()

    var generation = 0
        private set

    operator fun get(page: Int): List<TableRow>? = pages[page]

    /** True ho to padhna shuru karo: na yaad hai, na pehle se aa raha hai. */
    fun startLoad(page: Int): Boolean = page !in pages && loading.add(page)

    /** False ka matlab yeh page purani sheet ka tha aur chhod diya gaya. */
    fun loaded(page: Int, rows: List<TableRow>, complete: Boolean, from: Int): Boolean {
        if (from != generation) return false
        loading.remove(page)
        pages[page] = rows
        if (complete) partial.remove(page) else partial.add(page)
        return true
    }

    /** Import aage badha: sirf adhoore pages bhoolo. Lautaye gaye pages dobara banne chahiye. */
    fun forgetPartial(): List<Int> {
        val forgotten = partial.toList()
        forgotten.forEach(pages::remove)
        partial.clear()
        return forgotten
    }

    fun clear() {
        generation++
        pages.clear()
        partial.clear()
        loading.clear()
    }

    private companion object {
        const val LOAD_FACTOR = 0.75f
    }
}
