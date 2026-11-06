package app.kholo.reader.office

import app.kholo.reader.core.file.SafeZip
import app.kholo.reader.open.DocumentType
import app.kholo.reader.table.xls.BOF
import app.kholo.reader.table.xls.BOUND_SHEET
import app.kholo.reader.table.xls.EOF
import app.kholo.reader.table.xls.FILE_PASS
import app.kholo.reader.table.xls.RECORD_HEADER
import app.kholo.reader.table.xls.forEachRecord
import java.io.Closeable
import java.io.File
import java.io.IOException

private const val WORD_STREAM = "WordDocument"
private const val EXCEL_STREAM = "Workbook"
private const val POWERPOINT_STREAM = "PowerPoint Document"
private const val ENCRYPTED_SUMMARY = "EncryptedSummary"
private const val ENCRYPTION_INFO = "EncryptionInfo"
private const val ENCRYPTED_PACKAGE = "EncryptedPackage"
private const val WORD_PLAIN_HEAD = 0x44
private const val WORD_BLOCK = 512
private const val EXCEL_BLOCK = 1024
private const val FILE_PASS_RC4 = 1
private const val USR_EXCL = 0x0194
private const val FILE_LOCK = 0x0195
private const val INTERFACE_HDR = 0x00E1
private const val RRD_INFO = 0x0196
private const val RRD_HEAD = 0x0138
private const val SHEET_OFFSET_BYTES = 4

/** Excel bina password poochhe isi se kholta hai, jab file sirf "read only" ke liye bandh ho. */
private const val EXCEL_DEFAULT_PASSWORD = "VelvetSweatshop"

/** Khuli hui file. Decrypt hua data sirf memory me rehta hai, sivay ZIP ke jo private cache me hoti hai. */
sealed interface OfficeContent {
    class Package(val file: File) : OfficeContent

    class Word(val document: ByteArray, val table: ByteArray) : OfficeContent

    class Sheet(val workbook: ByteArray) : OfficeContent

    class Slides(val document: ByteArray) : OfficeContent
}

class OfficeDocument(val type: DocumentType, val content: OfficeContent)

/** Password maangne wali file. Galat password par [unlock] null deta hai. */
interface OfficeLock : Closeable {
    fun unlock(password: String): OfficeDocument?
}

sealed interface OleResult {
    class Ready(val document: OfficeDocument) : OleResult

    class Locked(val lock: OfficeLock) : OleResult

    /** Password hai, par aisa tareeka jo hum nahi kholte, jaise purani PPT. */
    data object PasswordUnsupported : OleResult
}

/** OLE2 file me kya hai, yeh dekh kar use khulne layak banata hai. */
class OfficeFile(private val newFile: (String) -> File) {
    /** [source] ab is class ka hai: kaam khatam hote hi delete ho jati hai. */
    fun open(source: File): OleResult {
        val ole = try {
            OleFile(source)
        } catch (e: IOException) {
            source.delete()
            throw e
        }
        var keep = false
        try {
            if (ole.has(ENCRYPTION_INFO) && ole.has(ENCRYPTED_PACKAGE)) {
                keep = true
                return OleResult.Locked(PackageLock(ole, source, ole.read(ENCRYPTION_INFO)))
            }
            return when {
                ole.has(WORD_STREAM) -> openWord(ole)
                ole.has(EXCEL_STREAM) -> openExcel(ole.read(EXCEL_STREAM))
                ole.has(POWERPOINT_STREAM) -> openPowerPoint(ole)
                // Excel 95 ki `Book` stream bhi yahin aati hai.
                else -> throw UnsupportedOffice()
            }
        } catch (e: IndexOutOfBoundsException) {
            keep = false
            throw IOException(e)
        } catch (e: IOException) {
            keep = false
            throw e
        } finally {
            if (!keep) {
                ole.close()
                source.delete()
            }
        }
    }

    private fun openWord(ole: OleFile): OleResult {
        val document = ole.read(WORD_STREAM)
        val header = WordHeader(document)
        val table = ole.read(header.tableName)
        return when {
            !header.encrypted -> OleResult.Ready(word(document, table))
            header.obfuscated -> OleResult.PasswordUnsupported
            else -> OleResult.Locked(WordLock(document, table))
        }
    }

