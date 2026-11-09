package app.kholo.reader.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.scale
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

private const val MAIN_MEMORY_BYTES = 24L * 1024 * 1024
private const val PHOTO_QUALITY = 82
private const val PHOTO_WORKERS = 3
private const val KEY_LENGTH = 256
private const val MIN_IMAGE_PIXELS = 10_000L

/** PDF readers isse lamba page theek se nahi dikhate. */
private const val MAX_PAGE_POINTS = 14_400f

/** Compress ke kadam: pehle halka, phir zyada. Target milte hi ruk jate hain. */
private val COMPRESS_STEPS = listOf(
    1f to 0.7f,
    0.8f to 0.6f,
    0.65f to 0.5f,
    0.5f to 0.42f,
    0.4f to 0.32f,
    0.3f to 0.25f,
)

/** Binary search me [COMPRESS_STEPS] par itni koshish se zyada nahi lagti. Sirf progress ke liye. */
private const val MAX_COMPRESS_PASSES = 3

/** Kaam kitna hua: `done` me se `total`. Cancel bhi yahin se phenka jata hai. */
typealias ToolProgress = (done: Int, total: Int) -> Unit

private val NO_PROGRESS: ToolProgress = { _, _ -> }

class CompressResult(val reachedTarget: Boolean, val hadImages: Boolean, val alreadySmall: Boolean = false)

private class EncodedPhoto(val jpeg: ByteArray, val width: Int, val height: Int)

/** PDF banane wale ne yeh badlav mana kiya hai. Password yahan khali hota hai, isliye file khul jati hai. */
class PdfRestricted : IOException()

class PdfHasNoPages : IOException()

/** PDF ke saare kaam. Sab sirf phone par hota hai, aur asli file kabhi nahi badalti: nateeja nayi file hai. */
class PdfTools(context: Context, private val scratchDir: File) {
    private val appContext = context.applicationContext

    @Volatile private var ready = false

    private fun memory(): MemoryUsageSetting {
        scratchDir.mkdirs()
        return MemoryUsageSetting.setupMixed(MAIN_MEMORY_BYTES).setTempDir(scratchDir)
    }

    private fun ensureReady() {
        if (!ready) {
            PDFBoxResourceLoader.init(appContext)
            ready = true
        }
    }

    /** User file chun raha hota hai tab tak PDFBox ki classes load ho jati hain, pehla kaam atakta nahi. */
    fun warmUp() {
        ensureReady()
        PDDocument().close()
    }

    /** Bada PDF RAM me nahi aata: 24 MB ke baad temp file use hoti hai. */
    private fun open(file: File, password: String = ""): PDDocument {
        ensureReady()
        return PDDocument.load(file, password, memory())
    }

    /**
     * Bank statement jaisi PDF bina password ke khulti hai par encrypted hoti hai, aur PDFBox use waise
     * save nahi karta. Jo badlav file allow karti hai wahan encryption hata dete hain, baaki me saaf mana.
     */
    private fun PDDocument.allow(check: (AccessPermission) -> Boolean) {
        if (!isEncrypted) return
        if (!check(currentAccessPermission)) throw PdfRestricted()
        isAllSecurityToBeRemoved = true
    }

    /** Convert tools ke liye: PDF khul kar kaam hote hi band ho jati hai. */
    fun <T> read(file: File, block: (PDDocument) -> T): T = open(file).use(block)

    /** Native renderer PDFBox se kai guna tez hai. Password ya kharab file ki saaf galti PDFBox hi batata hai. */
    fun pageCount(file: File): Int = quickPageCount(file) ?: open(file).use { doc ->
        doc.numberOfPages.also { if (it <= 0) throw PdfHasNoPages() }
    }

    private fun quickPageCount(file: File): Int? = try {
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
            pdf.pageCount.takeIf { it > 0 }
        }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    fun merge(files: List<File>, out: File, progress: ToolProgress = NO_PROGRESS) {
        val merger = PDFMergerUtility()
        open(files.first()).use { target ->
            target.allow(AccessPermission::canAssembleDocument)
            files.drop(1).forEachIndexed { index, file ->
                progress(index + 1, files.size)
                open(file).use { source ->
                    source.allow(AccessPermission::canAssembleDocument)
                    merger.appendDocument(target, source)
                }
            }
            target.save(out)
        }
    }

