package app.kholo.reader.core.file

import org.junit.Assert.assertEquals
import org.junit.Test

class ZipPathTest {
    @Test
    fun `relative targets resolve against the part folder`() {
        assertEquals("word/media/a.png", resolveZipPath("word", "media/a.png"))
        assertEquals("ppt/media/a.png", resolveZipPath("ppt/slides", "../media/a.png"))
    }

    @Test
    fun `absolute targets drop the slash`() {
        assertEquals("xl/worksheets/sheet1.xml", resolveZipPath("xl", "/xl/worksheets/sheet1.xml"))
    }

    @Test
    fun `traversal cannot climb above the zip root`() {
        assertEquals("etc/passwd", resolveZipPath("word", "../../../etc/passwd"))
    }
}
