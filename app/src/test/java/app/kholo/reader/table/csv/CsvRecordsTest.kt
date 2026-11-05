package app.kholo.reader.table.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvRecordsTest {
    private fun parse(text: String, delimiter: Char = ','): List<List<String>> {
        val records = CsvRecords(text.byteInputStream(), delimiter.code.toByte())
        val rows = mutableListOf<List<String>>()
        val fields = ArrayList<ByteArray>()
        while (true) {
            fields.clear()
            if (!records.read(fields)) break
            rows += fields.map { String(it) }
        }
        return rows
    }

    @Test
    fun `all line endings work and the last line needs no newline`() {
        assertEquals(listOf(listOf("a"), listOf("b"), listOf("c"), listOf("d")), parse("a\nb\r\nc\rd"))
    }

    @Test
    fun `quoted delimiter and newline stay inside the field`() {
        assertEquals(listOf(listOf("a,b", "c\nd"), listOf("e", "f")), parse("\"a,b\",\"c\nd\"\ne,f\n"))
    }

    @Test
    fun `doubled quote becomes one quote`() {
        assertEquals(listOf(listOf("say \"hi\"")), parse("\"say \"\"hi\"\"\"\n"))
    }

    @Test
    fun `empty lines are rows so numbers stay true`() {
        assertEquals(listOf(listOf("a"), listOf(""), listOf("b")), parse("a\n\nb\n"))
    }

    @Test
    fun `leading zeros stay as text`() {
        assertEquals(listOf(listOf("00123", "=SUM(A1)")), parse("00123,=SUM(A1)\n"))
    }

    @Test
    fun `an unclosed quote recovers after the record limit`() {
        val big = "\"" + "x".repeat((1 shl 20) + 10) + "\nnext,row\n"
        val rows = parse(big)
        assertEquals(listOf("next", "row"), rows.last())
    }

    @Test
    fun `indexer offsets match a plain parse`() {
        val text = "a,b\n\"x\ny\",2\nlast,3\n"
        val records = CsvRecords(text.byteInputStream(), ','.code.toByte())
        assertTrue(records.skip())
        assertEquals(4L, records.position)
        assertTrue(records.skip())
        assertEquals(12L, records.position)
        assertTrue(records.skip())
        assertFalse(records.skip())
    }
}
