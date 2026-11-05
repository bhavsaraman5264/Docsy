package app.kholo.reader.table.csv

import app.kholo.reader.table.TableInfo
import app.kholo.reader.table.TableRow
import app.kholo.reader.table.TableSource
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.charset.Charset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val INDEX_STEP = 100
private const val PUBLISH_EVERY = 5_000
private const val FIRST_ROWS = 200
private const val INITIAL_INDEX_SIZE = 1024

/** Har 100th record ka byte offset. 5 lakh rows par bas ~40 KB. Rows zaroorat par file se padhi jati hain. */
class CsvIndexSource(
    private val file: File,
    private val format: CsvFormat,
    scope: CoroutineScope,
) : TableSource {
    private val _info = MutableStateFlow(TableInfo())
    override val info: StateFlow<TableInfo> = _info.asStateFlow()

    @Volatile private var offsets = LongArray(INITIAL_INDEX_SIZE)
    private val dataStart: Long
    private val scan: Job

    init {
        val first = readRows(format.start, 0, FIRST_ROWS)
        val header = headerFor(first)
        val hasHeader = header.named
        dataStart = if (hasHeader) firstRecordEnd() else format.start
        offsets[0] = dataStart
        val visible = if (hasHeader) first.drop(1) else first
        val columns = maxOf(header.names.size, visible.maxOfOrNull { it.size } ?: 0)
        _info.value = TableInfo(header.names, visible.size, columns, finished = false)
        scan = scope.launch { scanAll() }
    }

    private class Header(val names: List<String>, val named: Boolean)

    private fun headerFor(first: List<List<String>>): Header {
        val top = first.firstOrNull().orEmpty()
        val named = top.isNotEmpty() && first.size > 1 && top.all { it.isNotBlank() && it.toDoubleOrNull() == null }
        return if (named) Header(top, true) else Header(List(top.size) { "Column ${it + 1}" }, false)
    }

    private fun firstRecordEnd(): Long = FileInputStream(file).use { stream ->
        stream.skipFully(format.start)
        val records = CsvRecords(stream, format.delimiter, format.start)
        records.skip()
        records.position
    }

    private fun CoroutineScope.scanAll() {
        try {
            FileInputStream(file).use { stream ->
                stream.skipFully(dataStart)
                val records = CsvRecords(stream, format.delimiter, dataStart)
                var count = 0
                while (isActive) {
                    val at = records.position
                    if (count % INDEX_STEP == 0) store(count / INDEX_STEP, at)
                    if (!records.skip()) break
                    count++
                    if (count % PUBLISH_EVERY == 0) {
                        val rows = count
                        _info.update { it.copy(rowCount = maxOf(it.rowCount, rows)) }
                    }
                }
                _info.update { it.copy(rowCount = count, finished = true) }
            }
        } catch (_: IOException) {
            _info.update { it.copy(finished = true, failed = true) }
        }
    }

    private fun store(slot: Int, offset: Long) {
        if (slot >= offsets.size) offsets = offsets.copyOf(offsets.size * 2)
        offsets[slot] = offset
    }

    override fun rows(start: Int, count: Int): List<TableRow> {
        val slot = start / INDEX_STEP
        val offset = if (slot == 0) dataStart else offsets.getOrElse(slot) { 0L }
        if (slot > 0 && offset == 0L) return emptyList()
        val skip = start - slot * INDEX_STEP
        return readRows(offset, skip, count).mapIndexed { i, cells -> TableRow(start + i + 1, cells) }
    }

    private fun readRows(offset: Long, skip: Int, count: Int): List<List<String>> =
        FileInputStream(file).use { stream ->
            stream.skipFully(offset)
            val records = CsvRecords(stream, format.delimiter, offset)
            repeat(skip) { if (!records.skip()) return@use emptyList() }
            val result = ArrayList<List<String>>(count)
            val fields = ArrayList<ByteArray>()
            while (result.size < count) {
                fields.clear()
                if (!records.read(fields)) break
                result += fields.map { decode(it, format.charset) }
            }
            result
        }

    private fun decode(bytes: ByteArray, charset: Charset) = String(bytes, charset)

    override fun close() {
        scan.cancel()
    }
}

private fun FileInputStream.skipFully(bytes: Long) {
    channel.position(bytes)
}
