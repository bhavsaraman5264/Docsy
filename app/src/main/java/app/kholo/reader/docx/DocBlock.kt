package app.kholo.reader.docx

import androidx.compose.ui.text.AnnotatedString

/** Reading view ke blocks. DOCX aur PPTX dono yahi banate hain. */
sealed interface DocBlock {
    data class Paragraph(
        val text: AnnotatedString,
        val heading: Int = 0,
        val marker: String? = null,
        val indent: Int = 0,
    ) :
        DocBlock

    data class Table(val rows: List<List<String>>) : DocBlock

    data class Image(val path: String) : DocBlock

    data class SlideHeader(val number: Int) : DocBlock

    data object Skipped : DocBlock
}
