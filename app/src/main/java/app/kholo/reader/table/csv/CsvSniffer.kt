package app.kholo.reader.table.csv

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

private const val SAMPLE_LINES = 20
private val CANDIDATES = listOf(',', ';', '\t', '|')

data class CsvFormat(val delimiter: Byte, val charset: Charset, val start: Long, val utf16: Boolean)

object CsvSniffer {
    fun sniff(sample: ByteArray): CsvFormat {
        val utf16 = utf16Charset(sample)
        if (utf16 != null) return CsvFormat(','.code.toByte(), utf16, 2L, utf16 = true)
        var start = if (hasUtf8Bom(sample)) 3 else 0
        val charset = if (isValidUtf8(sample)) Charsets.UTF_8 else Charset.forName("windows-1252")
        val text = String(sample, start, sample.size - start, charset)
        var delimiter = bestDelimiter(text)
        val first = text.lineSequence().firstOrNull().orEmpty()
        if (first.startsWith("sep=", ignoreCase = true) && first.length >= 5) {
            delimiter = first[4]
            start += first.toByteArray(charset).size + lineBreakSize(text, first.length)
        }
        return CsvFormat(delimiter.code.toByte(), charset, start.toLong(), utf16 = false)
    }

    private fun lineBreakSize(text: String, at: Int): Int = when {
        text.startsWith("\r\n", at) -> 2
        at < text.length -> 1
        else -> 0
    }

    private fun bestDelimiter(text: String): Char {
        val lines = text.lineSequence().filter { it.isNotEmpty() }.take(SAMPLE_LINES).toList()
        return CANDIDATES.maxByOrNull { candidate -> lines.sumOf { countOutsideQuotes(it, candidate) } } ?: ','
    }

    private fun countOutsideQuotes(line: String, target: Char): Int {
        var inQuotes = false
        return line.count { char ->
            if (char == '"') inQuotes = !inQuotes
            !inQuotes && char == target
        }
    }

    private fun hasUtf8Bom(bytes: ByteArray) = bytes.size >= 3 &&
        bytes[0] == 0xEF.toByte() &&
        bytes[1] == 0xBB.toByte() &&
        bytes[2] == 0xBF.toByte()

    private fun utf16Charset(bytes: ByteArray): Charset? = when {
        bytes.size < 2 -> null
        bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE
        bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE
        else -> null
    }

    /** Sample ke aakhir me aadha character ho sakta hai, isliye wahan galti maaf hai. */
    private fun isValidUtf8(bytes: ByteArray): Boolean {
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        val trimmed = if (bytes.size > 4) bytes.copyOf(bytes.size - 4) else bytes
        return try {
            decoder.decode(ByteBuffer.wrap(trimmed))
            true
        } catch (_: CharacterCodingException) {
            bytes.size > 4 && isValidPrefix(trimmed)
        }
    }

    private fun isValidPrefix(bytes: ByteArray): Boolean {
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        val result = decoder.decode(ByteBuffer.wrap(bytes), java.nio.CharBuffer.allocate(bytes.size), true)
        return !result.isMalformed
    }
}
