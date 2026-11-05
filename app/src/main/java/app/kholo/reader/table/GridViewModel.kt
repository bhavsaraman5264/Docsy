package app.kholo.reader.table

import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kholo.reader.core.AppContainer
import app.kholo.reader.core.AppError
import app.kholo.reader.core.LoadState
import app.kholo.reader.core.file.SafeZip
import app.kholo.reader.core.file.readUpTo
import app.kholo.reader.core.file.toAppError
import app.kholo.reader.open.DocumentType
import app.kholo.reader.table.csv.CsvIndexSource
import app.kholo.reader.table.csv.CsvSniffer
import app.kholo.reader.table.xlsx.SharedStringsFile
import app.kholo.reader.table.xlsx.XlsxPackage
import app.kholo.reader.table.xlsx.XlsxSqliteSource
import app.kholo.reader.table.xlsx.XlsxStyles
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParserException

const val PAGE_ROWS = 128
private const val CACHED_PAGES = 48
private const val SAMPLE_BYTES = 64 * 1024

class GridViewModel(
    private val uri: Uri,
    private val type: DocumentType,
    private val size: Long?,
    private val container: AppContainer,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<TableSource>>(LoadState.Loading)
    val state: StateFlow<LoadState<TableSource>> = _state.asStateFlow()

    /** Har naye page par badhta hai, taaki dikhne wali rows dobara bane. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private val io = CoroutineScope(SupervisorJob() + container.dispatchers.io)
    private val pages = LruCache<Int, List<TableRow>>(CACHED_PAGES)
    private val loading = mutableSetOf<Int>()
    private var tempFile: File? = null
    private var source: TableSource? = null

    init {
        viewModelScope.launch {
            _state.value = try {
                withContext(container.dispatchers.io) { LoadState.Ready(open()) }
            } catch (e: IOException) {
                LoadState.Failed(e.toAppError())
            } catch (_: SecurityException) {
                LoadState.Failed(AppError.FileGone)
            } catch (_: XmlPullParserException) {
                LoadState.Failed(AppError.Corrupt)
            }
        }
    }

    private fun open(): TableSource {
        val suffix = if (type == DocumentType.Xlsx) ".xlsx" else ".csv"
        val file = container.tempFiles.copy(uri, suffix, size).also { tempFile = it }
        val opened = if (type == DocumentType.Xlsx) openXlsx(file) else openCsv(file)
        source = opened
        return opened
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

    fun row(index: Int): TableRow? {
        val page = index / PAGE_ROWS
        val rows = pages.get(page)
        if (rows == null) {
            load(page)
            return null
        }
        load(page + 1)
        return rows.getOrNull(index % PAGE_ROWS)
    }

    private fun load(page: Int) {
        val table = source ?: return
        if (pages.get(page) != null || !loading.add(page)) return
        io.launch {
            val rows = try {
                table.rows(page * PAGE_ROWS, PAGE_ROWS)
            } catch (_: IOException) {
                emptyList()
            } catch (_: IllegalStateException) {
                emptyList()
            }
            withContext(container.dispatchers.main) {
                loading.remove(page)
                if (rows.size == PAGE_ROWS || table.info.value.finished) pages.put(page, rows)
                _version.value++
            }
        }
    }

    /** Import chalte waqt adhoore pages dobara padhne hain. */
    fun refresh() {
        pages.evictAll()
        _version.value++
    }

    fun selectSheet(index: Int) {
        pages.evictAll()
        io.launch {
            source?.selectSheet(index)
            withContext(container.dispatchers.main) { _version.value++ }
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
