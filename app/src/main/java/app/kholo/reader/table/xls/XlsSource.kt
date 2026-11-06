package app.kholo.reader.table.xls

import app.kholo.reader.office.i32
import app.kholo.reader.office.i64
import app.kholo.reader.office.u16
import app.kholo.reader.office.u8
import app.kholo.reader.table.TableInfo
import app.kholo.reader.table.TableRow
import app.kholo.reader.table.TableSource
import app.kholo.reader.table.xlsx.XlsxCell
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val FORMULA = 0x0006
private const val NUMBER = 0x0203
private const val LABEL = 0x0204
private const val BOOL_ERROR = 0x0205
private const val STRING = 0x0207
private const val RK = 0x027E
private const val MUL_RK = 0x00BD
private const val LABEL_SST = 0x00FD
private val CELL_RECORDS = setOf(FORMULA, NUMBER, LABEL, BOOL_ERROR, RK, MUL_RK, LABEL_SST)
private const val COLUMN_AT = 2
private const val XF_AT = 4
private const val VALUE_AT = 6
private const val RK_PAIR = 6
private const val MUL_RK_TAIL = 2
private const val FORMULA_SPECIAL_AT = 12
private const val FORMULA_SPECIAL = 0xFFFF
private const val FORMULA_TEXT = 0
private const val FORMULA_BOOL = 1
private const val FORMULA_ERROR = 2
private const val FORMULA_VALUE_AT = 8
private const val ERROR_FLAG_AT = 7
private const val STRING_LOOKAHEAD = 3
private const val RK_DIV_100 = 1
private const val RK_INTEGER = 2
private const val RK_VALUE_MASK = 3
private const val RK_INT_SHIFT = 2
private const val HUNDRED = 100.0
private const val MAX_EXACT = 1e15
private const val LOW_INT_MASK = 0xFFFFFFFFL
private const val INITIAL_CELLS = 1024

private val ERRORS = mapOf(
    0x00 to "#NULL!",
    0x07 to "#DIV/0!",
    0x0F to "#VALUE!",
    0x17 to "#REF!",
    0x1D to "#NAME?",
    0x24 to "#NUM!",
    0x2A to "#N/A",
)

/** Har row ke cell records kahan hain. Cell ka text tabhi banta hai jab row screen par aaye. */
private class SheetIndex(val rowNumbers: IntArray, val rowStarts: IntArray, val offsets: IntArray, val columns: Int)

/**
 * Excel 97-2003 ke liye grid source. File ke bytes memory me hain aur per sheet sirf offsets ka index
 * banta hai, isliye rows ka kharch cells ke hisaab se nahi badhta.
 */
class XlsSource(private val book: XlsWorkbook) : TableSource {
    private val data = book.data
    private val _info = MutableStateFlow(TableInfo())
    override val info: StateFlow<TableInfo> = _info.asStateFlow()
    override val sheets: List<String> = book.sheets.map { it.name }
    private val indexes = HashMap<Int, SheetIndex>()
    private var current: SheetIndex? = null

    init {
        selectSheet(0)
    }

    @Synchronized
    override fun selectSheet(index: Int) {
        val sheet = book.sheets.getOrNull(index)
        if (sheet == null) {
            current = null
            _info.value = TableInfo(finished = true)
            return
        }
        val chosen = indexes.getOrPut(index) { buildIndex(sheet.offset) }
        current = chosen
        _info.value = TableInfo(
            header = List(chosen.columns, XlsxCell::columnName),
            rowCount = chosen.rowNumbers.size,
            columnCount = chosen.columns,
            finished = true,
        )
    }

    private fun buildIndex(start: Int): SheetIndex {
        var packed = LongArray(INITIAL_CELLS)
        var count = 0
        var columns = 0
        data.forEachRecord(start) { id, body, length ->
            if (id in CELL_RECORDS) {
                if (count == packed.size) packed = packed.copyOf(count * 2)
                packed[count++] = (data.u16(body).toLong() shl Int.SIZE_BITS) or (body - RECORD_HEADER).toLong()
                columns = maxOf(columns, lastColumn(id, body, length) + 1)
            }
            id != EOF
        }
        val sorted = packed.copyOf(count).also { it.sort() }
        val numbers = ArrayList<Int>()
        val starts = ArrayList<Int>()
        val offsets = IntArray(count)
        sorted.forEachIndexed { index, value ->
            val row = (value ushr Int.SIZE_BITS).toInt()
            if (numbers.lastOrNull() != row) {
                numbers += row
                starts += index
            }
            offsets[index] = (value and LOW_INT_MASK).toInt()
        }
        starts += count
        return SheetIndex(numbers.toIntArray(), starts.toIntArray(), offsets, columns)
    }

