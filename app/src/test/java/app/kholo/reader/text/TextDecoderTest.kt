package app.kholo.reader.text

import java.io.File
import java.io.FileInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextDecoderTest {
    @Test
    fun `utf-8 hindi survives`() {
        assertEquals("यह हिंदी है", TextDecoder.decode("यह हिंदी है".toByteArray()))
    }

    @Test
    fun `bom is removed`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "hi".toByteArray()
        assertEquals("hi", TextDecoder.decode(bytes))
    }

    @Test
    fun `utf-16 with bom decodes`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "hi".toByteArray(Charsets.UTF_16LE)
        assertEquals("hi", TextDecoder.decode(bytes))
    }

    @Test
    fun `broken utf-8 falls back without crashing`() {
        assertEquals("café", TextDecoder.decode("caf".toByteArray() + byteArrayOf(0xE9.toByte())))
    }

    private fun pagerOver(text: String): Pair<TextPager, File> {
        val file = File.createTempFile("pager", ".txt")
        file.writeText(text)
        return TextPager(FileInputStream(file), file.length(), Charsets.UTF_8, 0L) to file
    }

    @Test
    fun `big text is cut on lines and loses nothing`() {
        val text = (1..5000).joinToString("\n") { "लाइन $it of a long file" }
        val (pager, file) = pagerOver(text)
        assertTrue(pager.chunkCount > 5)
        val joined = (0 until pager.chunkCount).joinToString("\n") { pager.chunk(it) }
        assertEquals(text, joined)
        pager.close()
        file.delete()
    }

    @Test
    fun `one giant line never splits a hindi letter`() {
        val text = "हिंदी".repeat(20_000)
        val (pager, file) = pagerOver(text)
        val joined = (0 until pager.chunkCount).joinToString("") { pager.chunk(it) }
        assertEquals(text, joined)
        pager.close()
        file.delete()
    }
}
