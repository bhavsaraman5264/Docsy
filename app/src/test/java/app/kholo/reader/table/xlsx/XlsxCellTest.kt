package app.kholo.reader.table.xlsx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XlsxCellTest {
    @Test
    fun `column letters become indexes`() {
        assertEquals(0, XlsxCell.columnOf("A1"))
        assertEquals(26, XlsxCell.columnOf("AA7"))
        assertEquals(16383, XlsxCell.columnOf("XFD1"))
        assertEquals(-1, XlsxCell.columnOf("ZZZZ1"))
    }

    @Test
    fun `big numbers never show as scientific`() {
        assertEquals("123456789012", XlsxCell.plainNumber("1.23456789012E11"))
        assertEquals("00123", XlsxCell.plainNumber("00123"))
    }

    @Test
    fun `excel serials become dates including the 1900 leap bug`() {
        assertEquals("01-01-2024", XlsxCell.date("45292", date1904 = false))
        assertEquals("01-03-1900", XlsxCell.date("61", date1904 = false))
        assertEquals("02-01-1904", XlsxCell.date("1", date1904 = true))
    }

    @Test
    fun `time fraction is kept`() {
        assertEquals("01-01-2024 12:00", XlsxCell.date("45292.5", date1904 = false))
    }

    @Test
    fun `date formats are told apart from numbers`() {
        assertTrue(XlsxStyles.looksLikeDate("dd/mm/yyyy"))
        assertFalse(XlsxStyles.looksLikeDate("#,##0.00"))
        assertFalse(XlsxStyles.looksLikeDate("\"Days\" 0"))
    }

    @Test
    fun `excel escapes are decoded`() {
        assertEquals("a\rb", SharedStringsFile.decodeEscapes("a_x000D_b"))
    }
}
