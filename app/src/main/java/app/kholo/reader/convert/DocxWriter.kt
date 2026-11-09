package app.kholo.reader.convert

import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val TWIPS_PER_POINT = 20
private const val EMU_PER_POINT = 12_700
private const val HALF_POINTS = 2
private const val SURROGATE_FREE_END = 0xD7FF
private const val PRIVATE_START = 0xE000
private const val BMP_LAST_VALID = 0xFFFD
private const val SUPPLEMENTARY_START = 0x10000
private const val SPACE = 0x20

const val DOCX_MARGIN_PT = 54f

private const val XML_HEAD = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""
private const val PACKAGE_RELS = "http://schemas.openxmlformats.org/package/2006/relationships"
private const val OFFICE_RELS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
private const val WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
private const val PICTURE_NS = "http://schemas.openxmlformats.org/drawingml/2006/picture"

/** Word ki file ek ZIP hai jisme XML hota hai, isliye iske liye koi library nahi chahiye. */
internal fun xmlText(text: String): String {
    val out = StringBuilder(text.length)
    var index = 0
    while (index < text.length) {
        val code = text.codePointAt(index)
        index += Character.charCount(code)
        when {
            code == '&'.code -> out.append("&amp;")
            code == '<'.code -> out.append("&lt;")
            code == '>'.code -> out.append("&gt;")
            code == '"'.code -> out.append("&quot;")
            isXmlChar(code) -> out.appendCodePoint(code)
        }
    }
    return out.toString()
}

/** XML me ek bhi galat akshar ho to Word poori file ko kharab bata deta hai. */
private fun isXmlChar(code: Int): Boolean =
    code == '\t'.code || code == '\n'.code || code == '\r'.code ||
        code in SPACE..SURROGATE_FREE_END ||
        code in PRIVATE_START..BMP_LAST_VALID ||
        code >= SUPPLEMENTARY_START

/**
 * DOCX likhta hai. Tasveerein seedhe ZIP me jati hain aur text [scratch] file me, taaki bada document bhi
 * memory me na aaye. Kaam ke baad [finish] zaroori hai.
 */
