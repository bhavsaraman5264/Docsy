package app.kholo.reader.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
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
import java.io.File
import java.io.IOException
import java.io.InputStream

private const val MAIN_MEMORY_BYTES = 24L * 1024 * 1024
private const val MAX_PHOTO_SIDE = 2200
private const val PHOTO_QUALITY = 0.82f
private const val KEY_LENGTH = 256
private const val MIN_IMAGE_PIXELS = 10_000L
private const val FULL_TURN = 360
private const val QUARTER_TURN = 90
private const val HALF_TURN = 180

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

class CompressResult(val reachedTarget: Boolean, val hadImages: Boolean)

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

    /** Bada PDF RAM me nahi aata: 24 MB ke baad temp file use hoti hai. */
    private fun open(file: File, password: String = ""): PDDocument {
        if (!ready) {
            PDFBoxResourceLoader.init(appContext)
            ready = true
        }
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

    fun pageCount(file: File): Int = open(file).use { doc ->
        doc.numberOfPages.also { if (it <= 0) throw PdfHasNoPages() }
    }

    fun merge(files: List<File>, out: File) {
        val merger = PDFMergerUtility()
        open(files.first()).use { target ->
            target.allow(AccessPermission::canAssembleDocument)
            files.drop(1).forEach { file ->
                open(file).use { source ->
                    source.allow(AccessPermission::canAssembleDocument)
                    merger.appendDocument(target, source)
                }
            }
            target.save(out)
        }
    }

    /** Jodna nahi, chunna: split, reorder aur page hatana teeno yahi hai. [order] me 0-based pages. */
    fun rearrange(file: File, order: List<Int>, out: File) {
        open(file).use { source ->
            source.allow(AccessPermission::canAssembleDocument)
            PDDocument(memory()).use { result ->
                order.forEach { index -> result.importPage(source.getPage(index)) }
                result.save(out)
            }
        }
    }

    fun photosToPdf(photos: List<() -> InputStream?>, out: File) {
        if (!ready) open(emptyPdf()).close()
        PDDocument(memory()).use { doc ->
            photos.forEach { source ->
                val bitmap = loadPhoto(source) ?: throw IOException("unreadable photo")
                val image = JPEGFactory.createFromImage(doc, bitmap, PHOTO_QUALITY)
                val box = fitPage(bitmap.width, bitmap.height)
                val page = PDPage(box)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { it.drawImage(image, 0f, 0f, box.width, box.height) }
                bitmap.recycle()
            }
            doc.save(out)
        }
    }

    private fun emptyPdf(): File {
        val file = File(scratchDir.apply { mkdirs() }, "init.pdf")
        if (!ready) {
            PDFBoxResourceLoader.init(appContext)
            ready = true
        }
        PDDocument().use {
            it.addPage(PDPage())
            it.save(file)
        }
        return file
    }

    /** Photo ko A4 ki chaudai par laate hain, taaki PDF print par sahi aaye. Bahut lambi photo chhoti hoti hai. */
    private fun fitPage(width: Int, height: Int): PDRectangle {
        val pageWidth = PDRectangle.A4.width
        val pageHeight = pageWidth * height / width
        val shrink = (MAX_PAGE_POINTS / pageHeight).coerceAtMost(1f)
        return PDRectangle(pageWidth * shrink, pageHeight * shrink)
    }

    private fun loadPhoto(source: () -> InputStream?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        source()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_PHOTO_SIDE) sample *= 2
        val decoded = source()?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        val degrees = source()?.use { exifRotation(it) } ?: 0
        return if (decoded == null || degrees == 0) {
            decoded
        } else {
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        }
    }

    private fun exifRotation(stream: InputStream): Int =
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> QUARTER_TURN
            ExifInterface.ORIENTATION_ROTATE_180 -> HALF_TURN
            ExifInterface.ORIENTATION_ROTATE_270 -> FULL_TURN - QUARTER_TURN
            else -> 0
        }

    /** Tasveerein chhoti karta hai, text jaisa tha waisa rehta hai. Target na mile to sabse chhoti file deta hai. */
    fun compress(file: File, targetBytes: Long, out: File): CompressResult {
        var hadImages = false
        var best = Long.MAX_VALUE
        val attempt = File(scratchDir.apply { mkdirs() }, "attempt.pdf")
        for ((scale, quality) in COMPRESS_STEPS) {
            open(file).use { doc ->
                doc.allow(AccessPermission::canModify)
                hadImages = recompressImages(doc, scale, quality) || hadImages
                doc.save(attempt)
            }
            if (attempt.length() < best) {
                best = attempt.length()
                attempt.copyTo(out, overwrite = true)
            }
            if (best <= targetBytes || !hadImages) break
        }
        attempt.delete()
        // Pehle se chhoti file dobara likhne par badi ho sakti hai. Tab asli hi behtar hai.
        if (best >= file.length()) {
            file.copyTo(out, overwrite = true)
            best = file.length()
        }
        return CompressResult(best <= targetBytes, hadImages)
    }

    private fun recompressImages(doc: PDDocument, scale: Float, quality: Float): Boolean {
        val done = HashMap<COSBase, PDImageXObject>()
        val made = HashSet<COSBase>()
        var changed = false
        for (page in doc.pages) {
            val resources = page.resources ?: continue
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

    /** Sirf tab chalta hai jab user ko password pata ho. */
    fun unlock(file: File, password: String, out: File) {
        open(file, password).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            doc.save(out)
        }
    }

    /** Scan wali PDF me text hota hi nahi. False ka matlab user ko batana hai, khaali file chup-chaap nahi deni. */
    fun extractText(file: File, out: File): Boolean {
        open(file).use { doc ->
            if (doc.isEncrypted && !doc.currentAccessPermission.canExtractContent()) throw PdfRestricted()
            out.writer(Charsets.UTF_8).use { PDFTextStripper().writeText(doc, it) }
        }
        return out.useLines { lines -> lines.any(String::isNotBlank) }
    }
}
