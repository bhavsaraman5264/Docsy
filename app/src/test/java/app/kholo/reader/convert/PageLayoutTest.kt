package app.kholo.reader.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PAGE_WIDTH = 600f
private const val BODY = 11f
private const val CHAR_WIDTH = 5f

class PageLayoutTest {
    /** Ek line ke shabd, jaise PDFBox deta hai. */
    private fun line(text: String, y: Float, x: Float = 50f, size: Float = BODY, bold: Boolean = false): List<TextRun> {
        var at = x
        return text.split(" ").map { word ->
            val width = word.length * CHAR_WIDTH
            TextRun(word, at, y, width, size, bold).also { at += width + CHAR_WIDTH }
        }
    }

    private val fullLine = "aaaaaaaaaa ".repeat(9).trim()

    @Test
    fun `wrapped lines become one paragraph`() {
        val runs = line(fullLine, 100f) + line(fullLine, 114f) + line("end here", 128f)
        val paragraphs = PageLayout.paragraphs(runs, PAGE_WIDTH)
        assertEquals(1, paragraphs.size)
        assertTrue(paragraphs.single().text.endsWith("aaaaaaaaaa end here"))
    }

    @Test
    fun `big gap starts a new paragraph`() {
        val runs = line(fullLine, 100f) + line(fullLine, 160f)
        assertEquals(2, PageLayout.paragraphs(runs, PAGE_WIDTH).size)
    }

    @Test
    fun `short line ends its paragraph`() {
        val runs = line(fullLine, 100f) + line("short", 114f) + line(fullLine, 128f)
        assertEquals(2, PageLayout.paragraphs(runs, PAGE_WIDTH).size)
    }

    @Test
    fun `word broken by a hyphen at line end is joined`() {
        val runs = line("$fullLine inter-", 100f) + line("national trade", 114f)
        assertTrue(PageLayout.paragraphs(runs, PAGE_WIDTH).single().text.contains("international trade"))
    }

    @Test
    fun `large text becomes a heading`() {
        val runs = line("Annual report", 60f, size = 24f) + line(fullLine, 100f) + line(fullLine, 114f)
        val paragraphs = PageLayout.paragraphs(runs, PAGE_WIDTH)
        assertEquals(1, paragraphs.first().heading)
        assertEquals(0, paragraphs.last().heading)
    }

    @Test
    fun `bullet line never joins the line above`() {
        val runs = line(fullLine, 100f) + line("• next point", 114f)
        assertEquals(2, PageLayout.paragraphs(runs, PAGE_WIDTH).size)
    }

    @Test
    fun `two columns are read left first`() {
        val column = "cccccc ".repeat(5).trim()
        val runs = (0 until 8).flatMap { row ->
            val y = 100f + row * 14f
            line("left$row $column", y, x = 40f) + line("right$row $column", y, x = 330f)
        }
        val text = PageLayout.paragraphs(runs, PAGE_WIDTH).joinToString(" ") { it.text }
        assertTrue(text.indexOf("left7") < text.indexOf("right0"))
    }

    @Test
    fun `blank page gives nothing`() {
        assertTrue(PageLayout.paragraphs(emptyList(), PAGE_WIDTH).isEmpty())
    }

    @Test
    fun `normal text is readable`() {
        assertTrue(PageQuality.readable("यह एक साधारण हिंदी वाक्य है", legacyFont = false, imageCount = 0))
    }

    @Test
    fun `scanned page has no text`() {
        assertFalse(PageQuality.readable("", legacyFont = false, imageCount = 1))
    }

    @Test
    fun `broken codes make the page a picture`() {
        assertFalse(PageQuality.readable("����abcdefgh", legacyFont = false, imageCount = 0))
    }

    @Test
    fun `legacy hindi font makes the page a picture`() {
        assertTrue(PageQuality.isLegacyFont("ABCDEE+KrutiDev010"))
        assertFalse(PageQuality.readable("vkt dk fnu vPNk gS", legacyFont = true, imageCount = 0))
    }
}
