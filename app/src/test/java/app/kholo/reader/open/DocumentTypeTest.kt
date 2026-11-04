package app.kholo.reader.open

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentTypeTest {
    private val pdf = "%PDF-1.7\n".toByteArray()
    private val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00)
    private val ole = "D0CF11E0A1B11AE1".chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `bytes win over a wrong extension`() {
        assertEquals(DocumentType.Pdf, DocumentType.detect(pdf, "report.csv", "text/csv"))
    }

    @Test
    fun `whatsapp octet-stream pdf is still a pdf`() {
        assertEquals(DocumentType.Pdf, DocumentType.detect(pdf, null, "application/octet-stream"))
    }

    @Test
    fun `pdf header after leading junk is found`() {
        val head = ByteArray(100) { 0x20 } + pdf
        assertEquals(DocumentType.Pdf, DocumentType.detect(head, "a.pdf", null))
    }

    @Test
    fun `zip is split by extension or mime`() {
        assertEquals(DocumentType.Docx, DocumentType.detect(zip, "a.docx", null))
        assertEquals(DocumentType.Xlsx, DocumentType.detect(zip, "a.xlsm", null))
        assertEquals(
            DocumentType.Xlsx,
            DocumentType.detect(zip, null, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        )
        assertEquals(DocumentType.Unknown, DocumentType.detect(zip, "a.zip", null))
    }

    @Test
    fun `ole files are reported as legacy office`() {
        assertEquals(DocumentType.LegacyOffice, DocumentType.detect(ole, "secret.xlsx", null))
    }

    @Test
    fun `empty file is its own case`() {
        assertEquals(DocumentType.Empty, DocumentType.detect(ByteArray(0), "a.pdf", null))
    }

    @Test
    fun `text and csv are split by name`() {
        val text = "a,b\n1,2\n".toByteArray()
        assertEquals(DocumentType.Csv, DocumentType.detect(text, "a.csv", null))
        assertEquals(DocumentType.Text, DocumentType.detect(text, "a.txt", null))
    }

    @Test
    fun `binary junk is not text`() {
        assertEquals(DocumentType.Unknown, DocumentType.detect(byteArrayOf(1, 0, 2), "a.csv", null))
    }

    @Test
    fun `utf-16 text with a bom is not binary`() {
        val text = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x41, 0x00)
        assertEquals(DocumentType.Text, DocumentType.detect(text, "a.txt", null))
    }
}
