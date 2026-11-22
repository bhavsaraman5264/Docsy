package app.kholo.reader.tools

import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kaagaz dhoondhne ki jaanch nakli photo par: farsh, tirchha kaagaz, us par text. Extension ke test jaisa. */
class ScanCropTest {
    private val width = 480
    private val height = 640

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private val granite = { x: Int, y: Int ->
        val v = 50 + ((x * 7919 + y * 104729) % 97) * 40 / 97
        rgb(v, v, v + 4)
    }

    /** Photo: charon taraf [floor], [corners] wala kaagaz, kaagaz par text ki lines. */
    private fun photo(corners: FloatArray, floor: (Int, Int) -> Int): IntArray {
        val ax = corners[0]
        val ay = corners[1]
        val bx = corners[2]
        val by = corners[3]
        val dx = corners[6]
        val dy = corners[7]
        val ux = bx - ax
        val uy = by - ay
        val vx = dx - ax
        val vy = dy - ay
        val det = ux * vy - uy * vx
        return IntArray(width * height) { at ->
            val x = at % width
            val y = at / width
            if (!inside(corners, x.toFloat(), y.toFloat())) return@IntArray floor(x, y)
            val s = ((x - ax) * vy - (y - ay) * vx) / det
            val t = (ux * (y - ay) - uy * (x - ax)) / det
            val line = (t * 40).roundToInt()
            val across = s > 0.1f && s < 0.85f && (s * 60).toInt() % 7 != 6
            val text = across && t > 0.08f && t < 0.92f && abs(t * 40 - line) < 0.06f
            if (text) rgb(40, 40, 50) else rgb(232, 234, 244)
        }
    }

    private fun inside(c: FloatArray, x: Float, y: Float): Boolean = (0 until 4).all { k ->
        val n = (k + 1) % 4
        (c[n * 2] - c[k * 2]) * (y - c[k * 2 + 1]) - (c[n * 2 + 1] - c[k * 2 + 1]) * (x - c[k * 2]) >= 0
    }

    private fun assertClose(found: FloatArray?, expected: FloatArray, within: Float) {
        assertNotNull(found)
        for (i in expected.indices) {
            assertTrue("corner $i: ${found!![i]} vs ${expected[i]}", abs(found[i] - expected[i]) < within)
        }
    }

    @Test
    fun `finds a tilted sheet on a dark grainy floor`() {
        val sheet = floatArrayOf(70f, 90f, 400f, 70f, 420f, 560f, 60f, 590f)
        assertClose(findPaper(photo(sheet, granite), width, height), sheet, 9f)
    }

    @Test
    fun `finds a sheet on a light floor a little darker than the paper`() {
        val sheet = floatArrayOf(90f, 60f, 410f, 80f, 395f, 600f, 75f, 585f)
        assertClose(findPaper(photo(sheet) { _, _ -> rgb(196, 192, 186) }, width, height), sheet, 9f)
    }

    @Test
    fun `a sheet running out of the bottom is cut at the photo edge`() {
        val sheet = floatArrayOf(80f, 120f, 420f, 110f, 430f, 700f, 70f, 700f)
        val found = findPaper(photo(sheet, granite), width, height)
        assertNotNull(found)
        assertTrue(found!![5] > height - 8)
        assertTrue(found[7] > height - 8)
        assertTrue(abs(found[1] - 120) < 9)
    }

    @Test
    fun `a normal page with a printed table is never cut`() {
        val white = rgb(255, 255, 255)
        val dark = rgb(20, 20, 20)
        val page = IntArray(width * height) { at ->
            val x = at % width
            val y = at / width
            val rule = (y == 150 || y == 151 || y == 450 || y == 451) && x in 60..420
            val side = (x == 60 || x == 61 || x == 420 || x == 421) && y in 150..451
            val text = y % 18 < 2 && y in 171..429 && x in 81..399 && (x / 9) % 6 != 5
            if (rule || side || text) dark else white
        }
        assertNull(findPaper(page, width, height))
    }

    @Test
    fun `a photo that is already just the page is left alone`() {
        assertNull(findPaper(IntArray(width * height) { rgb(240, 240, 240) }, width, height))
    }

    @Test
    fun `the straight page takes the longer of opposite sides`() {
        assertArrayEquals(intArrayOf(50, 40), paperSize(floatArrayOf(0f, 0f, 50f, 0f, 48f, 40f, 2f, 38f)))
    }
}
