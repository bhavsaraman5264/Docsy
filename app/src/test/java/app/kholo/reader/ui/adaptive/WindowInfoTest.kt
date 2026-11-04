package app.kholo.reader.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowInfoTest {
    @Test
    fun `width classes follow the plan breakpoints`() {
        assertEquals(WidthClass.Compact, windowInfoFor(599, 800).widthClass)
        assertEquals(WidthClass.Medium, windowInfoFor(600, 800).widthClass)
        assertEquals(WidthClass.Medium, windowInfoFor(839, 800).widthClass)
        assertEquals(WidthClass.Expanded, windowInfoFor(840, 800).widthClass)
    }

    @Test
    fun `landscape phone counts as short height`() {
        val info = windowInfoFor(800, 360)
        assertTrue(info.isShortHeight)
        assertTrue(info.isLandscape)
    }
}
