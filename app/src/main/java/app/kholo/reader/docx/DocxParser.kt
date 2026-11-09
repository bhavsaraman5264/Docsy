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
private const val FIRST_UPDATE_BLOCKS = 100
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

/**
 * `word/document.xml` ko streaming me padh kar blocks banata hai. [forExport] par header, footer,
 * footnote aur page number bhi padhe jate hain, jo PDF banane ke liye chahiye. Reader ko inki zaroorat nahi.
 */
class DocxParser(private val zip: SafeZip, private val forExport: Boolean = false) {
    private val rels = readRelationships(zip.open("word/_rels/document.xml.rels"), "word")
    private val styleNames = mutableMapOf<String, String>()
    private val styleProps = mutableMapOf<String, TextProps>()
    private var defaultSize = DEFAULT_SIZE_PT
    private val numbering: Map<String, List<Boolean>>
    private val counters = mutableMapOf<String, Int>()
    private var page = PageSetup()

    /** Paragraph ke text ke baad aayi tasveerein. Paragraph judne ke baad hi block bante hain. */
    private val afterParagraph = mutableListOf<DocBlock>()
    private val noteIds = mutableListOf<String>()

    init {
        readStyles()
        numbering = readNumbering()
    }

    @Suppress("CyclomaticComplexMethod") // XML tag ka switch, todne se padhna mushkil hoga.
    fun parse(onProgress: (ParsedDocument) -> Unit = {}): ParsedDocument {
        val stream = zip.open("word/document.xml") ?: throw IOException("no document")
        val blocks = mutableListOf<DocBlock>()
        var nextUpdate = FIRST_UPDATE_BLOCKS
        var headerPath: String? = null
        var footerPath: String? = null
        stream.use { input ->
            val parser = safeXml(input)
            var depth = 0
            var rows = mutableListOf<List<DocCell>>()
            var row = mutableListOf<DocCell>()
            var grid = mutableListOf<Float>()
            val cellText = StringBuilder()
            var cellRich = AnnotatedString.Builder()
            var cellFill: String? = null
            var cellSpan = 1
            var cellMerged = false
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
                            cellSpan = 1
                            cellMerged = false
                        }
                        "shd" -> if (depth == 1) cellFill = parser.attr("fill")?.takeIf { it != "auto" }
                        "gridSpan" -> if (depth == 1) {
                            cellSpan = (parser.attr("val")?.toIntOrNull() ?: 1).coerceIn(1, MAX_CELL_SPAN)
                        }
                        "vMerge" -> if (depth == 1) cellMerged = parser.attr("val") != "restart"
                        "headerReference" -> if (isDefaultPart(parser) && headerPath == null) {
                            headerPath = parser.attr("id")?.let(rels::get)
                        }
                        "footerReference" -> if (isDefaultPart(parser) && footerPath == null) {
                            footerPath = parser.attr("id")?.let(rels::get)
                        }
                        "p" -> {
                            val paragraph = readParagraph(parser, blocks)
                            when {
                                depth == 0 -> if (paragraph != null) {
                                    blocks += paragraph
                                    blocks += afterParagraph
                                    afterParagraph.clear()
                                    if (blocks.size >= nextUpdate) {
                                        nextUpdate *= 2
                                        onProgress(ParsedDocument(blocks.toList(), page.sanitized()))
                                    }
                                }
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
                            row += DocCell(
                                cellText.toString(),
                                cellRich.toAnnotatedString(),
                                cellFill,
                                cellSpan,
                                cellMerged,
                            )
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
        blocks += afterParagraph
        afterParagraph.clear()
        return ParsedDocument(blocks, page.sanitized(), readPart(headerPath), readPart(footerPath), readNotes())
    }

    private fun isDefaultPart(parser: XmlPullParser): Boolean = forExport && parser.attr("type") == "default"

    /** Header ya footer ki file. Inki tasveerein alag rels me hoti hain, isliye chhod dete hain. */
    private fun readPart(path: String?): List<DocBlock.Paragraph> {
        val stream = path?.let(zip::open) ?: return emptyList()
        val paragraphs = mutableListOf<DocBlock.Paragraph>()
        val ignored = mutableListOf<DocBlock>()
        stream.use { input ->
            val parser = safeXml(input)
            parser.walk { event ->
                if (event == XmlPullParser.START_TAG && parser.name == "p") {
                    readParagraph(parser, ignored)?.let(paragraphs::add)
                }
            }
        }
        afterParagraph.clear()
        return paragraphs
    }

    /** Footnote page ke neeche ki jagah document ke aakhir me aate hain, usi number ke saath. */
    private fun readNotes(): List<DocBlock.Paragraph> {
        if (noteIds.isEmpty()) return emptyList()
        val stream = zip.open("word/footnotes.xml") ?: return emptyList()
        val texts = mutableMapOf<String, MutableList<DocBlock.Paragraph>>()
        val ignored = mutableListOf<DocBlock>()
        var current: String? = null
        stream.use { input ->
            val parser = safeXml(input)
            parser.walk { event ->
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "footnote" -> current = parser.attr("id")
                        "p" -> {
                            val paragraph = readParagraph(parser, ignored)
                            val id = current
                            if (paragraph != null && id != null) texts.getOrPut(id) { mutableListOf() } += paragraph
                        }
                    }
                }
            }
        }
        afterParagraph.clear()
        return noteIds.flatMapIndexed { index, id ->
            texts[id].orEmpty().mapIndexed { line, paragraph ->
                if (line == 0) paragraph.numbered(index + 1) else paragraph
            }
        }
    }

