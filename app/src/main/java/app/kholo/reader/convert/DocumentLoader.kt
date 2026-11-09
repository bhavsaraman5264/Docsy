package app.kholo.reader.convert

import app.kholo.reader.core.file.SafeZip
import app.kholo.reader.core.file.readUpTo
import app.kholo.reader.docx.DocxParser
import app.kholo.reader.docx.ParsedDocument
import app.kholo.reader.docx.decodeImage
import app.kholo.reader.office.OfficeContent
import app.kholo.reader.office.OfficeFile
import app.kholo.reader.office.OleResult
import app.kholo.reader.office.UnsupportedOffice
import app.kholo.reader.office.WordParser
import java.io.Closeable
import java.io.File
import java.io.IOException

private const val MAGIC_BYTES = 4
private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B)
private val OLE_MAGIC = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte())

/** Password wali Word file. Tool me password poochhne ki jagah saaf message dete hain. */
class WordLocked : IOException()

class LoadedDocument(
    val parsed: ParsedDocument,
    val images: ImageLoader?,
    private val zip: SafeZip? = null,
) : Closeable {
    override fun close() {
        zip?.close()
    }
}

/** Word file ko PDF banane ke liye kholta hai. Extension par bharosa nahi, file ke pehle bytes dekhte hain. */
class DocumentLoader(private val newFile: (String) -> File) {
    fun load(file: File): LoadedDocument {
        val head = file.inputStream().use { it.readUpTo(MAGIC_BYTES) }
        return when {
            head.startsWith(ZIP_MAGIC) -> loadPackage(file)
            head.startsWith(OLE_MAGIC) -> loadLegacy(file)
            else -> throw UnsupportedOffice()
        }
    }

    private fun ByteArray.startsWith(magic: ByteArray): Boolean =
        size >= magic.size && magic.indices.all { this[it] == magic[it] }

    private fun loadPackage(file: File): LoadedDocument {
        val zip = SafeZip(file)
        var loaded = false
        try {
            val parsed = DocxParser(zip, forExport = true).parse()
            loaded = true
            return LoadedDocument(parsed, { path, side -> zip.decodeImage(path, side) }, zip)
        } finally {
            if (!loaded) zip.close()
        }
    }

    /** [OfficeFile] apni di hui file mita deta hai, aur user dobara try kar sakta hai, isliye copy dete hain. */
    private fun loadLegacy(file: File): LoadedDocument {
        val copy = newFile(".doc")
        file.copyTo(copy, overwrite = true)
        return when (val result = OfficeFile(newFile).open(copy)) {
            is OleResult.Ready -> {
                val content = result.document.content as? OfficeContent.Word ?: throw UnsupportedOffice()
                LoadedDocument(WordParser(content.document, content.table).parse(), null)
            }
            is OleResult.Locked -> {
                result.lock.close()
                throw WordLocked()
            }
            OleResult.PasswordUnsupported -> throw WordLocked()
        }
    }
}