    /** Jodna nahi, chunna: page nikalna, reorder aur hatana. [order] me 0-based pages, [turns] me degree. */
    fun rearrange(file: File, order: List<Int>, out: File, turns: Map<Int, Int> = emptyMap()) {
        open(file).use { source ->
            source.allow(AccessPermission::canAssembleDocument)
            writePages(source, order, out, turns)
        }
    }

    /** Ek PDF ke kai hisse. Asli file ek hi baar khulti hai, har hissa [outs] me apni file banta hai. */
    fun split(file: File, parts: List<List<Int>>, outs: List<File>, progress: ToolProgress = NO_PROGRESS) {
        open(file).use { source ->
            source.allow(AccessPermission::canAssembleDocument)
            parts.forEachIndexed { index, pages ->
                progress(index, parts.size)
                writePages(source, pages, outs[index])
            }
        }
    }

    private fun writePages(source: PDDocument, order: List<Int>, out: File, turns: Map<Int, Int> = emptyMap()) {
        PDDocument(memory()).use { result ->
            order.forEach { index ->
                val page = result.importPage(source.getPage(index))
                turns[index]?.let { page.rotation = (page.rotation + it) % FULL_TURN }
            }
            result.save(out)
        }
    }

    /** Photo padhna aur JPEG banana sabse bhaari hai, isliye woh saath chalta hai. PDF me jodna kram se. */
    fun photosToPdf(
        photos: List<() -> InputStream?>,
        out: File,
        progress: ToolProgress = NO_PROGRESS,
        workers: Int = PHOTO_WORKERS,
    ) {
        ensureReady()
        PDDocument(memory()).use { doc ->
            pipeline(photos, workers, ::encodePhoto) { index, photo ->
                val image = JPEGFactory.createFromByteArray(doc, photo.jpeg)
                val box = fitPage(photo.width, photo.height)
                val page = PDPage(box)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { it.drawImage(image, 0f, 0f, box.width, box.height) }
                progress(index + 1, photos.size)
            }
            doc.save(out)
        }
    }

    private fun encodePhoto(source: () -> InputStream?): EncodedPhoto {
        val decoded = loadPhoto(source) ?: throw IOException("unreadable photo")
        val photo = decoded.flattened()
        val jpeg = ByteArrayOutputStream().also { photo.compress(Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, it) }
        val encoded = EncodedPhoto(jpeg.toByteArray(), photo.width, photo.height)
        if (photo !== decoded) decoded.recycle()
        photo.recycle()
        return encoded
    }

    /** Photo ko A4 ki chaudai par laate hain, taaki PDF print par sahi aaye. Bahut lambi photo chhoti hoti hai. */
    private fun fitPage(width: Int, height: Int): PDRectangle {
        val pageWidth = PDRectangle.A4.width
        val pageHeight = pageWidth * height / width
        val shrink = (MAX_PAGE_POINTS / pageHeight).coerceAtMost(1f)
        return PDRectangle(pageWidth * shrink, pageHeight * shrink)
    }

    /**
     * Tasveerein chhoti karta hai, text jaisa tha waisa rehta hai. Target na mile to sabse chhoti file deta hai.
     * Har koshish poori file dobara likhti hai, isliye koshish kam se kam rakhi hain: dekho [searchSteps].
     */
    fun compress(file: File, targetBytes: Long, out: File, progress: ToolProgress = NO_PROGRESS): CompressResult {
        if (targetBytes > 0L && file.length() <= targetBytes) {
            file.copyTo(out, overwrite = true)
            return CompressResult(reachedTarget = true, hadImages = true, alreadySmall = true)
        }
        var hadImages = false
        var best = Long.MAX_VALUE
        var bestFits = false
        var pass = 0
        val passes = if (targetBytes > 0L) MAX_COMPRESS_PASSES else 1
        val attempt = File(scratchDir.apply { mkdirs() }, "attempt.pdf")
        try {
            searchSteps(COMPRESS_STEPS.size, smallestOnly = targetBytes <= 0L) { index ->
                val (scale, quality) = COMPRESS_STEPS[index]
                open(file).use { doc ->
                    doc.allow(AccessPermission::canModify)
                    val changed = recompressImages(doc, scale, quality) { done, pages ->
                        progress(pass * pages + done, passes * pages)
                    }
                    hadImages = changed || hadImages
                    doc.save(attempt)
                }
                pass++
                val fits = attempt.length() <= targetBytes
                if (fits || (!bestFits && attempt.length() < best)) {
                    best = attempt.length()
                    bestFits = fits
                    attempt.copyTo(out, overwrite = true)
                }
                when {
                    !hadImages -> StepOutcome.Stop
                    fits -> StepOutcome.Fits
                    else -> StepOutcome.TooBig
                }
            }
        } finally {
            attempt.delete()
        }
        // Pehle se chhoti file dobara likhne par badi ho sakti hai. Tab asli hi behtar hai.
        if (best >= file.length()) {
            file.copyTo(out, overwrite = true)
            best = file.length()
        }
        return CompressResult(best <= targetBytes, hadImages)
    }

