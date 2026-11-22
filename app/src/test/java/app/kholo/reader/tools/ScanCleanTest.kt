package app.kholo.reader.tools

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Engine ki jaanch nakli page par: kaagaz, text ki lines, ungli, chhed. Android ki zaroorat nahi. */
class ScanCleanTest {
    private val width = 450
    private val height = 640
    private val paper = rgb(236, 228, 205)
    private val ink = rgb(30, 30, 40)
    private val skin = rgb(196, 140, 110)
    private val everything = CleanChoice(CleanFix.defaults, blackWhite = false)

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun gray(pixel: Int) = luma((pixel shr 16) and 0xFF, (pixel shr 8) and 0xFF, pixel and 0xFF)

    /** Text jaisi lines: 1 pixel moti, beech-beech me khaali, [tilt] degree tedhi. */
    private fun page(tilt: Double = 0.0): IntArray {
        val pixels = IntArray(width * height) { paper }
        val slope = kotlin.math.tan(tilt * PI / 180)
        for (line in 0 until 40) {
            val base = 60 + line * 13
            for (x in 60 until width - 60) {
                if ((x / 6) % 5 == 4) continue
                val y = (base + (x - width / 2) * slope).roundToInt()
                if (y in 0 until height) pixels[y * width + x] = ink
                if (x % 3 == 0 && y + 1 in 0 until height) pixels[(y + 1) * width + x] = ink
            }
        }
        return pixels
    }

    /** Baayen kinare se andar aati ungli: 30 px moti, 110 px lambi. */
    private fun IntArray.withFinger(): IntArray {
        for (y in 300 until 330) {
            for (x in 0 until 110) this[y * width + x] = skin
        }
        for (y in 295 until 335) {
            for (x in 0 until 115) {
                if (hypot((x - 100).toDouble(), (y - 315).toDouble()) < 18) this[y * width + x] = skin
            }
        }
        return this
    }

    private fun IntArray.withHole(cx: Int, cy: Int, radius: Int): IntArray {
        for (y in cy - radius..cy + radius) {
            for (x in cx - radius..cx + radius) {
                if (hypot((x - cx).toDouble(), (y - cy).toDouble()) <= radius) this[y * width + x] = rgb(40, 40, 40)
            }
        }
        return this
    }

    private fun cleaned(pixels: IntArray, plan: CleanPlan): IntArray {
        val out = pixels.copyOf()
        val painter = PlanPainter(plan, width, height)
        for (y in 0 until height) painter.paint(out, y * width, y, null)
        return out
    }

    @Test
    fun `finger at the edge is removed and the text stays`() {
        val pixels = page().withFinger()
        val plan = planPage(pixels, width, height, everything)
        assertTrue(plan.found)
        val out = cleaned(pixels, plan)
        // Ungli ka beech ab kaagaz jaisa safed.
        assertTrue(gray(out[315 * width + 40]) > 235)
        assertTrue(gray(out[310 * width + 90]) > 235)
        // Text ki line wahi kaali.
        val textAt = 60 * width + 61
        assertTrue(gray(out[textAt]) < 90)
        // Kaagaz safed ho gaya.
        assertTrue(gray(out[30 * width + 30]) > 245)
    }

    @Test
    fun `a clean page has nothing to remove`() {
        val plan = planPage(page(), width, height, everything)
        assertFalse(plan.found)
        assertFalse(plan.blank)
        assertEquals(0f, plan.tilt)
    }

    @Test
    fun `a dark printed block in the middle is kept`() {
        val pixels = page()
        for (y in 300 until 360) {
            for (x in 150 until 300) pixels[y * width + x] = ink
        }
        val plan = planPage(pixels, width, height, everything)
        assertFalse(plan.found)
        val out = cleaned(pixels, plan)
        assertTrue(gray(out[330 * width + 220]) < 90)
    }

    @Test
    fun `punch holes near the edge go, only when asked`() {
        val pixels = page().withHole(25, 200, 7).withHole(25, 440, 7)
        assertTrue(planPage(pixels, width, height, everything).found)
        val noHoles = CleanChoice(CleanFix.defaults and CleanFix.Holes.bit.inv(), blackWhite = false)
        assertFalse(planPage(pixels, width, height, noHoles).found)
        val out = cleaned(pixels, planPage(pixels, width, height, everything))
        assertTrue(gray(out[200 * width + 25]) > 230)
    }

    @Test
    fun `fingers stay when edges are turned off`() {
        val choice = CleanChoice(CleanFix.Light.bit, blackWhite = false)
        assertFalse(planPage(page().withFinger(), width, height, choice).found)
    }

    @Test
    fun `tilted text lines are found and a straight page is left alone`() {
        val plan = planPage(page(tilt = 2.0), width, height, everything)
        assertTrue("tilt ${plan.tilt}", abs(plan.tilt - 2f) < 0.3f)
        val back = planPage(page(tilt = -1.5), width, height, everything)
        assertTrue("tilt ${back.tilt}", abs(back.tilt + 1.5f) < 0.3f)
    }

    @Test
    fun `an empty page is blank`() {
        val plan = planPage(IntArray(width * height) { paper }, width, height, everything)
        assertTrue(plan.blank)
    }

    @Test
    fun `without even light the paper keeps its colour`() {
        val choice = CleanChoice(CleanFix.Edges.bit, blackWhite = false)
        val pixels = page().withFinger()
        val out = cleaned(pixels, planPage(pixels, width, height, choice))
        val far = out[30 * width + 30]
        assertTrue(abs(gray(far) - gray(paper)) < 6)
        assertTrue(abs(gray(out[315 * width + 40]) - gray(paper)) < 12)
    }

