package app.kholo.reader.docx

import androidx.compose.ui.text.AnnotatedString
import app.kholo.reader.core.file.SafeZip
import app.kholo.reader.core.file.attr
import app.kholo.reader.core.file.readRelationships
import app.kholo.reader.core.file.safeXml
import app.kholo.reader.core.file.walk
import java.io.IOException
import org.xmlpull.v1.XmlPullParser

/** Har slide ka text aur tasveerein, order me. Animation, video aur SmartArt nahi. */
class PptxParser(private val zip: SafeZip) {
    fun parse(): ParsedDocument {
        val rels = readRelationships(zip.open("ppt/_rels/presentation.xml.rels"), "ppt")
        val order = mutableListOf<String>()
        val stream = zip.open("ppt/presentation.xml") ?: throw IOException("no presentation")
        stream.use {
            val parser = safeXml(it)
            parser.walk { event ->
                if (event == XmlPullParser.START_TAG && parser.name == "sldId") {
                    parser.attr("id")?.let { _ -> relationOf(parser)?.let(rels::get)?.let(order::add) }
                }
            }
        }
        val blocks = mutableListOf<DocBlock>()
        order.forEachIndexed { index, path ->
            blocks += DocBlock.SlideHeader(index + 1)
            readSlide(path, blocks)
        }
        return ParsedDocument(blocks)
    }

    /** `sldId` me `id` (number) aur `r:id` dono hote hain. Yahan `r:` wala chahiye. */
    private fun relationOf(parser: XmlPullParser): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i).substringAfter(':') == "id" &&
                parser.getAttributeValue(i).startsWith("rId")
            ) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    private fun readSlide(path: String, blocks: MutableList<DocBlock>) {
        val stream = zip.open(path) ?: return
        val folder = path.substringBeforeLast('/')
        val file = path.substringAfterLast('/')
        val rels = readRelationships(zip.open("$folder/_rels/$file.rels"), folder)
        stream.use {
            val parser = safeXml(it)
            val text = StringBuilder()
            var inText = false
            var title = false
            parser.walk { event ->
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "sp" -> title = false
                        "ph" -> title = parser.attr("type").let { type -> type == "title" || type == "ctrTitle" }
                        "p" -> text.clear()
                        "t" -> inText = true
                        "blip" -> parser.attr("embed")?.let(rels::get)?.let { image -> blocks += DocBlock.Image(image) }
                    }
                    XmlPullParser.TEXT -> if (inText) text.append(parser.text)
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "t" -> inText = false
                        "p" -> if (text.isNotBlank()) {
                            blocks +=
                                DocBlock.Paragraph(AnnotatedString(text.toString()), heading = if (title) 2 else 0)
                        }
                    }
                }
            }
        }
    }
}
