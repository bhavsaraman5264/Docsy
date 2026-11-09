package app.kholo.reader.table

import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kholo.reader.core.AppContainer
import app.kholo.reader.core.AppError
import app.kholo.reader.core.LoadState
import app.kholo.reader.core.PageStamps
import app.kholo.reader.core.file.SafeZip
import app.kholo.reader.core.file.readUpTo
import app.kholo.reader.core.file.toAppError
import app.kholo.reader.office.OfficeContent
import app.kholo.reader.open.DocumentType
import app.kholo.reader.table.csv.CsvIndexSource
import app.kholo.reader.table.csv.CsvSniffer
import app.kholo.reader.table.xls.XlsSource
import app.kholo.reader.table.xls.XlsWorkbook
import app.kholo.reader.table.xlsx.SharedStringsFile
import app.kholo.reader.table.xlsx.XlsxPackage
import app.kholo.reader.table.xlsx.XlsxSqliteSource
import app.kholo.reader.table.xlsx.XlsxStyles
import app.kholo.reader.viewer.FindController
import app.kholo.reader.viewer.MAX_FIND_HITS
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParserException

const val PAGE_ROWS = 128
private const val CACHED_PAGES = 48
private const val SAMPLE_BYTES = 64 * 1024

/** Sheet ki row ka position (Excel ka row number nahi) aur 0-based column. */
data class GridHit(val row: Int, val column: Int)

class GridViewModel(
    private val uri: Uri,
    private val type: DocumentType,
    private val size: Long?,
    private val container: AppContainer,
    private val content: OfficeContent? = null,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<TableSource>>(LoadState.Loading)
    val state: StateFlow<LoadState<TableSource>> = _state.asStateFlow()

    private val io = CoroutineScope(SupervisorJob() + container.dispatchers.io)
    private val book = PageBook(CACHED_PAGES)
    private val stamps = PageStamps()
    private var sheetStamp by mutableIntStateOf(0)
    private var tempFile: File? = null
    private var source: TableSource? = null

    val find = FindController<GridHit>(viewModelScope, container.dispatchers.io, ::scan)

    init {
        viewModelScope.launch {
            _state.value = try {
                withContext(container.dispatchers.io) { LoadState.Ready(open()) }
            } catch (e: IOException) {
                LoadState.Failed(e.toAppError())
            } catch (_: SecurityException) {
                LoadState.Failed(AppError.NeedsAccess)
            } catch (_: XmlPullParserException) {
                LoadState.Failed(AppError.Corrupt)
            } catch (_: IllegalArgumentException) {
                LoadState.Failed(AppError.Corrupt)
            } catch (_: IndexOutOfBoundsException) {
                LoadState.Failed(AppError.Corrupt)
            } catch (_: SQLiteException) {
                LoadState.Failed(AppError.NoSpace)
            }
        }
    }

    private fun open(): TableSource {
        val opened = if (content is OfficeContent.Sheet) {
            XlsSource(XlsWorkbook(content.workbook))
        } else {
            openFile()
        }
        source = opened
        return opened
    }

    private fun openFile(): TableSource {
        val suffix = if (type == DocumentType.Xlsx) ".xlsx" else ".csv"
        // Password wali file pehle hi khul kar private cache me hai, aur use ViewerViewModel mitata hai.
        val file = (content as? OfficeContent.Package)?.file
            ?: container.tempFiles.copy(uri, suffix, size).also { tempFile = it }
        return if (type == DocumentType.Xlsx) openXlsx(file) else openCsv(file)
    }

    /** Sirf khuli sheet me. Rows pages me aati hain, isliye badi file bhi memory me nahi aati. */
    private suspend fun scan(query: String, emit: (List<GridHit>) -> Unit) {
        val table = source ?: return
        val total = table.info.first { it.finished }.rowCount
        var start = 0
        var found = 0
        try {
            while (start < total && found < MAX_FIND_HITS) {
                coroutineContext.ensureActive()
                val hits = ArrayList<GridHit>()
                table.rows(start, PAGE_ROWS).forEachIndexed { offset, row ->
                    row.cells.forEachIndexed { column, cell ->
                        if (cell.contains(query, ignoreCase = true)) hits += GridHit(start + offset, column)
                    }
                }
                if (hits.isNotEmpty()) emit(hits)
                found += hits.size
                start += PAGE_ROWS
            }
        } catch (_: IOException) {
            // Jitna mila utna dikhao, kharab hisse par search nahi rukna chahiye.
        } catch (_: SQLiteException) {
            // Viewer band ho raha hai.
        } catch (_: IllegalStateException) {
            // Viewer band ho raha hai.
        }
    }

    private fun openCsv(file: File): TableSource {
        val sample = file.inputStream().use { it.readUpTo(SAMPLE_BYTES) }
        var format = CsvSniffer.sniff(sample)
        var data = file
        if (format.utf16) {
            // UTF-16 ko ek baar UTF-8 me badal lo, taaki byte indexer ek hi tarah kaam kare.
            val converted = container.tempFiles.newFile(".csv")
            file.inputStream().use { stream ->
                stream.skip(format.start)
                stream.reader(format.charset).use { reader ->
                    converted.writer(Charsets.UTF_8).use { reader.copyTo(it) }
                }
            }
            file.delete()
            tempFile = converted
            data = converted
            format = CsvSniffer.sniff(data.inputStream().use { it.readUpTo(SAMPLE_BYTES) })
        }
        return CsvIndexSource(data, format, io)
    }

    private fun openXlsx(file: File): TableSource {
        val zip = SafeZip(file)
        val workbook = XlsxPackage.read(zip)
        val styles = XlsxStyles.read(zip)
        val strings = SharedStringsFile.read(zip, container.tempFiles.newFile(".bin"))
        val db = container.tempFiles.newFile(".db")
        return XlsxSqliteSource(zip, workbook, strings, styles, db, io)
    }

    /** Row ka nishan: composition me padho. Sirf iske page ka data aane par badalta hai. */
    fun stamp(index: Int): Long = (sheetStamp.toLong() shl Int.SIZE_BITS) or stamps.read(index / PAGE_ROWS).toLong()

    fun row(index: Int): TableRow? {
        val page = index / PAGE_ROWS
        val rows = book[page]
        if (rows == null) {
            load(page)
            return null
        }
        load(page + 1)
        return rows.getOrNull(index % PAGE_ROWS)
    }

    private fun load(page: Int) {
        val table = source ?: return
        if (!book.startLoad(page)) return
        val generation = book.generation
        io.launch {
            val rows = try {
                table.rows(page * PAGE_ROWS, PAGE_ROWS)
            } catch (_: IOException) {
                emptyList()
            } catch (_: IllegalStateException) {
                emptyList()
            } catch (_: SQLiteException) {
                emptyList()
            }
            withContext(container.dispatchers.main) {
                val complete = rows.size == PAGE_ROWS || table.info.value.finished
                if (book.loaded(page, rows, complete, generation)) stamps.bump(page)
            }
        }
    }

    /** Import chalte waqt sirf adhoore pages dobara padhne hain. Poore pages wahi rehte hain. */
    fun refresh() {
        book.forgetPartial().forEach(stamps::bump)
    }

    fun selectSheet(index: Int) {
        io.launch {
            source?.selectSheet(index)
            withContext(container.dispatchers.main) {
                book.clear()
                sheetStamp++
                find.rescan()
            }
        }
    }

    override fun onCleared() {
        io.cancel()
        try {
            source?.close()
        } catch (_: IOException) {
            // Temp file safai niche hogi hi.
        }
        tempFile?.delete()
    }
}
