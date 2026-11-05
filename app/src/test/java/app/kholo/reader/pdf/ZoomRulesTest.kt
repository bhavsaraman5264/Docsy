package app.kholo.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomRulesTest {
    @Test
    fun `fit zoom matches the value measured on the emulator`() {
        assertEquals(1.815f, ZoomRules.fitZoom(viewWidth = 1080, pageWidth = 595), 0.001f)
    }

    @Test
    fun `fit zoom survives a view that has no size yet`() {
        assertEquals(1f, ZoomRules.fitZoom(viewWidth = 0, pageWidth = 595), 0f)
        assertEquals(1f, ZoomRules.fitZoom(viewWidth = 1080, pageWidth = 0), 0f)
    }

    @Test
    fun `min zoom keeps the widest page fully visible`() {
        assertEquals(0.907f, ZoomRules.minZoom(1080, widestPageWidth = 1190), 0.001f)
    }

    @Test
    fun `max zoom never exceeds the library limit`() {
        assertEquals(25f, ZoomRules.maxZoom(minZoom = 10f), 0f)
    }

    @Test
    fun `small float noise still counts as fit`() {
        assertTrue(ZoomRules.isAtFit(zoom = 1.8151f, fit = 1.815f))
        assertFalse(ZoomRules.isAtFit(zoom = 2f, fit = 1.815f))
    }

    @Test
    fun `reader at fit stays at fit after rotating to landscape`() {
        val zoom = ZoomRules.zoomAfterResize(
            oldZoom = 1.815f,
            oldViewWidth = 1080,
            newViewWidth = 2272,
            pageWidth = 595,
            widestPageWidth = 595,
        )
        assertEquals(ZoomRules.fitZoom(2272, 595), zoom, 0.001f)
    }

    @Test
    fun `zoomed reader keeps the same zoom ratio after rotating`() {
        val zoom = ZoomRules.zoomAfterResize(
            oldZoom = 3.63f,
            oldViewWidth = 1080,
            newViewWidth = 2272,
            pageWidth = 595,
            widestPageWidth = 595,
        )
        assertEquals(ZoomRules.fitZoom(2272, 595) * 2f, zoom, 0.01f)
    }

    @Test
    fun `pinch keeps the point under the fingers in place`() {
        val content = ZoomRules.contentAt(viewCoord = 500f, scroll = 200, zoom = 2f, inset = 100)
        val scroll = ZoomRules.scrollFor(content, zoom = 4f, viewCoord = 500f, inset = 100)
        assertEquals(500f, content * 4f - scroll + 100f, 0.001f)
    }
}