    private fun recompressImages(doc: PDDocument, scale: Float, quality: Float, progress: ToolProgress): Boolean {
        val done = HashMap<COSBase, PDImageXObject>()
        val made = HashSet<COSBase>()
        var changed = false
        val pages = doc.numberOfPages
        doc.pages.forEachIndexed { index, page ->
            progress(index, pages)
            val resources = page.resources ?: return@forEachIndexed
            for (name in resources.xObjectNames.toList()) {
                val image = resources.getXObject(name) as? PDImageXObject ?: continue
                if (image.cosObject in made || !canReencode(image)) continue
                val smaller = done[image.cosObject] ?: reencode(doc, image, scale, quality)?.also {
                    done[image.cosObject] = it
                    made += it.cosObject
                }
                if (smaller != null) {
                    resources.put(name, smaller)
                    changed = true
                }
            }
        }
        return changed
    }

    /** Stencil aur mask wali tasveer JPEG banne par kaala dabba ban jati hai, aur 1-bit scan JPEG me bada hota hai. */
    private fun canReencode(image: PDImageXObject): Boolean {
        val pixels = image.width.toLong() * image.height
        val masked = image.softMask != null || image.cosObject.getDictionaryObject(COSName.MASK) != null
        return !image.isStencil && !masked && image.bitsPerComponent > 1 && pixels >= MIN_IMAGE_PIXELS
    }

    /** Ek tasveer na padhi jaye to use chhod do, poora kaam na gire. */
    private fun reencode(doc: PDDocument, image: PDImageXObject, scale: Float, quality: Float): PDImageXObject? = try {
        val source = image.image
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (scale < 1f) source.scale(width, height) else source
        JPEGFactory.createFromImage(doc, scaled, quality)
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }

    fun lock(file: File, password: String, out: File) {
        open(file).use { doc ->
            if (doc.isEncrypted && !doc.currentAccessPermission.canModify()) throw PdfRestricted()
            val policy = StandardProtectionPolicy(password, password, AccessPermission())
            policy.encryptionKeyLength = KEY_LENGTH
            doc.protect(policy)
            doc.save(out)
        }
    }

    /** Sirf tab chalta hai jab user ko password pata ho. False ka matlab file par password tha hi nahi. */
    fun unlock(file: File, password: String, out: File): Boolean = open(file, password).use { doc ->
        val locked = doc.isEncrypted
        doc.isAllSecurityToBeRemoved = true
        doc.save(out)
        locked
    }

    /** Scan wali PDF me text hota hi nahi. False ka matlab user ko batana hai, khaali file chup-chaap nahi deni. */
    fun extractText(file: File, out: File, progress: ToolProgress = NO_PROGRESS): Boolean {
        open(file).use { doc ->
            if (doc.isEncrypted && !doc.currentAccessPermission.canExtractContent()) throw PdfRestricted()
            val stripper = object : PDFTextStripper() {
                override fun endPage(page: PDPage) {
                    super.endPage(page)
                    progress(currentPageNo, doc.numberOfPages)
                }
            }
            out.writer(Charsets.UTF_8).use { stripper.writeText(doc, it) }
        }
        return out.useLines { lines -> lines.any(String::isNotBlank) }
    }
}
