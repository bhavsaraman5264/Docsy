package app.kholo.reader.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SafeFileBaseTest {
    @Test
    fun `normal and hindi names stay readable`() {
        assertEquals("Bank statement (1)", safeFileBase("Bank statement (1).pdf"))
        assertEquals("हिंदी फ़ाइल", safeFileBase("हिंदी फ़ाइल.pdf"))
    }

    @Test
    fun `a name can never leave the folder`() {
        listOf("../../shared_prefs/x.pdf", "..\\..\\x.pdf", "a/b.pdf", "a\u0000b.pdf").forEach {
            val safe = safeFileBase(it)
            assertFalse(safe, safe.contains('/') || safe.contains('\\') || safe.contains('\u0000'))
        }
    }

    @Test
    fun `empty or dots only falls back`() {
        assertEquals("kholo", safeFileBase(null))
        assertEquals("kholo", safeFileBase(".pdf"))
        assertEquals("kholo", safeFileBase("...pdf"))
    }
}
