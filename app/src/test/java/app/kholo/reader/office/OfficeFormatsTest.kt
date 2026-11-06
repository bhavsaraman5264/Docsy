package app.kholo.reader.office

import app.kholo.reader.docx.DocBlock
import app.kholo.reader.docx.ParsedDocument
import app.kholo.reader.open.DocumentType
import app.kholo.reader.table.xls.XlsSource
import app.kholo.reader.table.xls.XlsWorkbook
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures `src/test/resources/office` me hain: Apache POI ke test-data, aur msoffcrypto se bani password files. */
class OfficeFormatsTest {
    private fun tempFile(suffix: String): File = File.createTempFile("office", suffix).apply { deleteOnExit() }

    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/office/$name")) { name }.use { it.readBytes() }

    private fun open(name: String): OleResult {
        val copy = tempFile(".ole")
        copy.writeBytes(resource(name))
        return OfficeFile(::tempFile).open(copy)
    }

    private fun ready(name: String): OfficeDocument = (open(name) as OleResult.Ready).document

    private fun locked(name: String): OfficeLock = (open(name) as OleResult.Locked).lock

    private fun parse(document: OfficeDocument): ParsedDocument = when (val content = document.content) {
        is OfficeContent.Word -> WordParser(content.document, content.table).parse()
        is OfficeContent.Slides -> PptParser(content.document).parse()
        else -> error("not a text document")
    }

    private fun ParsedDocument.paragraphs(): List<String> =
        blocks.filterIsInstance<DocBlock.Paragraph>().map { it.text.text }

    private fun table(document: OfficeDocument): XlsSource =
        XlsSource(XlsWorkbook((document.content as OfficeContent.Sheet).workbook))

    @Test
    fun wordTextIsRead() {
        val document = ready("simple.doc")
        assertEquals(DocumentType.Doc, document.type)
        assertTrue(parse(document).paragraphs().any { "created with Word 97-SR2." in it })
    }

    @Test
    fun wordTableKeepsRowsColumnsAndOrder() {
        val blocks = parse(ready("simple-table.doc")).blocks
        val table = blocks.filterIsInstance<DocBlock.Table>().single()
        assertEquals(
            listOf(listOf("Cell 1,1", "Cell 1,2", "Cell 1,3"), listOf("Cell 2,1", "Cell 2,2", "Cell 2,3")),
            table.rows.map { row -> row.map { it.text } },
        )
        val after = blocks.subList(blocks.indexOf(table) + 1, blocks.size).filterIsInstance<DocBlock.Paragraph>()
        assertEquals("This text is below the table.", after.first().text.text)
    }

    @Test
    fun wordHeadingAndListsAreMarked() {
        val paragraphs = parse(ready("Lists.doc")).blocks.filterIsInstance<DocBlock.Paragraph>()
        assertTrue(paragraphs.first { it.text.text == "Heading Level 1" }.heading > 0)
        assertNotNull(paragraphs.first { it.text.text == "Unordered list 1" }.marker)
    }

    @Test
    fun wordPageBreakIsKept() {
        val parsed = parse(ready("SampleDoc.doc"))
        assertTrue(parsed.paragraphs().any { "This is page two" in it })
        assertTrue(DocBlock.PageBreak in parsed.blocks)
    }

    @Test
    fun slidesKeepTitleAndSkipMasterText() {
        val parsed = parse(ready("basic_test_ppt_file.ppt"))
        val text = parsed.paragraphs()
        assertTrue("This is a test title" in text)
        assertTrue("This is a test subtitle" in text)
        assertFalse(text.any { "Click to edit Master" in it })
        val title = parsed.blocks.filterIsInstance<DocBlock.Paragraph>()
            .first { it.text.text == "This is a test title" }
        assertTrue(title.heading > 0)
    }

    @Test
    fun slidesReadTextBoxes() {
        val document = ready("with_textbox.ppt")
        assertEquals(DocumentType.Ppt, document.type)
        assertTrue(parse(document).paragraphs().any { "Hello, World!!!" in it })
    }

    @Test
    fun slideNumbersAreInOrder() {
        val headers = parse(ready("SampleShow.ppt")).blocks.filterIsInstance<DocBlock.SlideHeader>()
        assertTrue(headers.isNotEmpty())
        assertEquals((1..headers.size).toList(), headers.map { it.number })
    }

    @Test
    fun excelSheetsAndCellsAreRead() {
        val source = table(ready("SampleSS.xls"))
        assertEquals(listOf("First Sheet", "Sheet Number 2", "Sheet3"), source.sheets)
        val rows = source.rows(0, 10)
        assertEquals("Test spreadsheet", rows[0].cells[0])
        assertEquals("2nd row 2nd column", rows[1].cells[1])
        source.selectSheet(1)
        assertEquals("Start of 2nd sheet", source.rows(0, 1)[0].cells[0])
        source.selectSheet(2)
        assertEquals(0, source.info.value.rowCount)
        assertTrue(source.info.value.finished)
    }

    @Test
    fun excelValueTypesMatchWhatExcelShows() {
        val rows = table(ready("generated.xls")).rows(0, 10)
        assertEquals(listOf("Naam", "Rakam"), rows[0].cells)
        assertEquals("Asha हिन्दी", rows[1].cells[0])
        assertEquals("1234.5", rows[1].cells[1])
        assertEquals("100000000000", rows[2].cells[1])
        assertEquals("abcd", rows[3].cells[0])
        assertEquals("TRUE", rows[4].cells[0])
        assertEquals("24-11-2006", rows[5].cells[0])
    }