    private fun lastColumn(id: Int, body: Int, length: Int): Int =
        if (id == MUL_RK) data.u16(body + length - MUL_RK_TAIL) else data.u16(body + COLUMN_AT)

    @Synchronized
    override fun rows(start: Int, count: Int): List<TableRow> {
        val sheet = current ?: return emptyList()
        val end = minOf(start + count, sheet.rowNumbers.size)
        return (start until end).map { position ->
            val cells = ArrayList<String>()
            for (index in sheet.rowStarts[position] until sheet.rowStarts[position + 1]) {
                readRecord(sheet.offsets[index], cells)
            }
            TableRow(sheet.rowNumbers[position] + 1, cells)
        }
    }

    private fun put(cells: MutableList<String>, column: Int, value: String) {
        while (cells.size <= column) cells += ""
        cells[column] = value
    }

    private fun readRecord(at: Int, cells: MutableList<String>) {
        val id = data.u16(at)
        val length = data.u16(at + 2)
        val body = at + RECORD_HEADER
        val column = data.u16(body + COLUMN_AT)
        val xf = data.u16(body + XF_AT)
        when (id) {
            LABEL_SST -> put(cells, column, book.strings.getOrElse(data.i32(body + VALUE_AT)) { "" })
            LABEL -> put(cells, column, data.unicodeString(body + VALUE_AT + 2, data.u16(body + VALUE_AT)))
            NUMBER -> put(cells, column, number(Double.fromBits(data.i64(body + VALUE_AT)), xf))
            RK -> put(cells, column, number(rk(data.i32(body + VALUE_AT)), xf))
            MUL_RK -> {
                val pairs = (length - XF_AT - MUL_RK_TAIL) / RK_PAIR
                for (index in 0 until pairs) {
                    val pair = body + XF_AT + index * RK_PAIR
                    put(cells, column + index, number(rk(data.i32(pair + 2)), data.u16(pair)))
                }
            }
            BOOL_ERROR -> put(cells, column, boolOrError(body))
            FORMULA -> put(cells, column, formula(body, length, xf))
        }
    }

    private fun boolOrError(body: Int): String {
        val value = data.u8(body + VALUE_AT)
        return if (data.u8(body + ERROR_FLAG_AT) != 0) ERRORS[value].orEmpty() else bool(value)
    }

    private fun bool(value: Int): String = if (value != 0) "TRUE" else "FALSE"

    private fun formula(body: Int, length: Int, xf: Int): String {
        val value = body + VALUE_AT
        if (data.u16(body + FORMULA_SPECIAL_AT) != FORMULA_SPECIAL) return number(Double.fromBits(data.i64(value)), xf)
        return when (data.u8(value)) {
            FORMULA_TEXT -> formulaText(body + length)
            FORMULA_BOOL -> bool(data.u8(value + 2))
            FORMULA_ERROR -> ERRORS[data.u8(value + 2)].orEmpty()
            else -> ""
        }
    }

    /** Formula ka text result agle STRING record me hota hai. Beech me shared-formula record aa sakta hai. */
    private fun formulaText(next: Int): String {
        var seen = 0
        var text = ""
        data.forEachRecord(next) { id, body, _ ->
            if (id == STRING) text = data.unicodeString(body + 2, data.u16(body))
            id != STRING && ++seen < STRING_LOOKAHEAD
        }
        return text
    }

    private fun rk(raw: Int): Double {
        val value = if (raw and RK_INTEGER != 0) {
            (raw shr RK_INT_SHIFT).toDouble()
        } else {
            Double.fromBits((raw and RK_VALUE_MASK.inv()).toLong() shl Int.SIZE_BITS)
        }
        return if (raw and RK_DIV_100 != 0) value / HUNDRED else value
    }

    private fun number(value: Double, xf: Int): String = when {
        book.isDate(xf) -> XlsxCell.date(value.toString(), book.date1904)
        value.isNaN() || value.isInfinite() -> ""
        value == Math.rint(value) && Math.abs(value) < MAX_EXACT -> value.toLong().toString()
        else -> BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
    }

    override fun close() = Unit
}
