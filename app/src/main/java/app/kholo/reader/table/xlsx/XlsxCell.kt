package app.kholo.reader.table.xlsx

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private const val LETTER_BASE = 26
private const val SECONDS_IN_DAY = 86_400.0
private const val LOTUS_BUG_SERIAL = 60
private const val MAX_COLUMN = 16_384
private const val MAX_PLAIN_DIGITS = 400

/** Excel ki aakhri date, 31-12-9999. */
private const val MAX_DATE_SERIAL = 2_958_465.0

object XlsxCell {
    /** `C5` se 0-based column. Kharab reference par -1. */
    fun columnOf(reference: String): Int {
        var column = 0
        for (char in reference) {
            if (char !in 'A'..'Z') break
            column = column * LETTER_BASE + (char - 'A' + 1)
        }
        return if (column in 1..MAX_COLUMN) column - 1 else -1
    }

    /** 0-based column se Excel ka naam: 0 se `A`, 26 se `AA`. */
    fun columnName(index: Int): String {
        var value = index + 1
        val name = StringBuilder()
        while (value > 0) {
            value--
            name.insert(0, 'A' + value % LETTER_BASE)
            value /= LETTER_BASE
        }
        return name.toString()
    }

    /** Bade number aur Aadhaar jaise 12 digit kabhi `1.2E+11` na dikhen. */
    fun plainNumber(raw: String): String = try {
        val scientific = 'E' in raw || 'e' in raw
        val value = if (scientific) BigDecimal(raw) else null
        // 1E999999999 ko likhne me memory khatam ho jati, aise number jaise hain waise hi dikhao.
        if (value != null && abs(value.scale()) <= MAX_PLAIN_DIGITS) value.toPlainString() else raw
    } catch (_: NumberFormatException) {
        raw
    } catch (_: ArithmeticException) {
        raw
    }

    fun date(raw: String, date1904: Boolean): String {
        val serial = raw.toDoubleOrNull()?.takeIf { it in 0.0..MAX_DATE_SERIAL } ?: return raw
        val days = serial.toLong()
        val base = when {
            date1904 -> LocalDate.of(1904, 1, 1)
            days < LOTUS_BUG_SERIAL -> LocalDate.of(1899, 12, 31)
            else -> LocalDate.of(1899, 12, 30)
        }
        val seconds = ((serial - days) * SECONDS_IN_DAY).toLong()
        val value: LocalDateTime = base.plusDays(days).atStartOfDay().plusSeconds(seconds)
        val pattern = if (seconds == 0L) "dd-MM-yyyy" else "dd-MM-yyyy HH:mm"
        return value.format(DateTimeFormatter.ofPattern(pattern))
    }
}
