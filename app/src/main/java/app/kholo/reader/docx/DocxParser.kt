package app.kholo.reader.docx

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import app.kholo.reader.core.file.SafeZip
import app.kholo.reader.core.file.attr
import app.kholo.reader.core.file.readRelationships
import app.kholo.reader.core.file.safeXml
import app.kholo.reader.core.file.walk
import java.io.IOException
import org.xmlpull.v1.XmlPullParser

private const val BULLET = "•"
private const val TWIPS_PER_POINT = 20f
private const val HALF_POINTS = 2f
private const val EMU_PER_POINT = 12_700f
private const val DEFAULT_SIZE_PT = 11f
private const val MAX_FONT_PT = 200f
private const val MAX_LIST_LEVEL = 8
private const val HEX_RADIX = 16
private const val OPAQUE = 0xFF000000L

private data class TextProps(
    val size: Float? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val color: String? = null,
)

private class Run(var props: TextProps = TextProps(), var underline: Boolean = false)

/** `word/document.xml` ko streaming me padh kar blocks banata hai. */
class DocxParser(private val zip: SafeZip) {
    private val rels = readRelationships(zip.open("word/_rels/document.xml.rels"), "word")
    private val styleNames = mutableMapOf<String, String>()
    private val styleProps = mutableMapOf<String, TextProps>()
    private var defaultSize = DEFAULT_SIZE_PT
    private val numbering: Map<String, List<Boolean>>
    private val counters = mutableMapOf<String, Int>()
    private var page = PageSetup()

    init {
        readStyles()
        numbering = readNumbering()
    }

