package app.kholo.reader.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
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

/** Ajeeb files: owner-password, transparent photo, stencil tasveer, lambi photo. */
class PdfToolsEdgeCaseTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val dir = File(context.cacheDir, "tool-edge-test")
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

    private fun out(name: String) = File(dir, name)

    private fun textPdf(name: String, words: String = "Plain words"): File {
        val file = out(name)
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            PDPageContentStream(doc, page).use {
                it.beginText()
                it.setFont(PDType1Font.HELVETICA, 14f)
                it.newLineAtOffset(72f, 700f)
                it.showText(words)
                it.endText()
            }
            doc.save(file)
        }
        return file
    }

    /** Bank statement jaisi file: khulti bina password ke hai, par encrypted hai. */
    private fun ownerLocked(name: String): File {
        val file = textPdf("plain-$name")
        val locked = out(name)
        PDDocument.load(file).use { doc ->
            val policy = StandardProtectionPolicy("owner-secret", "", AccessPermission())
            policy.encryptionKeyLength = 128
            doc.protect(policy)
            doc.save(locked)
        }
        return locked
    }

    private fun text(file: File): String = PDDocument.load(file).use { PDFTextStripper().getText(it) }

    private fun render(file: File, width: Int = 400): Bitmap {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return PdfRenderer(fd).use { pdf ->
            pdf.openPage(0).use { page ->
                val height = width * page.height / page.width
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    @Test
    fun ownerLockedPdfCanBeMerged() {
        val merged = out("merged.pdf")
        tools.merge(listOf(ownerLocked("owner.pdf"), textPdf("second.pdf")), merged)
        assertEquals(2, PDDocument.load(merged).use { it.numberOfPages })
    }

    @Test
    fun ownerLockedPdfCanBeCompressed() {
        val small = out("small.pdf")
        tools.compress(ownerLocked("owner.pdf"), 0L, small)
        assertTrue(text(small).contains("Plain words"))
    }

    @Test
    fun ownerLockedPdfCanBeReordered() {
        val reordered = out("reordered.pdf")
        tools.rearrange(ownerLocked("owner.pdf"), listOf(0), reordered)
        assertTrue(text(reordered).contains("Plain words"))
    }

    @Test
    fun ownerLockedPdfCanGetANewPassword() {
        val again = out("again.pdf")
        tools.lock(ownerLocked("owner.pdf"), "new-pass", again)
        assertTrue(PDDocument.load(again, "new-pass").use { PDFTextStripper().getText(it) }.contains("Plain words"))
    }

    @Test
    fun compressNeverReturnsABiggerFile() {
        val source = textPdf("tiny.pdf")
        val result = out("tiny-small.pdf")
        tools.compress(source, 0L, result)
        assertTrue("${result.length()} > ${source.length()}", result.length() <= source.length())
    }

    /** Scan me text aksar stencil mask hota hai. Use JPEG banaya to kaala dabba ban jata hai. */
    @Test
    fun compressKeepsStencilMasks() {
        val file = out("stencil.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            val bitmap = Bitmap.createBitmap(304, 300, Bitmap.Config.ARGB_8888)
            val source = LosslessFactory.createFromImage(doc, bitmap)
            val stream = source.cosObject
            stream.setBoolean(COSName.IMAGE_MASK, true)
            stream.removeItem(COSName.COLORSPACE)
            stream.removeItem(COSName.SMASK)
            stream.removeItem(COSName.DECODE_PARMS)
            stream.setInt(COSName.BITS_PER_COMPONENT, 1)
            val bits = ByteArray(38 * 300) { 0x0F }
            stream.createOutputStream(COSName.FLATE_DECODE).use { it.write(bits) }
            PDPageContentStream(doc, page).use { it.drawImage(source, 0f, 0f, 595f, 842f) }
            doc.save(file)
        }
        val result = out("stencil-small.pdf")
        tools.compress(file, 0L, result)
        PDDocument.load(result).use { doc ->
            val resources = doc.getPage(0).resources
            val image = resources.xObjectNames.map { resources.getXObject(it) }.first() as PDImageXObject
            assertTrue("stencil was turned into a normal picture", image.isStencil)
        }
    }

    @Test
    fun transparentPngGetsAWhiteBackground() {
        val bitmap = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { color = Color.RED }
        Canvas(bitmap).drawCircle(300f, 400f, 60f, paint)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val result = out("transparent.pdf")
        tools.photosToPdf(listOf({ ByteArrayInputStream(bytes) }), result)
        val corner = render(result).getPixel(10, 10)
        assertTrue("corner is ${Integer.toHexString(corner)}", Color.red(corner) > 240 && Color.blue(corner) > 240)
    }

    @Test
    fun veryLongPhotoStaysInsideThePdfPageLimit() {
        val bitmap = Bitmap.createBitmap(100, 3000, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GRAY)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        val result = out("long.pdf")
        tools.photosToPdf(listOf({ ByteArrayInputStream(bytes) }), result)
        PDDocument.load(result).use { doc ->
            val box = doc.getPage(0).mediaBox
            assertTrue("page is ${box.height} tall", box.height <= 14400f)
            assertEquals(100f / 3000f, box.width / box.height, 0.002f)
        }
    }

    @Test
    fun ownerLockedPdfAsTheSecondFileCanBeMerged() {
        val merged = out("merged2.pdf")
        tools.merge(listOf(textPdf("first.pdf"), ownerLocked("owner.pdf")), merged)
        assertEquals(2, PDDocument.load(merged).use { it.numberOfPages })
    }

    @Test
    fun blockedPdfIsRefusedClearly() {
        val file = textPdf("plain-blocked.pdf")
        val locked = out("blocked.pdf")
        PDDocument.load(file).use { doc ->
            val permission = AccessPermission().apply {
                setCanModify(false)
                setCanAssembleDocument(false)
                setCanExtractContent(false)
            }
            doc.protect(StandardProtectionPolicy("owner-secret", "", permission).apply { encryptionKeyLength = 128 })
            doc.save(locked)
        }
        assertThrows(PdfRestricted::class.java) { tools.compress(locked, 0L, out("a.pdf")) }
        assertThrows(PdfRestricted::class.java) { tools.rearrange(locked, listOf(0), out("b.pdf")) }
        assertThrows(PdfRestricted::class.java) { tools.merge(listOf(locked, file), out("c.pdf")) }
        assertThrows(PdfRestricted::class.java) { tools.lock(locked, "x", out("d.pdf")) }
        assertThrows(PdfRestricted::class.java) { tools.extractText(locked, out("e.txt")) }
    }

    @Test
    fun scannedPdfSaysItHasNoText() {
        val bitmap = Bitmap.createBitmap(400, 500, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.LTGRAY)
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        val scan = out("scan.pdf")
        tools.photosToPdf(listOf({ ByteArrayInputStream(bytes) }), scan)
        assertFalse(tools.extractText(scan, out("scan.txt")))
        assertTrue(tools.extractText(textPdf("words.pdf"), out("words.txt")))
    }

    @Test
    fun aPdfWithNoPagesIsReported() {
        val file = out("zero.pdf")
        PDDocument().use { it.save(file) }
        assertThrows(PdfHasNoPages::class.java) { tools.pageCount(file) }
    }
}
