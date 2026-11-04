package app.kholo.reader.pdf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfCapabilitiesTest {
    @Test
    fun `android 15 and above always has full features`() {
        assertTrue(PdfCapabilities.from(sdkInt = 35, extensionS = 0).fullFeatures)
    }

    @Test
    fun `android 14 depends on the extension, as seen on the emulator with extension 7`() {
        assertFalse(PdfCapabilities.from(sdkInt = 34, extensionS = 7).fullFeatures)
        assertTrue(PdfCapabilities.from(sdkInt = 34, extensionS = 13).fullFeatures)
    }

    @Test
    fun `android 9 and 10 never have full features`() {
        assertFalse(PdfCapabilities.from(sdkInt = 28, extensionS = 0).fullFeatures)
        assertFalse(PdfCapabilities.from(sdkInt = 29, extensionS = 99).fullFeatures)
    }
}