    @Suppress("CyclomaticComplexMethod") // XML tag ka switch, todne se padhna mushkil hoga.
    fun parse(): ParsedDocument {
        val stream = zip.open("word/document.xml") ?: throw IOException("no document")
        val blocks = mutableListOf<DocBlock>()
        stream.use { input ->
            val parser = safeXml(input)
            var depth = 0
            var rows = mutableListOf<List<DocCell>>()
            var row = mutableListOf<DocCell>()
            var grid = mutableListOf<Float>()
            val cellText = StringBuilder()
            var cellRich = AnnotatedString.Builder()
            var cellFill: String? = null
            var skippedNote = false
            parser.walk { event ->
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "tbl" -> {
                            if (depth == 0) {
                                rows = mutableListOf()
                                grid = mutableListOf()
                            }
                            depth++
                        }
                        "gridCol" -> if (depth == 1) grid += distance(parser.attr("w"))
                        "tr" -> if (depth == 1) row = mutableListOf()
                        "tc" -> if (depth == 1) {
                            cellText.clear()
                            cellRich = AnnotatedString.Builder()
                            cellFill = null
                        }
                        "shd" -> if (depth == 1) cellFill = parser.attr("fill")?.takeIf { it != "auto" }
                        "p" -> {
                            val paragraph = readParagraph(parser, blocks)
                            when {
                                depth == 0 -> if (paragraph != null) blocks += paragraph
                                paragraph != null -> {
                                    if (cellText.isNotEmpty()) {
                                        cellText.append('\n')
                                        cellRich.append('\n')
                                    }
                                    cellText.append(paragraph.text.text)
                                    cellRich.append(paragraph.rich)
                                }
                            }
                        }
                        "pgSz" -> page = page.copy(
                            width = twips(parser.attr("w")).ifZero(page.width),
                            height = twips(parser.attr("h")).ifZero(page.height),
                        )
                        "pgMar" -> page = page.copy(
                            marginLeft = twips(parser.attr("left")).ifZero(page.marginLeft),
                            marginRight = twips(parser.attr("right")).ifZero(page.marginRight),
                            marginTop = twips(parser.attr("top")).ifZero(page.marginTop),
                            marginBottom = twips(parser.attr("bottom")).ifZero(page.marginBottom),
                        )
                        "txbxContent", "chart", "oMath" -> if (!skippedNote) {
                            blocks += DocBlock.Skipped
                            skippedNote = true
                        }
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    when (parser.name) {
                        "tc" -> if (depth == 1) {
                            row += DocCell(cellText.toString(), cellRich.toAnnotatedString(), cellFill)
                        }
                        "tr" -> if (depth == 1) rows += row
                        "tbl" -> {
                            depth--
                            if (depth == 0 && rows.isNotEmpty()) blocks += DocBlock.Table(rows, grid)
                        }
                    }
                }
            }
        }
        return ParsedDocument(blocks, page.sanitized())
    }

    private fun Float.ifZero(other: Float) = if (this > 0f) this else other

    private fun twips(value: String?): Float = number(value) / TWIPS_PER_POINT

    /** `NaN` aur `Infinity` bhi Float ban jate hain, par layout me crash karte hain. */
    private fun number(value: String?): Float = value?.toFloatOrNull()?.takeIf { it.isFinite() } ?: 0f

    /** Negative indent Word me valid hai (margin se bahar), par layout me jagah negative nahi ho sakti. */
    private fun distance(value: String?): Float = twips(value).coerceAtLeast(0f)

    private class ParagraphState {
        var styleId: String? = null
        var numId: String? = null
        var level = 0
        var align = TextAlign.Start
        var before = 0f
        var after = 0f
        var left = 0f
        var imageWidth = 0f
        var pageBreakBefore = false
    }

    /** `<w:p>` ke END tak padhta hai. Tasveerein aur page break [blocks] me seedhe jate hain. */
    @Suppress("CyclomaticComplexMethod")
    private fun readParagraph(parser: XmlPullParser, blocks: MutableList<DocBlock>): DocBlock.Paragraph? {
        val plain = AnnotatedString.Builder()
        val rich = AnnotatedString.Builder()
        val state = ParagraphState()
        var run = Run()
        var depth = 1
        var inRunProps = false
        var inParagraphProps = false
        var inText = false
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    when (parser.name) {
                        "pPr" -> inParagraphProps = true
                        "pStyle" -> state.styleId = parser.attr("val")
                        "numId" -> state.numId = parser.attr("val")
                        "ilvl" -> state.level = (parser.attr("val")?.toIntOrNull() ?: 0).coerceIn(0, MAX_LIST_LEVEL)
                        "jc" -> state.align = alignOf(parser.attr("val"))
                        "spacing" -> if (inParagraphProps) {
                            state.before = distance(parser.attr("before"))
                            state.after = distance(parser.attr("after"))
                        }
                        "ind" -> if (inParagraphProps) {
                            state.left =
                                distance(parser.attr("left") ?: parser.attr("start"))
                        }
                        "r" -> run = Run()
                        "rPr" -> inRunProps = !inParagraphProps
                        "b" -> if (inRunProps) run.props = run.props.copy(bold = isOn(parser))
                        "i" -> if (inRunProps) run.props = run.props.copy(italic = isOn(parser))
                        "u" -> if (inRunProps) run.underline = parser.attr("val") != "none"
                        "sz" -> if (inRunProps) run.props = run.props.copy(size = halfPoints(parser.attr("val")))
                        "color" -> if (inRunProps) run.props = run.props.copy(color = parser.attr("val"))
                        "t" -> inText = true
                        "tab" -> if (!inParagraphProps) {
                            plain.append('\t')
                            rich.append('\t')
                        }
                        "br" -> if (parser.attr("type") == "page") {
                            blocks += DocBlock.PageBreak
                        } else {
                            plain.append('\n')
                            rich.append('\n')
                        }
                        "lastRenderedPageBreak" -> state.pageBreakBefore = true
                        "extent" -> state.imageWidth = number(parser.attr("cx")).coerceAtLeast(0f) / EMU_PER_POINT
                        "blip" -> parser.attr("embed")?.let(rels::get)?.let {
                            blocks +=
                                DocBlock.Image(it, state.imageWidth)
                        }
                    }
                }
                XmlPullParser.TEXT -> if (inText) {
                    plain.append(parser.text, run)
                    rich.appendRich(parser.text, run, state.styleId)
                }
                XmlPullParser.END_TAG -> {
                    depth--
                    when (parser.name) {
                        "pPr" -> inParagraphProps = false
                        "rPr" -> inRunProps = false
                        "t" -> inText = false
                    }
                }
                XmlPullParser.END_DOCUMENT -> depth = 0
            }
        }
        if (state.pageBreakBefore) blocks += DocBlock.PageBreak
        val text = plain.toAnnotatedString()
        if (text.isEmpty()) return null
        return DocBlock.Paragraph(
            text = text,
            heading = headingOf(state.styleId),
            marker = state.numId?.takeIf { it != "0" }?.let { markerFor(it, state.level) },
            indent = state.level,
            rich = rich.toAnnotatedString(),
            align = state.align,
            spaceBefore = state.before,
            spaceAfter = state.after,
            leftIndent = state.left,
        )
    }

    private fun isOn(parser: XmlPullParser): Boolean = parser.attr("val") !in setOf("0", "false")

    private fun halfPoints(value: String?): Float? =
        number(value).takeIf { it > 0f }?.div(HALF_POINTS)?.coerceAtMost(MAX_FONT_PT)

    private fun alignOf(value: String?): TextAlign = when (value) {
        "center" -> TextAlign.Center
        "right", "end" -> TextAlign.End
        "both", "distribute" -> TextAlign.Justify
        else -> TextAlign.Start
    }

    private fun AnnotatedString.Builder.append(text: String, run: Run) {
        val style = SpanStyle(
            fontWeight = if (run.props.bold == true) FontWeight.Bold else null,
            fontStyle = if (run.props.italic == true) FontStyle.Italic else null,
            textDecoration = if (run.underline) TextDecoration.Underline else null,
        )
        withStyle(style) { append(text) }
    }

    /** Run ki apni formatting pehle, phir paragraph style, phir document ka default. */
    private fun AnnotatedString.Builder.appendRich(text: String, run: Run, styleId: String?) {
        val base = styleProps[styleId] ?: TextProps()
        val size = run.props.size ?: base.size ?: defaultSize
        val bold = run.props.bold ?: base.bold ?: false
        val italic = run.props.italic ?: base.italic ?: false
        val color = (run.props.color ?: base.color)?.let(::parseColor) ?: Color.Black
        val style = SpanStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = if (run.underline) TextDecoration.Underline else null,
        )
        withStyle(style) { append(text) }
    }

    private fun headingOf(styleId: String?): Int {
        val name = styleNames[styleId] ?: styleId.orEmpty().lowercase()
        return when {
            name == "title" -> 1
            name.startsWith("heading") -> name.filter(Char::isDigit).toIntOrNull()?.coerceIn(1, HEADING_LEVELS) ?: 1
            else -> 0
        }
    }

    private fun markerFor(numId: String, level: Int): String {
        val numbered = numbering[numId]?.getOrNull(level) == true
        if (!numbered) return BULLET
        val key = "$numId:$level"
        val value = (counters[key] ?: 0) + 1
        counters[key] = value
        return "$value."
    }

    private fun readStyles() {
        val stream = zip.open("word/styles.xml") ?: return
        var current: String? = null
        var inDefaults = false
        stream.use {
            val parser = safeXml(it)
            parser.walk { event ->
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "rPrDefault" -> inDefaults = true
                        "style" -> current = parser.attr("styleId")
                        "name" -> current?.let { id -> styleNames[id] = parser.attr("val").orEmpty().lowercase() }
                        "sz" -> {
                            val size = halfPoints(parser.attr("val"))
                            if (inDefaults && size != null) defaultSize = size
                            updateStyle(current) { props -> props.copy(size = size) }
                        }
                        "b" -> updateStyle(current) { props -> props.copy(bold = isOn(parser)) }
                        "i" -> updateStyle(current) { props -> props.copy(italic = isOn(parser)) }
                        "color" -> updateStyle(current) { props -> props.copy(color = parser.attr("val")) }
                    }
                } else if (event == XmlPullParser.END_TAG && parser.name == "rPrDefault") {
                    inDefaults = false
                }
            }
        }
    }

    private fun updateStyle(id: String?, change: (TextProps) -> TextProps) {
        if (id != null) styleProps[id] = change(styleProps[id] ?: TextProps())
    }

    /** numId se har level ka "numbered hai ya bullet". */
    private fun readNumbering(): Map<String, List<Boolean>> {
        val stream = zip.open("word/numbering.xml") ?: return emptyMap()
        val abstract = mutableMapOf<String, MutableList<Boolean>>()
        val links = mutableMapOf<String, String>()
        var currentAbstract: String? = null
        var currentNum: String? = null
        stream.use {
            val parser = safeXml(it)
            parser.walk { event ->
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "abstractNum" -> currentAbstract = parser.attr("abstractNumId")
                        "numFmt" -> currentAbstract?.let { id ->
                            abstract.getOrPut(id) { mutableListOf() } += parser.attr("val") != "bullet"
                        }
                        "num" -> currentNum = parser.attr("numId")
                        "abstractNumId" -> currentNum?.let { num -> parser.attr("val")?.let { links[num] = it } }
                    }
                }
            }
        }
        return links.mapValues { abstract[it.value].orEmpty() }
    }

    private companion object {
        const val HEADING_LEVELS = 6
    }
}

/** `FF0000` jaisa hex. `auto` ya kharab value par null. */
internal fun parseColor(value: String): Color? {
    val rgb = value.toLongOrNull(HEX_RADIX) ?: return null
    return Color(OPAQUE or rgb)
}
