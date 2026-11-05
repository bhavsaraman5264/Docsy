package app.kholo.reader.table.xlsx

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val LETTER_BASE = 26
private const val SECONDS_IN_DAY = 86_400.0
private const val LOTUS_BUG_SERIAL = 60
private const val MAX_COLUMN = 16_384

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

    /** Bade number aur Aadhaar jaise 12 digit kabhi `1.2E+11` na dikhen. */
    fun plainNumber(raw: String): String = try {
        if ('E' in raw || 'e' in raw) BigDecimal(raw).toPlainString() else raw
    } catch (_: NumberFormatException) {
        raw
    }

    fun date(raw: String, date1904: Boolean): String {
        val serial = raw.toDoubleOrNull() ?: return raw
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
