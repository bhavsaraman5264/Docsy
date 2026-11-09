package app.kholo.reader.convert

import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocxWriterTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun written(block: (DocxWriter) -> Unit): File {
        val out = folder.newFile("out.docx")
        DocxWriter(out, folder.newFile("body.xml"), 595f, 842f).use { writer ->
            block(writer)
            writer.finish()
        }
        return out
    }

    private fun paragraph(text: String) = PageParagraph(listOf(WordRun(text, false, false, 11f)), 0, false, 0f)

    private fun ZipFile.text(name: String): String =
        getInputStream(getEntry(name)).use { it.readBytes().decodeToString() }

    private fun assertWellFormed(xml: String) {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
    }

    @Test
    fun `file has every part word needs and all xml is valid`() {
        val file = written { it.paragraph(paragraph("hello"), newPage = false) }
        ZipFile(file).use { zip ->
            listOf(
                "[Content_Types].xml",
                "_rels/.rels",
                "word/document.xml",
                "word/styles.xml",
                "word/_rels/document.xml.rels",
            ).forEach { assertWellFormed(zip.text(it)) }
            assertTrue(zip.text("word/document.xml").contains("hello"))
        }
    }

    @Test
    fun `special and control characters cannot break the file`() {
        val file = written { it.paragraph(paragraph("a < b & \"c\" \u0001\u000B end"), newPage = false) }
        ZipFile(file).use { zip ->
            val xml = zip.text("word/document.xml")
            assertWellFormed(xml)
            assertTrue(xml.contains("a &lt; b &amp; &quot;c&quot;"))
            assertFalse(xml.contains('\u0001'))
        }
    }

    @Test
    fun `hindi and emoji survive`() {
        assertEquals("नमस्ते 😀", xmlText("नमस्ते 😀"))
    }

    @Test
    fun `broken surrogate is removed`() {
        assertEquals("ab", xmlText("a\uD83Db"))
    }

    @Test
    fun `picture gets its file and link`() {
        val file = written { writer ->
            writer.picture(100f, 50f, newPage = true) { it.write(byteArrayOf(1, 2, 3)) }
        }
        ZipFile(file).use { zip ->
            assertTrue(zip.getEntry("word/media/image1.jpg") != null)
            assertTrue(zip.text("word/_rels/document.xml.rels").contains("media/image1.jpg"))
            val xml = zip.text("word/document.xml")
            assertWellFormed(xml)
            assertTrue(xml.contains("rIdImage1"))
            assertTrue(xml.contains("<w:pageBreakBefore/>"))
        }
    }

    @Test
    fun `scratch file is removed`() {
        val scratch = folder.newFile("scratch.xml")
        DocxWriter(folder.newFile("a.docx"), scratch, 595f, 842f).use { it.finish() }
        assertFalse(scratch.exists())
    }
}
