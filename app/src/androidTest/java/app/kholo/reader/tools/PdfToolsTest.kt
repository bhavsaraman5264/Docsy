package app.kholo.reader.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Har tool ka output PDF dobara khol kar jaanchta hai, sirf "crash nahi hua" nahi. */
class PdfToolsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val dir = File(context.cacheDir, "tool-test")
    private lateinit var tools: PdfTools

    @Before
    fun setUp() {
        dir.mkdirs()
        PDFBoxResourceLoader.init(context)
        tools = PdfTools(context, dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun asset(name: String): File {
        val out = File(dir, name)
        instrumentation.context.assets.open(name).use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    private fun out(name: String) = File(dir, name)

    private fun text(file: File, password: String = ""): String =
        PDDocument.load(file, password).use { PDFTextStripper().getText(it) }

    private fun pages(file: File, password: String = ""): Int = PDDocument.load(file, password).use { it.numberOfPages }

    private fun photo(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        val paint = Paint().apply { this.color = Color.BLACK }
        for (i in 0 until 400) canvas.drawCircle((i * 37 % 1200).toFloat(), (i * 91 % 1600).toFloat(), 18f, paint)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
    }

    @Test
    fun mergeKeepsAllPagesInOrder() {
        val result = out("merged.pdf")
        tools.merge(listOf(asset("simple.pdf"), asset("mixed-sizes.pdf")), result)
        assertEquals(8, pages(result))
        val content = text(result)
        assertTrue(content.indexOf("Simple PDF, page 1") < content.indexOf("A4 portrait"))
    }

    @Test
    fun rearrangeReordersAndDropsPages() {
        val result = out("reordered.pdf")
        tools.rearrange(asset("simple.pdf"), listOf(2, 0), result)
        assertEquals(2, pages(result))
        val content = text(result)
        assertTrue(content.indexOf("page 3") < content.indexOf("page 1"))
        assertFalse(content.contains("page 2"))
    }

    @Test
    fun splitKeepsTheChosenRange() {
        val result = out("split.pdf")
        tools.rearrange(asset("long-500-pages.pdf"), (99 until 110).toList(), result)
        assertEquals(11, pages(result))
        assertTrue(text(result).contains("page 100"))
    }

    @Test
    fun photosBecomePages() {
        val result = out("photos.pdf")
        val shots = listOf(photo(Color.WHITE), photo(Color.LTGRAY))
        tools.photosToPdf(shots.map { bytes -> { ByteArrayInputStream(bytes) } }, result)
        assertEquals(2, pages(result))
        assertTrue(result.length() > 1000)
    }

    @Test
    fun compressReachesTheTargetAndKeepsPages() {
        val source = out("big-photos.pdf")
        val shots = List(4) { photo(Color.WHITE) }
        tools.photosToPdf(shots.map { bytes -> { ByteArrayInputStream(bytes) } }, source)
        val target = source.length() / 3
        val result = out("small.pdf")
        val report = tools.compress(source, target, result)
        assertTrue(report.hadImages)
        assertTrue("${result.length()} > $target", result.length() <= target)
        assertEquals(4, pages(result))
    }

    @Test
    fun compressReportsWhenThereAreNoPhotos() {
        val result = out("same.pdf")
        val report = tools.compress(asset("simple.pdf"), 100L, result)
        assertFalse(report.hadImages)
        assertFalse(report.reachedTarget)
        assertTrue(text(result).contains("Simple PDF"))
    }

    @Test
    fun lockNeedsThePasswordAndKeepsContent() {
        val result = out("locked.pdf")
        tools.lock(asset("simple.pdf"), "s3cret", result)
        assertThrows(InvalidPasswordException::class.java) { PDDocument.load(result).close() }
        assertTrue(text(result, "s3cret").contains("Simple PDF"))
    }

    @Test
    fun unlockRemovesThePassword() {
        val result = out("open.pdf")
        tools.unlock(asset("Aadhaar_fake_ASHA1990.pdf"), "ASHA1990", result)
        assertTrue(text(result).contains("Fake Aadhaar"))
    }

    @Test
    fun unlockWithAWrongPasswordFails() {
        val file = asset("Aadhaar_fake_ASHA1990.pdf")
        assertThrows(InvalidPasswordException::class.java) { tools.unlock(file, "WRONG", out("x.pdf")) }
    }

    @Test
    fun toolsRefuseALockedPdfCleanly() {
        val file = asset("Aadhaar_fake_ASHA1990.pdf")
        assertThrows(InvalidPasswordException::class.java) { tools.pageCount(file) }
    }

    @Test
    fun textComesOutOfThePdf() {
        val result = out("text.txt")
        tools.extractText(asset("simple.pdf"), result)
        assertTrue(result.readText().contains("the quick brown fox"))
    }
}