class DocxWriter(
    out: File,
    private val scratch: File,
    private val pageWidth: Float,
    private val pageHeight: Float,
) : Closeable {
    private val zip = ZipOutputStream(BufferedOutputStream(out.outputStream()))
    private val body: BufferedWriter = scratch.bufferedWriter(Charsets.UTF_8)
    private var pictures = 0
    private var finished = false

    val textWidth: Float get() = pageWidth - 2 * DOCX_MARGIN_PT
    val textHeight: Float get() = pageHeight - 2 * DOCX_MARGIN_PT

    init {
        entry("[Content_Types].xml", CONTENT_TYPES)
        entry("_rels/.rels", ROOT_RELS)
        entry("word/styles.xml", STYLES)
        body.write(
            XML_HEAD + """<w:document xmlns:w="$WORD_NS" xmlns:r="$OFFICE_RELS" """ +
                """xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" """ +
                """xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" """ +
                """xmlns:pic="$PICTURE_NS"><w:body>""",
        )
    }

    private fun entry(name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    fun paragraph(paragraph: PageParagraph, newPage: Boolean) {
        body.write("<w:p><w:pPr>")
        if (paragraph.heading > 0) body.write("""<w:pStyle w:val="Heading${paragraph.heading}"/>""")
        if (newPage) body.write("<w:pageBreakBefore/>")
        if (paragraph.centered) body.write("""<w:jc w:val="center"/>""")
        body.write("</w:pPr>")
        paragraph.runs.forEach(::writeRun)
        body.write("</w:p>")
    }

    private fun writeRun(run: WordRun) {
        val size = (run.size * HALF_POINTS).roundToInt().coerceAtLeast(1)
        body.write("<w:r><w:rPr>")
        if (run.bold) body.write("<w:b/><w:bCs/>")
        if (run.italic) body.write("<w:i/><w:iCs/>")
        body.write("""<w:sz w:val="$size"/><w:szCs w:val="$size"/></w:rPr>""")
        body.write("""<w:t xml:space="preserve">${xmlText(run.text)}</w:t></w:r>""")
    }

    /** [write] JPEG ke bytes likhta hai. */
    fun picture(width: Float, height: Float, newPage: Boolean, write: (OutputStream) -> Unit) {
        pictures++
        val id = pictures
        zip.putNextEntry(ZipEntry("word/media/image$id.jpg"))
        write(zip)
        zip.closeEntry()
        val cx = (width * EMU_PER_POINT).roundToLong()
        val cy = (height * EMU_PER_POINT).roundToLong()
        body.write("<w:p><w:pPr>")
        if (newPage) body.write("<w:pageBreakBefore/>")
        body.write("</w:pPr><w:r><w:drawing>")
        body.write("""<wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="$cx" cy="$cy"/>""")
        body.write("""<wp:docPr id="$id" name="Picture $id"/><a:graphic>""")
        body.write("""<a:graphicData uri="$PICTURE_NS"><pic:pic><pic:nvPicPr>""")
        body.write("""<pic:cNvPr id="$id" name="Picture $id"/><pic:cNvPicPr/></pic:nvPicPr>""")
        body.write("""<pic:blipFill><a:blip r:embed="rIdImage$id"/>""")
        body.write("""<a:stretch><a:fillRect/></a:stretch></pic:blipFill>""")
        body.write("""<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm>""")
        body.write("""<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic>""")
        body.write("</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>")
    }

    fun finish() {
        val width = (pageWidth * TWIPS_PER_POINT).roundToInt()
        val height = (pageHeight * TWIPS_PER_POINT).roundToInt()
        val margin = (DOCX_MARGIN_PT * TWIPS_PER_POINT).roundToInt()
        val orient = if (width > height) """ w:orient="landscape"""" else ""
        // Word ko body me kam se kam ek paragraph chahiye.
        body.write("<w:p/>")
        body.write("""<w:sectPr><w:pgSz w:w="$width" w:h="$height"$orient/>""")
        body.write(
            """<w:pgMar w:top="$margin" w:right="$margin" w:bottom="$margin" w:left="$margin" """ +
                """w:header="0" w:footer="0" w:gutter="0"/></w:sectPr></w:body></w:document>""",
        )
        body.close()
        zip.putNextEntry(ZipEntry("word/document.xml"))
        scratch.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
        entry("word/_rels/document.xml.rels", documentRels())
        finished = true
    }

    private fun documentRels(): String = buildString {
        append(XML_HEAD).append("""<Relationships xmlns="$PACKAGE_RELS">""")
        append("""<Relationship Id="rIdStyles" Type="$OFFICE_RELS/styles" Target="styles.xml"/>""")
        for (id in 1..pictures) {
            append("""<Relationship Id="rIdImage$id" Type="$OFFICE_RELS/image" Target="media/image$id.jpg"/>""")
        }
        append("</Relationships>")
    }

    override fun close() {
        if (!finished) body.close()
        zip.close()
        scratch.delete()
    }

    private companion object {
        const val CONTENT_TYPES = XML_HEAD +
            """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
            """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
            """<Default Extension="xml" ContentType="application/xml"/>""" +
            """<Default Extension="jpg" ContentType="image/jpeg"/>""" +
            """<Override PartName="/word/document.xml" """ +
            """ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>""" +
            """<Override PartName="/word/styles.xml" """ +
            """ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/></Types>"""

        const val ROOT_RELS = XML_HEAD + """<Relationships xmlns="$PACKAGE_RELS">""" +
            """<Relationship Id="rId1" Type="$OFFICE_RELS/officeDocument" Target="word/document.xml"/></Relationships>"""

        const val STYLES = XML_HEAD + """<w:styles xmlns:w="$WORD_NS"><w:docDefaults><w:rPrDefault><w:rPr>""" +
            """<w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Mangal"/>""" +
            """<w:sz w:val="22"/><w:szCs w:val="22"/>""" +
            """</w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="120"/></w:pPr></w:pPrDefault>""" +
            """</w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal">""" +
            """<w:name w:val="Normal"/>""" +
            """</w:style><w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/>""" +
            """<w:basedOn w:val="Normal"/><w:next w:val="Normal"/>""" +
            """<w:pPr><w:outlineLvl w:val="0"/></w:pPr></w:style>""" +
            """<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/>""" +
            """<w:basedOn w:val="Normal"/><w:next w:val="Normal"/>""" +
            """<w:pPr><w:outlineLvl w:val="1"/></w:pPr></w:style>""" +
            """<w:style w:type="paragraph" w:styleId="Heading3"><w:name w:val="heading 3"/>""" +
            """<w:basedOn w:val="Normal"/><w:next w:val="Normal"/>""" +
            """<w:pPr><w:outlineLvl w:val="2"/></w:pPr></w:style>""" +
            """</w:styles>"""
    }
}
