package app.kholo.reader.table.xlsx

import android.database.sqlite.SQLiteDatabase
import app.kholo.reader.core.file.SafeZip
import app.kholo.reader.core.file.attr
import app.kholo.reader.core.file.safeXml
import app.kholo.reader.table.TableInfo
import app.kholo.reader.table.TableRow
import app.kholo.reader.table.TableSource
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

private const val FIRST_COMMIT = 200
private const val BATCH_ROWS = 2000

/**
 * Sheet ko stream karke private SQLite me likhta hai, aur pehli 200 rows aate hi grid chal padti hai.
 * Bank statement jaisa data yahan hota hai, isliye viewer band hote hi poora cache delete hota hai.
 */
class XlsxSqliteSource(
    private val zip: SafeZip,
    private val workbook: XlsxWorkbook,
    private val strings: SharedStringsFile,
    private val styles: XlsxStyles,
    private val dbFile: File,
    private val scope: CoroutineScope,
) : TableSource, Closeable {
    private val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null).apply {
        enableWriteAheadLogging()
        execSQL("PRAGMA synchronous=OFF")
        execSQL(
            "CREATE TABLE IF NOT EXISTS row_data(sheet INTEGER, pos INTEGER, excel_row INTEGER, cells BLOB, " +
                "PRIMARY KEY(sheet, pos)) WITHOUT ROWID",
        )
    }
    private val _info = MutableStateFlow(TableInfo())
    override val info: StateFlow<TableInfo> = _info.asStateFlow()
    override val sheets: List<String> = workbook.sheets.map { it.name }
    private var current = 0
    private var job: Job? = null
    private val finishedSheets = mutableMapOf<Int, TableInfo>()

    init {
        selectSheet(0)
    }

    override fun selectSheet(index: Int) {
        job?.cancel()
        current = index
        val done = finishedSheets[index]
        if (done != null) {
            _info.value = done
            return
        }
        _info.value = TableInfo()
        db.execSQL("DELETE FROM row_data WHERE sheet=$index")
        job = scope.launch {
            try {
                import(index)
            } catch (_: IOException) {
                _info.update { it.copy(finished = true, failed = true) }
            } catch (_: XmlPullParserException) {
                _info.update { it.copy(finished = true, failed = true) }
            }
        }
    }

    private fun CoroutineScope.import(sheet: Int) {
        val path = workbook.sheets.getOrNull(sheet)?.path
        val stream = path?.let(zip::open)
        if (stream == null) {
            _info.value = TableInfo(finished = true)
            return
        }
        val insert = db.compileStatement("INSERT OR REPLACE INTO row_data VALUES(?,?,?,?)")
        stream.use {
            val parser = safeXml(it)
            var pos = 0
            var columns = 0
            var lastRow = 0
            val cells = ArrayList<String>()
            var column = -1
            var type: String? = null
            var style = 0
            val value = StringBuilder()
            var inValue = false
            var excelRow = 0
            db.beginTransaction()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT && isActive) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "row" -> {
                            cells.clear()
                            column = -1
                            excelRow = parser.attr("r")?.toIntOrNull() ?: (lastRow + 1)
                        }
                        "c" -> {
                            val reference = parser.attr("r")
                            column = if (reference != null) XlsxCell.columnOf(reference) else column + 1
                            type = parser.attr("t")
                            style = parser.attr("s")?.toIntOrNull() ?: 0
                            value.clear()
                        }
                        "v", "t" -> inValue = true
                    }
                    XmlPullParser.TEXT -> if (inValue) value.append(parser.text)
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "v", "t" -> inValue = false
                        "c" -> if (column >= 0) {
                            while (cells.size <= column) cells.add("")
                            cells[column] = render(value.toString(), type, style)
                        }
                        "row" -> {
                            lastRow = excelRow
                            if (cells.isNotEmpty()) {
                                insert.bindLong(1, sheet.toLong())
                                insert.bindLong(2, pos.toLong())
                                insert.bindLong(3, excelRow.toLong())
                                insert.bindBlob(4, encode(cells))
                                insert.executeInsert()
                                pos++
                                columns = maxOf(columns, cells.size)
                                if (pos == FIRST_COMMIT || pos % BATCH_ROWS == 0) {
                                    db.setTransactionSuccessful()
                                    db.endTransaction()
                                    publish(pos, columns, finished = false)
                                    db.beginTransaction()
                                }
                            }
                        }
                    }
                }
                event = parser.next()
            }
            db.setTransactionSuccessful()
            db.endTransaction()
            if (isActive) {
                publish(pos, columns, finished = true)
                finishedSheets[sheet] = _info.value
            }
        }
    }

    private fun publish(rows: Int, columns: Int, finished: Boolean) {
        val header = List(columns) { columnName(it) }
        _info.value = TableInfo(header, rows, columns, finished)
    }

    private fun columnName(index: Int): String {
        var value = index + 1
        val name = StringBuilder()
        while (value > 0) {
            value--
            name.insert(0, 'A' + value % 26)
            value /= 26
        }
        return name.toString()
    }

    private fun render(raw: String, type: String?, style: Int): String = when (type) {
        "s" -> strings.get(raw.toIntOrNull() ?: -1)
        "inlineStr", "str", "e" -> SharedStringsFile.decodeEscapes(raw)
        "b" -> if (raw == "1") "TRUE" else "FALSE"
        else -> when {
            raw.isEmpty() -> ""
            styles.isDate(style) -> XlsxCell.date(raw, workbook.date1904)
            else -> XlsxCell.plainNumber(raw)
        }
    }

    private fun encode(cells: List<String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(cells.size)
            cells.forEach {
                val text = it.toByteArray()
                out.writeInt(text.size)
                out.write(text)
            }
        }
        return bytes.toByteArray()
    }

    private fun decode(blob: ByteArray): List<String> {
        val input = java.io.DataInputStream(blob.inputStream())
        return List(input.readInt()) {
            val text = ByteArray(input.readInt())
            input.readFully(text)
            String(text)
        }
    }

    override fun rows(start: Int, count: Int): List<TableRow> {
        if (!db.isOpen) return emptyList()
        val args = arrayOf(current.toString(), start.toString(), (start + count - 1).toString())
        val query = "SELECT excel_row, cells FROM row_data WHERE sheet=? AND pos BETWEEN ? AND ? ORDER BY pos"
        return db.rawQuery(query, args).use { cursor ->
            val rows = ArrayList<TableRow>(count)
            while (cursor.moveToNext()) rows += TableRow(cursor.getInt(0), decode(cursor.getBlob(1)))
            rows
        }
    }

    override fun close() {
        job?.cancel()
        db.close()
        strings.close()
        zip.close()
        SQLiteDatabase.deleteDatabase(dbFile)
    }
}
