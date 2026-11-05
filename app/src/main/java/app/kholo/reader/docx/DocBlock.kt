package app.kholo.reader.docx

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign

/** Word ka page, points me. Default A4. */
data class PageSetup(
    val width: Float = 595f,
    val height: Float = 842f,
    val marginLeft: Float = 72f,
    val marginRight: Float = 72f,
    val marginTop: Float = 72f,
    val marginBottom: Float = 72f,
)

class DocCell(val text: String, val rich: AnnotatedString, val fill: String?)

/** DOCX aur PPTX ke blocks. `rich` me Word ke asli size aur rang hain, jo page view use karta hai. */
sealed interface DocBlock {
    data class Paragraph(
        val text: AnnotatedString,
        val heading: Int = 0,
        val marker: String? = null,
        val indent: Int = 0,
        val rich: AnnotatedString = text,
        val align: TextAlign = TextAlign.Start,
        val spaceBefore: Float = 0f,
        val spaceAfter: Float = 0f,
        val leftIndent: Float = 0f,
    ) : DocBlock

    data class Table(val rows: List<List<DocCell>>, val grid: List<Float> = emptyList()) : DocBlock

    data class Image(val path: String, val widthPt: Float = 0f) : DocBlock

    data class SlideHeader(val number: Int) : DocBlock

    data object PageBreak : DocBlock

    data object Skipped : DocBlock
}

class ParsedDocument(val blocks: List<DocBlock>, val page: PageSetup = PageSetup())