    @Test
    fun `lone specks go, full stops stay`() {
        val w = 60
        val dark = ByteArray(w * w)
        fun fill(xs: IntRange, ys: IntRange) {
            for (y in ys) {
                for (x in xs) dark[y * w + x] = 1
            }
        }
        dark[10 * w + 10] = 1
        // Hashiye me akela 3x3 daana.
        fill(10..12, 40..42)
        // Akshar ki khadi line, aur uske 2 pixel baad full stop.
        fill(40..41, 20..34)
        fill(44..46, 32..34)
        val marks = speckles(dark, w, w)
        assertEquals(1, marks[10 * w + 10].toInt())
        assertEquals(1, marks[41 * w + 11].toInt())
        assertEquals(0, marks[33 * w + 45].toInt())
        assertEquals(0, marks[25 * w + 40].toInt())
        assertEquals(10, marks.count { it.toInt() == 1 })
    }

    @Test
    fun `a light fingernail inside the finger goes too`() {
        val pixels = page().withFinger()
        for (y in 306 until 324) {
            for (x in 88 until 106) {
                val rim = y == 306 || y == 323 || x == 88 || x == 105
                pixels[y * width + x] = if (rim) rgb(150, 100, 80) else rgb(238, 226, 214)
            }
        }
        val out = cleaned(pixels, planPage(pixels, width, height, everything))
        for (y in 300 until 330 step 2) {
            for (x in 80 until 110 step 2) assertTrue("$x,$y", gray(out[y * width + x]) > 200)
        }
    }

    @Test
    fun `the very edge of the page is cleaned too`() {
        val pixels = page()
        for (y in 0 until height) {
            for (x in 0 until 12) pixels[y * width + x] = rgb(90, 70, 50)
        }
        val out = cleaned(pixels, planPage(pixels, width, height, everything))
        for (y in 0 until height step 7) assertTrue("$y", gray(out[y * width]) > 230)
    }

    @Test
    fun `a straight finger with a light middle goes even when a dark table shows`() {
        val pixels = page()
        for (y in 0 until height) {
            for (x in 0 until width) if (y > height - 30 || x > width - 18) pixels[y * width + x] = rgb(70, 55, 40)
        }
        for (y in 290 until 340) {
            for (x in 0 until 145) {
                if (x > 120 && hypot((x - 120).toDouble(), (y - 315).toDouble()) > 25) continue
                val edge = minOf(y - 290, 339 - y, 145 - x)
                pixels[y * width + x] = if (edge < 10) rgb(168, 112, 90) else rgb(217, 170, 145)
            }
        }
        val out = cleaned(pixels, planPage(pixels, width, height, everything))
        for ((x, y) in listOf(30 to 315, 60 to 315, 100 to 300, 100 to 330, width - 5 to 100, 200 to height - 5)) {
            assertTrue("$x,$y", gray(out[y * width + x]) > 235)
        }
        assertTrue(gray(out[60 * width + 200]) < 90)
    }

    @Test
    fun `a phone shadow turns smoothly into paper and its text stays`() {
        val pixels = page()
        for (y in 250 until 450) {
            for (x in 100 until 350) {
                val p = pixels[y * width + x]
                val r = ((p shr 16) and 0xFF) * 6 / 10
                val g = ((p shr 8) and 0xFF) * 6 / 10
                val b = (p and 0xFF) * 6 / 10
                pixels[y * width + x] = rgb(r, g, b)
            }
        }
        val out = cleaned(pixels, planPage(pixels, width, height, everything))
        for ((x, y) in listOf(150 to 300, 340 to 440, 101 to 258, 99 to 258, 200 to 452)) {
            assertTrue("$x,$y", gray(out[y * width + x]) > 235)
        }
        assertTrue(gray(out[307 * width + 200]) < 110)
    }

    @Test
    fun `text stays at least as dark as it was while the paper turns white`() {
        val pixels = IntArray(width * height) { rgb(200, 196, 188) }
        for (line in 0 until 30) {
            for (x in 60 until 390) {
                if ((x / 6) % 5 == 4) continue
                for (k in 0..1) pixels[(60 + line * 16 + k) * width + x] = rgb(50, 50, 55)
            }
        }
        val out = cleaned(pixels, planPage(pixels, width, height, everything))
        assertTrue(gray(out[30 * width + 30]) > 245)
        // Photo se kabhi halka nahi; thoda gehra hi chahiye.
        assertTrue(gray(out[60 * width + 61]) <= 50)
        assertTrue(gray(out[220 * width + 200]) <= 50)
    }

    @Test
    fun `a printed box at the edge is kept`() {
        val pixels = page()
        for (y in 300 until 360) {
            for (x in 0 until 120) pixels[y * width + x] = ink
        }
        assertFalse(planPage(pixels, width, height, everything).found)
    }

    @Test
    fun `otsu splits ink from paper`() {
        val counts = IntArray(256)
        counts[40] = 100
        counts[230] = 900
        assertTrue(otsu(counts) in 40 until 230)
    }

    @Test
    fun `tilt of rotated points`() {
        val points = ArrayList<Int>()
        val radians = 3.0 * PI / 180
        for (line in 0 until 30) {
            for (x in 0 until 300 step 2) {
                points += x
                points += (40 + line * 15 + x * sin(radians) / cos(radians)).roundToInt()
            }
        }
        val tilt = tiltOf(points.toIntArray(), 400, 600)
        assertTrue("tilt $tilt", abs(tilt - 3f) < 0.2f)
    }
}
