package app.kholo.reader.docx

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import app.kholo.reader.core.file.SafeZip
import app.kholo.reader.core.file.attr
import app.kholo.reader.core.file.readRelationships
import app.kholo.reader.core.file.safeXml
import app.kholo.reader.core.file.walk
import java.io.IOException
import org.xmlpull.v1.XmlPullParser

private const val BULLET = "•"

/** `word/document.xml` ko streaming me padh kar blocks banata hai. Word ka page layout nahi, reading view. */
class DocxParser(private val zip: SafeZip) {
    private val rels = readRelationships(zip.open("word/_rels/document.xml.rels"), "word")
    private val styleNames = readStyleNames()
    private val numbering = readNumbering()
    private val counters = mutableMapOf<String, Int>()

    fun parse(): List<DocBlock> {
        val stream = zip.open("word/document.xml") ?: throw IOException("no document")
        val blocks = mutableListOf<DocBlock>()
        stream.use { input ->
            val parser = safeXml(input)
            var tableDepth = 0
            var rows = mutableListOf<List<String>>()
            var row = mutableListOf<String>()
            val cell = StringBuilder()
            var skippedNote = false
            parser.walk { event ->
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "tbl" -> {
                            if (tableDepth == 0) rows = mutableListOf()
                            tableDepth++
                        }
                        "tr" -> if (tableDepth == 1) row = mutableListOf()
                        "tc" -> if (tableDepth == 1) cell.clear()
                        "p" -> {
                            val paragraph = readParagraph(parser, blocks)
                            if (tableDepth > 0) {
                                if (cell.isNotEmpty()) cell.append('\n')
                                cell.append(paragraph?.text?.text.orEmpty())
                            } else if (paragraph != null) {
                                blocks += paragraph
                            }
                        }
                        "txbxContent", "chart", "oMath" -> if (!skippedNote) {
                            blocks += DocBlock.Skipped
                            skippedNote = true
                        }
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    when (parser.name) {
                        "tc" -> if (tableDepth == 1) row += cell.toString()
                        "tr" -> if (tableDepth == 1) rows += row
                        "tbl" -> {
                            tableDepth--
                            if (tableDepth == 0 && rows.isNotEmpty()) blocks += DocBlock.Table(rows)
                        }
                    }
                }
            }
        }
        return blocks
    }

    private class Run(var bold: Boolean = false, var italic: Boolean = false, var underline: Boolean = false)

    /** `<w:p>` ke END tak padhta hai. Tasveerein [blocks] me seedhe jati hain. */
    private fun readParagraph(parser: XmlPullParser, blocks: MutableList<DocBlock>): DocBlock.Paragraph? {
        val builder = AnnotatedString.Builder()
        var heading = 0
        var marker: String? = null
        var indent = 0
        var numId: String? = null
        var run = Run()
        var depth = 1
        var inRunProps = false
        var inText = false
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    when (parser.name) {
                        "pStyle" -> heading = headingOf(parser.attr("val"))
                        "numId" -> numId = parser.attr("val")
                        "ilvl" -> indent = parser.attr("val")?.toIntOrNull() ?: 0
                        "r" -> run = Run()
                        "rPr" -> inRunProps = true
                        "b" -> if (inRunProps) run.bold = parser.attr("val") !in setOf("0", "false")
                        "i" -> if (inRunProps) run.italic = parser.attr("val") !in setOf("0", "false")
                        "u" -> if (inRunProps) run.underline = parser.attr("val") != "none"
                        "t" -> inText = true
                        "tab" -> if (!inRunProps) builder.append('\t')
                        "br" -> builder.append('\n')
                        "blip" -> parser.attr("embed")?.let(rels::get)?.let { blocks += DocBlock.Image(it) }
                    }
                }
                XmlPullParser.TEXT -> if (inText) builder.styled(parser.text, run)
                XmlPullParser.END_TAG -> {
                    depth--
                    when (parser.name) {
                        "rPr" -> inRunProps = false
                        "t" -> inText = false
                    }
                }
                XmlPullParser.END_DOCUMENT -> depth = 0
            }
        }
        numId?.takeIf { it != "0" }?.let { marker = markerFor(it, indent) }
        val text = builder.toAnnotatedString()
        return if (text.isEmpty()) null else DocBlock.Paragraph(text, heading, marker, indent)
    }

    private fun AnnotatedString.Builder.styled(text: String, run: Run) {
        val style = SpanStyle(
            fontWeight = if (run.bold) FontWeight.Bold else null,
            fontStyle = if (run.italic) FontStyle.Italic else null,
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

    private fun readStyleNames(): Map<String, String> {
        val stream = zip.open("word/styles.xml") ?: return emptyMap()
        val names = mutableMapOf<String, String>()
        var current: String? = null
        stream.use {
            val parser = safeXml(it)
            parser.walk { event ->
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "style" -> current = parser.attr("styleId")
                        "name" -> current?.let { id -> names[id] = parser.attr("val").orEmpty().lowercase() }
                    }
                }
            }
        }
        return names
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
