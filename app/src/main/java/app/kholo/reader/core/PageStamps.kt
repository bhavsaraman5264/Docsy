package app.kholo.reader.core

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf

/**
 * Har page ya tukde ka apna nishan. Naya data aane par sirf usi page ki rows dobara banti hain.
 * Ek hi global counter se har load par poori screen dobara banti thi, aur scroll atakta tha.
 * Sirf main thread se.
 */
class PageStamps {
    private val stamps = HashMap<Int, MutableIntState>()

    /** Composition me padho, taaki yeh page badalne par wahi row dobara bane. */
    fun read(page: Int): Int = stamps.getOrPut(page) { mutableIntStateOf(0) }.intValue

    fun bump(page: Int) {
        stamps.getOrPut(page) { mutableIntStateOf(0) }.intValue++
    }

    fun bumpAll() {
        stamps.values.forEach { it.intValue++ }
    }
}
