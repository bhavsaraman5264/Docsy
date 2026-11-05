package app.kholo.reader.text

import org.junit.Assert.assertEquals
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

    @Test
    fun `chunks keep every line`() {
        val text = (1..250).joinToString("\n")
        val chunks = TextDecoder.chunks(text)
        assertEquals(3, chunks.size)
        assertEquals(text, chunks.joinToString("\n"))
    }
}
