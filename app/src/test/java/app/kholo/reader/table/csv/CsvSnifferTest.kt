package app.kholo.reader.table.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvSnifferTest {
    @Test
    fun `finds the delimiter`() {
        assertEquals(';'.code.toByte(), CsvSniffer.sniff("a;b;c\n1;2;3\n".toByteArray()).delimiter)
        assertEquals('\t'.code.toByte(), CsvSniffer.sniff("a\tb\n1\t2\n".toByteArray()).delimiter)
    }

    @Test
    fun `sep line sets the delimiter and is skipped`() {
        val format = CsvSniffer.sniff("sep=|\na|b\n".toByteArray())
        assertEquals('|'.code.toByte(), format.delimiter)
        assertEquals(6L, format.start)
    }

    @Test
    fun `utf-8 bom is skipped`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "a,b\n".toByteArray()
        assertEquals(3L, CsvSniffer.sniff(bytes).start)
    }

    @Test
    fun `utf-16 is flagged for conversion`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "a,b\n".toByteArray(Charsets.UTF_16LE)
        assertTrue(CsvSniffer.sniff(bytes).utf16)
    }

    @Test
    fun `invalid utf-8 falls back to windows-1252`() {
        val bytes = "name,caf".toByteArray() + byteArrayOf(0xE9.toByte()) + ",ok,done,more,text\n".toByteArray()
        assertEquals("windows-1252", CsvSniffer.sniff(bytes).charset.name())
    }
}
