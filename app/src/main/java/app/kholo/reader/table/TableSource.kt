package app.kholo.reader.table

import java.io.Closeable
import kotlinx.coroutines.flow.StateFlow

data class TableInfo(
    val header: List<String> = emptyList(),
    val rowCount: Int = 0,
    val columnCount: Int = 0,
    val finished: Boolean = false,
    val failed: Boolean = false,
)

/** Grid ko format ka pata nahi: CSV aur XLSX dono yahi dete hain. Rows hamesha on-demand aati hain. */
interface TableSource : Closeable {
    val info: StateFlow<TableInfo>
    val sheets: List<String> get() = emptyList()

    fun selectSheet(index: Int) = Unit

    /** Row ke saath uska dikhne wala number, jo XLSX me asli Excel row hota hai. */
    fun rows(start: Int, count: Int): List<TableRow>
}

class TableRow(val number: Int, val cells: List<String>)
