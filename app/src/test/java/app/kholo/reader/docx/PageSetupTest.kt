package app.kholo.reader.docx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageSetupTest {
    @Test
    fun `negative margins never reach the layout`() {
        val safe = PageSetup(marginTop = -5f, marginBottom = -1f, marginLeft = -72f, marginRight = -72f).sanitized()
        assertEquals(0f, safe.marginTop, 0f)
        assertEquals(0f, safe.marginBottom, 0f)
        assertEquals(0f, safe.marginLeft, 0f)
    }

    @Test
    fun `margins wider than the page fall back to a usable page`() {
        val safe = PageSetup(marginLeft = 450f, marginRight = 450f).sanitized()
        assertTrue(safe.width - safe.marginLeft - safe.marginRight > 0f)
    }

    @Test
    fun `a page with no size gets the default`() {
        val safe = PageSetup(width = 0f, height = -10f).sanitized()
        assertEquals(PageSetup().width, safe.width, 0f)
        assertEquals(PageSetup().height, safe.height, 0f)
    }

    @Test
    fun `a normal page is left alone`() {
        assertEquals(PageSetup(), PageSetup().sanitized())
    }
}