    private fun DocBlock.Paragraph.numbered(number: Int): DocBlock.Paragraph {
        val mark = AnnotatedString("[$number] ")
        return copy(text = mark + text, rich = mark + rich)
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

    /** Word ka PAGE ya NUMPAGES field. File me iska purana nateeja likha hota hai, jo hamare kaam ka nahi. */
    private class FieldState {
        var inInstruction = false
        var mark: Char? = null
        var skipping = false
    }

    private fun fieldMark(instruction: String?): Char? =
        when (instruction.orEmpty().trim().substringBefore(' ').uppercase()) {
            "PAGE" -> PAGE_NUMBER_MARK
            "NUMPAGES" -> PAGE_TOTAL_MARK
            else -> null
        }

    /** `<w:p>` ke END tak padhta hai. Tasveerein aur page break [blocks] me seedhe jate hain. */
    @Suppress("CyclomaticComplexMethod")
    private fun readParagraph(parser: XmlPullParser, blocks: MutableList<DocBlock>): DocBlock.Paragraph? {
        val plain = AnnotatedString.Builder()
        val rich = AnnotatedString.Builder()
        val state = ParagraphState()
        val field = FieldState()
        var run = Run()
        val emit = { text: String ->
            plain.append(text, run)
            rich.appendRich(text, run, state.styleId)
        }
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
                        "footnoteReference" -> if (forExport) {
                            parser.attr("id")?.let { noteIds += it }
                            emit("[${noteIds.size}]")
                        }
                        "instrText" -> field.inInstruction = forExport
                        "fldSimple" -> if (forExport) {
                            fieldMark(parser.attr("instr"))?.let {
                                emit(it.toString())
                                field.skipping = true
                            }
                        }
                        "fldChar" -> if (forExport) {
                            val mark = field.mark
                            when (parser.attr("fldCharType")) {
                                "separate" -> if (mark != null) {
                                    emit(mark.toString())
                                    field.skipping = true
                                }
                                "end" -> {
                                    if (mark != null && !field.skipping) emit(mark.toString())
                                    field.mark = null
                                    field.skipping = false
                                }
                            }
                        }
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
                            val image = DocBlock.Image(it, state.imageWidth)
                            if (forExport && plain.length > 0) afterParagraph += image else blocks += image
                        }
                    }
                }
                XmlPullParser.TEXT -> when {
                    field.inInstruction -> field.mark = fieldMark(parser.text) ?: field.mark
                    inText && !field.skipping -> emit(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    depth--
                    when (parser.name) {
                        "pPr" -> inParagraphProps = false
                        "rPr" -> inRunProps = false
                        "t" -> inText = false
                        "instrText" -> field.inInstruction = false
                        "fldSimple" -> field.skipping = false
                    }
                }
                XmlPullParser.END_DOCUMENT -> depth = 0
            }
        }
        // Yeh Word ka pichhla page hisaab hai. PDF me hum khud page banate hain.
        if (state.pageBreakBefore && !forExport) blocks += DocBlock.PageBreak
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
        const val MAX_CELL_SPAN = 63
    }
}

/** `FF0000` jaisa hex. `auto` ya kharab value par null. */
internal fun parseColor(value: String): Color? {
    val rgb = value.toLongOrNull(HEX_RADIX) ?: return null
    return Color(OPAQUE or rgb)
}
