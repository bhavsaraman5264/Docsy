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
) {
    /** Word me negative aur page se bade margin mil sakte hain. Layout ko hamesha sahi values milen. */
    fun sanitized(): PageSetup {
        val defaults = PageSetup()
        val safeWidth = if (width > 0f && width <= MAX_PAGE_PT) width else defaults.width
        val safeHeight = if (height > 0f && height <= MAX_PAGE_PT) height else defaults.height
        val left = marginLeft.coerceAtLeast(0f)
        val right = marginRight.coerceAtLeast(0f)
        val top = marginTop.coerceAtLeast(0f)
        val bottom = marginBottom.coerceAtLeast(0f)
        val sidesFit = left + right < safeWidth * MAX_MARGIN_SHARE
        val endsFit = top + bottom < safeHeight * MAX_MARGIN_SHARE
        return PageSetup(
            width = safeWidth,
            height = safeHeight,
            marginLeft = if (sidesFit) left else defaults.marginLeft,
            marginRight = if (sidesFit) right else defaults.marginRight,
            marginTop = if (endsFit) top else defaults.marginTop,
            marginBottom = if (endsFit) bottom else defaults.marginBottom,
        )
    }

    private companion object {
        const val MAX_MARGIN_SHARE = 0.8f

        /** Word ka sabse bada page 22 inch hota hai. */
        const val MAX_PAGE_PT = 1584f
    }
}

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
