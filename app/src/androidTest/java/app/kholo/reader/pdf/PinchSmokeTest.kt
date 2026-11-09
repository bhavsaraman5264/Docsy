package app.kholo.reader.pdf

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Asli do-ungli pinch chalata hai, nateeja Kholo.ZOOM logs me dikhta hai. Arg: uri (content URI of a PDF). */
class PinchSmokeTest {
    @Test
    fun pinchInAndOut() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val uri = InstrumentationRegistry.getArguments().getString("uri")
        device.executeShellCommand(
            "am start -a android.intent.action.VIEW -t application/pdf -d $uri --grant-read-uri-permission " +
                "-n app.docsy.reader/app.kholo.reader.ViewerActivity",
        )
        val pdf = device.wait(Until.findObject(By.pkg("app.docsy.reader").depth(0)), 10_000)
        assertNotNull(pdf)
        pdf.setGestureMarginPercentage(0.25f)
        pdf.pinchOpen(0.8f, 1500)
        Thread.sleep(1500)
        pdf.pinchClose(0.8f, 1500)
        Thread.sleep(1500)
    }
}
