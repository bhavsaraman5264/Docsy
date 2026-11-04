package app.kholo.reader.core.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RingBufferTreeTest {
    private fun event(n: Int, level: LogLevel = LogLevel.I) = LogEvent(n.toLong(), level, LogTag.PDF, "e$n")

    @Test
    fun `keeps only the newest events`() {
        val tree = RingBufferTree(capacity = 2)
        (1..3).forEach { tree.log(event(it)) }
        assertEquals(listOf("e2", "e3"), tree.snapshot().map { it.message })
    }

    @Test
    fun `drops events below the minimum level`() {
        val tree = RingBufferTree(capacity = 2, minLevel = LogLevel.I)
        tree.log(event(1, LogLevel.D))
        assertEquals(emptyList<LogEvent>(), tree.snapshot())
        assertFalse(tree.isLoggable(LogLevel.D))
    }
}
