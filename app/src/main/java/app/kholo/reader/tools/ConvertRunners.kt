package app.kholo.reader.tools

import androidx.compose.ui.text.AnnotatedString
import app.kholo.reader.R
import app.kholo.reader.convert.DocumentLoader
import app.kholo.reader.convert.DocumentPdfWriter
import app.kholo.reader.convert.PdfToWord
import app.kholo.reader.core.file.SafeZip
import app.kholo.reader.docx.DocBlock
import app.kholo.reader.docx.ParsedDocument
import app.kholo.reader.text.TextDecoder

private const val WORD_PICTURE_DPI = 150
private const val MAX_TEXT_BYTES = 32L * 1024 * 1024

internal object WordToPdfRunner : ToolRunner {
    override fun run(job: ToolJob, progress: ToolProgress): ToolOutput {
        val out = job.newFile()
        val lost = DocumentLoader(job::newFile).load(job.source()).use { document ->
            DocumentPdfWriter(document.images, progress).write(document.parsed, out)
        }
        return ToolOutput(out, if (lost) R.string.convert_some_skipped else null)
    }
}

internal object TextToPdfRunner : ToolRunner {
    override fun run(job: ToolJob, progress: ToolProgress): ToolOutput {
        val source = job.source()
        if (source.length() > MAX_TEXT_BYTES) throw SafeZip.TooBig()
        val lines = TextDecoder.decode(source.readBytes()).lineSequence()
        val blocks = lines.map { DocBlock.Paragraph(AnnotatedString(it)) }.toList()
        val out = job.newFile()
        DocumentPdfWriter(null, progress).write(ParsedDocument(blocks), out)
        return ToolOutput(out)
    }
}

internal object PdfToWordRunner : ToolRunner {
    override val mime = DOCX_MIME
    override val extension = ".docx"

    override fun run(job: ToolJob, progress: ToolProgress): ToolOutput {
        val out = job.newFile(extension)
        val result = PdfToWord(job.pdf) { job.newFile(".xml") }
            .convert(job.source(), out, { job.pageImage(it, WORD_PICTURE_DPI) }, progress)
        val note = when {
            result.pictures > 0 -> R.string.word_some_pictures
            result.lostImages -> R.string.convert_some_skipped
            else -> null
        }
        return ToolOutput(out, note)
    }
}