    @Test
    fun excelDateFormatsBecomeDates() {
        val cells = table(ready("Formatting.xls")).rows(0, 20).mapNotNull { it.cells.getOrNull(1) }
        assertTrue(cells.toString(), cells.count { it == "24-11-2006" } >= 5)
    }

    @Test
    fun excelGapsKeepRealRowNumbers() {
        val rows = table(ready("SimpleMultiCell.xls")).rows(0, 10)
        assertEquals("1", rows[0].cells[0])
        val numbers = rows.map { it.number }
        assertEquals(numbers.distinct().sorted(), numbers)
    }

    @Test
    fun oldWordRc4Password() {
        val lock = locked("password_tika_binaryrc4.doc")
        assertNull(lock.unlock("wrong"))
        val text = parse(checkNotNull(lock.unlock("tika"))).paragraphs()
        assertTrue(text.toString(), text.any { "This is an encrypted Word 2007 File." in it })
    }

    @Test
    fun oldWordCryptoApiPassword() {
        val lock = locked("password_password_cryptoapi.doc")
        assertNull(lock.unlock("wrong"))
        val text = parse(checkNotNull(lock.unlock("password"))).paragraphs()
        assertTrue(text.toString(), text.any { "This is a test" in it })
    }

    @Test
    fun oldExcelPassword() {
        val lock = locked("password.xls")
        assertNull(lock.unlock("wrong"))
        val source = table(checkNotNull(lock.unlock("password")))
        assertTrue(source.sheets.isNotEmpty())
        assertTrue(source.info.value.rowCount > 0)
        assertTrue(source.rows(0, 50).any { row -> row.cells.any { it.isNotEmpty() } })
    }

    @Test
    fun oldPowerPointPasswordSaysUnsupported() {
        assertEquals(OleResult.PasswordUnsupported, open("Password_Protected-hello.ppt"))
    }

    private fun assertPackage(name: String, password: String, type: DocumentType, part: String) {
        val lock = locked(name)
        assertNull(lock.unlock("wrong"))
        val opened = checkNotNull(lock.unlock(password))
        assertEquals(type, opened.type)
        val file = (opened.content as OfficeContent.Package).file
        ZipFile(file).use { zip -> assertNotNull(zip.getEntry(part)) }
        lock.close()
    }

    @Test
    fun agileWord() = assertPackage("agile.docx", "kholo123", DocumentType.Docx, "word/document.xml")

    @Test
    fun agileExcel() = assertPackage("agile.xlsx", "kholo123", DocumentType.Xlsx, "xl/workbook.xml")

    @Test
    fun agilePowerPoint() = assertPackage("agile.pptx", "kholo123", DocumentType.Pptx, "ppt/presentation.xml")

    @Test
    fun standardExcel() = assertPackage("protected_passtika.xlsx", "tika", DocumentType.Xlsx, "xl/workbook.xml")

    @Test
    fun decryptedPackageMatchesBody() {
        val lock = locked("agile.docx")
        val file = (checkNotNull(lock.unlock("kholo123")).content as OfficeContent.Package).file
        val xml = ZipFile(file).use { zip ->
            zip.getInputStream(zip.getEntry("word/document.xml")).use { String(it.readBytes()) }
        }
        assertTrue("Agile encrypted Word body" in xml)
        lock.close()
    }

    @Test
    fun sourceTempFileIsDeleted() {
        val copy = tempFile(".ole")
        copy.writeBytes(resource("simple.doc"))
        OfficeFile(::tempFile).open(copy)
        assertFalse(copy.exists())
    }

    @Test(expected = IOException::class)
    fun garbageIsRejected() {
        val copy = tempFile(".ole")
        copy.writeBytes(ByteArray(4096) { 7 })
        OfficeFile(::tempFile).open(copy)
    }

    /** Kharab file par sirf "file nahi khuli" aana chahiye. Koi aur exception app band kar deta. */
    @Test
    fun damagedFilesNeverCrash() {
        val random = Random(20261106)
        val names = listOf("simple-table.doc", "Lists.doc", "SampleSS.xls", "generated.xls", "basic_test_ppt_file.ppt")
        for (name in names) {
            val original = resource(name)
            repeat(FUZZ_ROUNDS) {
                val damaged = original.copyOf()
                repeat(FUZZ_BYTES) { damaged[random.nextInt(damaged.size)] = random.nextInt().toByte() }
                val cut = if (random.nextBoolean()) damaged else damaged.copyOf(random.nextInt(damaged.size))
                openDamaged(cut)
            }
        }
    }

    private fun openDamaged(bytes: ByteArray) {
        val copy = tempFile(".ole")
        copy.writeBytes(bytes)
        try {
            val document = (OfficeFile(::tempFile).open(copy) as? OleResult.Ready)?.document ?: return
            when (document.content) {
                is OfficeContent.Sheet -> table(document).run {
                    for (sheet in sheets.indices) {
                        selectSheet(sheet)
                        rows(0, info.value.rowCount)
                    }
                }
                else -> parse(document)
            }
        } catch (_: IOException) {
            // Yahi umeed hai.
        } catch (_: IndexOutOfBoundsException) {
            // ViewModels ise "file kharab hai" dikhate hain.
        } catch (_: IllegalArgumentException) {
            // ViewModels ise "file kharab hai" dikhate hain.
        } finally {
            copy.delete()
        }
    }

    private companion object {
        const val FUZZ_ROUNDS = 300
        const val FUZZ_BYTES = 24
    }
}
