package app.kholo.reader.tools

import app.kholo.reader.core.AppError
import app.kholo.reader.core.file.toAppError
import java.io.FileNotFoundException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolEdgeCasesTest {
    @Test
    fun `very long names are cut`() {
        assertTrue(safeFileBase("x".repeat(400) + ".pdf").length <= 80)
    }

    @Test
    fun `a full disk is not called a broken file`() {
        assertEquals(AppError.NoSpace, IOException("write failed: ENOSPC (No space left on device)").toAppError())
        assertEquals(AppError.NoSpace, FileNotFoundException("open failed: ENOSPC").toAppError())
        assertEquals(AppError.Corrupt, IOException("bad xref").toAppError())
        assertEquals(AppError.FileGone, FileNotFoundException("gone").toAppError())
        assertEquals(AppError.NeedsAccess, SecurityException("expired").toAppError())
    }
}
