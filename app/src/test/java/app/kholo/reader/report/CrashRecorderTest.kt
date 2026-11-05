package app.kholo.reader.report

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashRecorderTest {
    @Test
    fun `crash trace never contains the exception message`() {
        val error = IllegalStateException("secret-file-name.pdf", IllegalArgumentException("content://private"))
        val trace = CrashRecorder.safeTrace(error)
        assertTrue(trace.contains("IllegalStateException"))
        assertTrue(trace.contains("IllegalArgumentException"))
        assertFalse(trace.contains("secret-file-name"))
        assertFalse(trace.contains("content://private"))
    }
}
