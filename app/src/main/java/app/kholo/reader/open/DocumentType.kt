package app.kholo.reader.open

private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
private val OLE_MAGIC = hexBytes("D0CF11E0A1B11AE1")

/** PDF ka header file ke pehle 1024 bytes me kahin bhi ho sakta hai. */
private const val PDF_HEADER_WINDOW = 1024

enum class DocumentType {
    Pdf,
    Text,
    Csv,
    Docx,
    Xlsx,
    Pptx,

    /** Purani DOC/XLS/PPT, ya password wali Office file. Dono OLE2 hoti hain. */
    LegacyOffice,
    Empty,
    Unknown,
    ;

    /** Recent list ka chhota naam. */
    val label: String
        get() = when (this) {
            Pdf -> "PDF"
            Text -> "TXT"
            Csv -> "CSV"
            Docx -> "DOC"
            Xlsx -> "XLS"
            Pptx -> "PPT"
            else -> "FILE"
        }

    companion object {
        /** Office file ka naam ya MIME nahi mila to ZIP ke andar ke folder se pehchan. */
        fun fromZipEntry(name: String): DocumentType = when {
            name.startsWith("word/") -> Docx
            name.startsWith("xl/") -> Xlsx
            name.startsWith("ppt/") -> Pptx
            else -> Unknown
        }

        /** File ke bytes extension se upar hain: `.csv` naam ki PDF, PDF hi khulegi. */
        fun detect(head: ByteArray, name: String?, mime: String?): DocumentType {
            if (head.isEmpty()) return Empty
            val extension = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
            return when {
                head.containsWithin(PDF_MAGIC, PDF_HEADER_WINDOW) -> Pdf
                head.startsWith(OLE_MAGIC) -> LegacyOffice
                head.startsWith(ZIP_MAGIC) -> zipType(extension, mime)
                head.looksBinary() -> Unknown
                extension == "csv" || extension == "tsv" || mime == "text/csv" -> Csv
                else -> Text
            }
        }

        private fun zipType(extension: String, mime: String?): DocumentType = when {
            extension == "docx" || mime?.contains("wordprocessingml") == true -> Docx
            extension == "xlsx" || extension == "xlsm" || mime?.contains("spreadsheetml") == true -> Xlsx
            extension == "pptx" || mime?.contains("presentationml") == true -> Pptx
            else -> Unknown
        }
    }
}

private const val HEX_RADIX = 16

private fun hexBytes(hex: String): ByteArray = hex.chunked(2).map { it.toInt(HEX_RADIX).toByte() }.toByteArray()

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private fun ByteArray.containsWithin(needle: ByteArray, window: Int): Boolean {
    val last = minOf(size - needle.size, window - needle.size)
    return (0..last).any { start -> needle.indices.all { this[start + it] == needle[it] } }
}

/** NUL byte text file me nahi hota, sivaye UTF-16 ke, jiska BOM alag pehchana jata hai. */
private fun ByteArray.looksBinary(): Boolean {
    val bom = take(2).map { it.toInt() and 0xFF }
    val utf16 = bom == listOf(0xFF, 0xFE) || bom == listOf(0xFE, 0xFF)
    return !utf16 && any { it == 0.toByte() }
}