    private fun word(document: ByteArray, table: ByteArray) =
        OfficeDocument(DocumentType.Doc, OfficeContent.Word(document, table))

    private fun openExcel(workbook: ByteArray): OleResult {
        val filePass = findFilePass(workbook) ?: return OleResult.Ready(sheet(workbook))
        if (workbook.u16(filePass) != FILE_PASS_RC4) return OleResult.PasswordUnsupported
        val lock = ExcelLock(workbook, filePass + 2)
        return lock.unlock(EXCEL_DEFAULT_PASSWORD)?.let { OleResult.Ready(it) } ?: OleResult.Locked(lock)
    }

    private fun sheet(workbook: ByteArray) = OfficeDocument(DocumentType.Xls, OfficeContent.Sheet(workbook))

    private fun findFilePass(workbook: ByteArray): Int? {
        var found: Int? = null
        workbook.forEachRecord(0) { id, body, _ ->
            if (id == FILE_PASS) found = body
            found == null && id != EOF
        }
        return found
    }

    private fun openPowerPoint(ole: OleFile): OleResult {
        if (ole.has(ENCRYPTED_SUMMARY)) return OleResult.PasswordUnsupported
        return OleResult.Ready(OfficeDocument(DocumentType.Ppt, OfficeContent.Slides(ole.read(POWERPOINT_STREAM))))
    }

    private inner class WordLock(private val document: ByteArray, private val table: ByteArray) : OfficeLock {
        override fun unlock(password: String): OfficeDocument? {
            val key = LegacyCrypto.key(table, 0, password) ?: return null
            val head = document.copyOf(WORD_PLAIN_HEAD)
            key.decrypt(document, WORD_BLOCK)
            head.copyInto(document)
            key.decrypt(table, WORD_BLOCK)
            return word(document, table)
        }

        override fun close() = Unit
    }

    private inner class ExcelLock(private val workbook: ByteArray, private val header: Int) : OfficeLock {
        override fun unlock(password: String): OfficeDocument? {
            val key = LegacyCrypto.key(workbook, header, password) ?: return null
            val plain = workbook.copyOf()
            key.decrypt(plain, EXCEL_BLOCK)
            restoreClearParts(workbook, plain)
            return sheet(plain)
        }

        override fun close() = Unit
    }

    /** RC4 poori stream par chalta hai, par record header aur kuch records kabhi encrypt nahi hote. */
    private fun restoreClearParts(original: ByteArray, plain: ByteArray) {
        original.forEachRecord(0) { id, body, length ->
            val header = body - RECORD_HEADER
            original.copyInto(plain, header, header, body)
            when (id) {
                BOF, USR_EXCL, FILE_LOCK, INTERFACE_HDR, RRD_INFO, RRD_HEAD ->
                    original.copyInto(plain, body, body, body + length)
                BOUND_SHEET -> original.copyInto(plain, body, body, body + SHEET_OFFSET_BYTES)
                // Parser FilePass dekhe to file ko phir se band samjhe, isliye iski pehchan mita do.
                FILE_PASS -> plain.fill(0, header, header + 2)
            }
            true
        }
    }

    private inner class PackageLock(
        private val ole: OleFile,
        private val source: File,
        private val info: ByteArray,
    ) : OfficeLock {
        override fun unlock(password: String): OfficeDocument? {
            val key = OoxmlCrypto.key(info, password) ?: return null
            val target = newFile(".zip")
            try {
                ole.open(ENCRYPTED_PACKAGE).use { stream ->
                    target.outputStream().use { key.decrypt(stream, it) }
                }
                return OfficeDocument(packageType(target), OfficeContent.Package(target))
            } catch (e: IOException) {
                target.delete()
                throw e
            }
        }

        private fun packageType(file: File): DocumentType = SafeZip(file).use { zip ->
            when {
                zip.exists("word/document.xml") -> DocumentType.Docx
                zip.exists("xl/workbook.xml") -> DocumentType.Xlsx
                zip.exists("ppt/presentation.xml") -> DocumentType.Pptx
                else -> throw UnsupportedOffice()
            }
        }

        override fun close() {
            ole.close()
            source.delete()
        }
    }
}
