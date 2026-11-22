package app.kholo.reader.tools

import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.util.Matrix
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NewToolsTest {
    private val fakeJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())

    @Test
    fun `a small photo is padded exactly to the form minimum without touching the image`() {
        for (min in listOf(4L * 1024, 20L * 1024, 70_000L, 200L * 1024)) {
            val padded = padJpeg(fakeJpeg, min)
            assertTrue("$min gave ${padded.size}", padded.size >= min && padded.size <= min + 4)
            assertEquals(0xFF.toByte(), padded[0])
            assertEquals(0xD8.toByte(), padded[1])
            assertEquals(0xFE.toByte(), padded[3])
            // Asli JPEG data aakhir me jaisa ka taisa.
            assertArrayEquals(fakeJpeg.copyOfRange(2, fakeJpeg.size), padded.copyOfRange(padded.size - 5, padded.size))
        }
    }

    @Test
    fun `padding leaves big enough files and non jpeg bytes alone`() {
        assertSame(fakeJpeg, padJpeg(fakeJpeg, 3))
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        assertSame(png, padJpeg(png, 10_000))
    }

    @Test
    fun `every comment segment fits the jpeg length limit`() {
        val padded = padJpeg(fakeJpeg, 300_000)
        var at = 2
        while (padded[at] == 0xFF.toByte() && padded[at + 1] == 0xFE.toByte()) {
            val length = (padded[at + 2].toInt() and 0xFF shl 8) or (padded[at + 3].toInt() and 0xFF)
            assertTrue(length in 2..65_535)
            at += 2 + length
        }
        assertEquals(0x01.toByte(), padded[at])
    }

    @Test
    fun `form limits work for portals that count a KB as 1000 or 1024 bytes`() {
        val ibps = PhotoPreset.IbpsPhoto.spec
        assertEquals(50_000L, ibps.maxBytes)
        assertEquals(20L * 1024, ibps.minBytes)
        assertTrue(ibps.hasSize)
        assertEquals(PhotoFit.Pad, PhotoPreset.IbpsSign.spec.fit)
        PhotoPreset.entries.forEach { assertTrue(it.name, it.spec.minKb < it.spec.maxKb) }
    }

    @Test
    fun `own size needs a sensible range and both sides`() {
        assertEquals(PhotoSpec(minKb = 0, maxKb = 50), CustomSize().spec())
        assertEquals(PhotoSpec(10, 30, 200, 230), CustomSize("10", "30", "200", "230").spec())
        assertNull(CustomSize(maxKb = "").spec())
        assertNull(CustomSize(minKb = "60", maxKb = "50").spec())
        assertNull(CustomSize(width = "200").spec())
        assertNull(CustomSize(width = "99999", height = "100").spec())
        assertNotNull(CustomSize(maxKb = "5000").spec())
    }

    @Test
    fun `id card and photo tools cap how many photos they take`() {
        assertEquals(2, ToolKind.IdCard.maxInputs)
        assertTrue(ToolKind.IdCard.multipleInputs)
        assertTrue(ToolKind.IdCard.usesScanner && ToolKind.Scan.usesScanner)
        assertEquals("id-card", ToolKind.IdCard.fixedName)
        assertNull(ToolKind.Watermark.fixedName)
    }

    private fun Matrix.map(x: Float, y: Float): Pair<Float, Float> =
        (scaleX * x + shearX * y + translateX) to (shearY * x + scaleY * y + translateY)

    /** Dikhne wala neeche-baayan aur upar-daayan kona, asli page ke kis kone par aata hai. */
    private fun corners(rotation: Int): Triple<VisiblePage, Pair<Float, Float>, Pair<Float, Float>> {
        val page = PDPage(PDRectangle(10f, 20f, 600f, 800f)).apply { this.rotation = rotation }
        val visible = VisiblePage(page)
        return Triple(visible, visible.matrix.map(0f, 0f), visible.matrix.map(visible.width, visible.height))
    }

    @Test
    fun `stamps follow a rotated page so numbers stay at the bottom the reader sees`() {
        val (upright, uBottom, uTop) = corners(0)
        assertEquals(600f to 800f, upright.width to upright.height)
        assertEquals(10f to 20f, uBottom)
        assertEquals(610f to 820f, uTop)

        // 90: page ghadi ki disha me ghumta hai, isliye dikhne wala neeche-baayan asli neeche-daayan hai.
        val (turned, tBottom, tTop) = corners(90)
        assertEquals(800f to 600f, turned.width to turned.height)
        assertEquals(610f to 20f, tBottom)
        assertEquals(10f to 820f, tTop)

        val (_, hBottom, hTop) = corners(180)
        assertEquals(610f to 820f, hBottom)
        assertEquals(10f to 20f, hTop)

        val (back, bBottom, bTop) = corners(-90)
        assertEquals(800f to 600f, back.width to back.height)
        assertEquals(10f to 820f, bBottom)
        assertEquals(610f to 20f, bTop)
    }
}
