package app.kholo.reader.table.xls

import app.kholo.reader.office.UnsupportedOffice
import app.kholo.reader.office.i32
import app.kholo.reader.office.toSafeInt
import app.kholo.reader.office.u16
import app.kholo.reader.office.u32
import app.kholo.reader.office.u8
import app.kholo.reader.table.xlsx.XlsxStyles
import java.io.IOException

internal const val RECORD_HEADER = 4
internal const val BOF = 0x0809
internal const val EOF = 0x000A
internal const val FILE_PASS = 0x002F
internal const val BOUND_SHEET = 0x0085
private const val DATE_MODE = 0x0022
private const val FORMAT = 0x041E
private const val XF = 0x00E0
private const val SST = 0x00FC
private const val CONTINUE = 0x003C
private const val BIFF8 = 0x0600
private const val SHEET_NAME_AT = 6
private const val SHEET_TYPE_AT = 5
private const val WORKSHEET = 0
private const val SST_STRINGS_AT = 8
private const val FLAG_WIDE = 0x01
private const val FLAG_EXTENDED = 0x04
private const val FLAG_RICH = 0x08
private const val RICH_RUN_BYTES = 4
private val BUILT_IN_DATES = (14..22).toSet() + (27..36).toSet() + (45..47).toSet() + (50..58).toSet()

class XlsSheetInfo(val name: String, val offset: Int)

/** BIFF8 ke record par chalne ka ek hi tareeka. `each` false lautaye to ruk jao. */
internal inline fun ByteArray.forEachRecord(from: Int, each: (id: Int, body: Int, length: Int) -> Boolean) {
    var at = from
    while (at + RECORD_HEADER <= size) {
        val id = u16(at)
        val length = u16(at + 2)
        val body = at + RECORD_HEADER
        if (body + length > size) return
        if (!each(id, body, length)) return
        at = body + length
    }
}

/** XLUnicodeString: lambai, phir ek flag byte, phir 1 ya 2 byte ke akshar. */
internal fun ByteArray.unicodeString(at: Int, count: Int): String {
    val wide = u8(at) and FLAG_WIDE != 0
    return if (wide) {
        String(this, at + 1, count * 2, Charsets.UTF_16LE)
    } else {
        String(this, at + 1, count, Charsets.ISO_8859_1)
    }
}

/** Excel 97-2003 ki globals: sheets, shared strings aur date formats. */
class XlsWorkbook(val data: ByteArray) {
    val sheets = ArrayList<XlsSheetInfo>()
    var strings: Array<String> = emptyArray()
        private set
    var date1904 = false
        private set
    private val dateFormats = HashSet<Int>()
    private val xfFormats = ArrayList<Int>()

    init {
        if (data.u16(0) != BOF) throw IOException("not biff")
        if (data.u16(RECORD_HEADER) != BIFF8) throw UnsupportedOffice()
        readGlobals()
    }

    fun isDate(xf: Int): Boolean = xfFormats.getOrNull(xf)?.let { it in BUILT_IN_DATES || it in dateFormats } == true

    private fun readGlobals() {
        val segments = ArrayList<IntRange>()
        var inStrings = false
        data.forEachRecord(0) { id, body, length ->
            if (inStrings && id != CONTINUE) inStrings = false
            when (id) {
                DATE_MODE -> date1904 = data.u16(body) != 0
                FORMAT -> readFormat(body)
                XF -> xfFormats += data.u16(body + 2)
                BOUND_SHEET -> readSheet(body)
                SST -> {
                    inStrings = true
                    segments += body until body + length
                }
                CONTINUE -> if (inStrings) segments += body until body + length
            }
            id != EOF
        }
        if (segments.isNotEmpty()) strings = StringTable(data, segments).read()
    }

    private fun readFormat(body: Int) {
        val code = data.unicodeString(body + 4, data.u16(body + 2))
        if (XlsxStyles.looksLikeDate(code)) dateFormats += data.u16(body)
    }

    private fun readSheet(body: Int) {
        if (data.u8(body + SHEET_TYPE_AT) != WORKSHEET) return
        val name = data.unicodeString(body + SHEET_NAME_AT + 1, data.u8(body + SHEET_NAME_AT))
        sheets += XlsSheetInfo(name, data.u32(body).toSafeInt())
    }
}

/**
 * Shared strings kai CONTINUE records me bante hain, aur ek string beech se bhi kat sakti hai.
 * Kate hue hisse ke shuru me naya flag byte hota hai, kyunki chaudai wahan badal sakti hai.
 */
private class StringTable(private val data: ByteArray, private val segments: List<IntRange>) {
    private var segment = 0
    private var at = segments[0].first

    private fun left(): Int = segments[segment].last + 1 - at

    private fun advance(): Boolean {
        if (segment + 1 >= segments.size) return false
        segment++
        at = segments[segment].first
        return true
    }

    private fun ensure() {
        while (left() <= 0) if (!advance()) throw IOException("strings cut short")
    }

    private fun byte(): Int {
        ensure()
        return data.u8(at++)
    }

    private fun short(): Int = byte() or (byte() shl Byte.SIZE_BITS)

    private fun skip(count: Long) {
        var remaining = count
        while (remaining > 0) {
            ensure()
            val step = minOf(remaining, left().toLong()).toInt()
            at += step
            remaining -= step
        }
    }

    fun read(): Array<String> {
        val declared = data.i32(at + Int.SIZE_BYTES)
        at += SST_STRINGS_AT
        if (declared < 0) throw IOException("bad string count")
        val found = ArrayList<String>(minOf(declared, data.size / 2))
        try {
            repeat(declared) { found += next() }
        } catch (_: IOException) {
            // Kati hui table: jitni strings mili unhi se kaam chalao, baaki cells khaali dikhenge.
        }
        return found.toTypedArray()
    }

    private fun next(): String {
        val count = short()
        val flags = byte()
        val runs = if (flags and FLAG_RICH != 0) short() else 0
        val extended = if (flags and FLAG_EXTENDED != 0) readInt() else 0L
        val text = chars(count, flags and FLAG_WIDE != 0)
        skip(runs.toLong() * RICH_RUN_BYTES + extended)
        return text
    }

    private fun readInt(): Long {
        val low = short().toLong()
        return low or (short().toLong() shl Short.SIZE_BITS)
    }

    private fun chars(count: Int, startWide: Boolean): String {
        val text = StringBuilder(count)
        var wide = startWide
        var remaining = count
        while (remaining > 0) {
            if (left() <= 0) {
                if (!advance()) throw IOException("string cut short")
                wide = data.u8(at++) and FLAG_WIDE != 0
            }
            val width = if (wide) 2 else 1
            val step = minOf(remaining, left() / width)
            if (step <= 0) throw IOException("bad string")
            val charset = if (wide) Charsets.UTF_16LE else Charsets.ISO_8859_1
            text.append(String(data, at, step * width, charset))
            at += step * width
            remaining -= step
        }
        return text.toString()
    }
}
