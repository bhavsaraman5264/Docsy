package app.kholo.reader.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.annotation.StringRes
import androidx.core.graphics.createBitmap
import app.kholo.reader.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

private const val KB = 1024L

/**
 * Kai portal 1 KB ko 1000 bytes maante hain, kai 1024. Upar ki seema 1000 se aur neeche ki 1024 se, taaki dono maanein.
 */
private const val PORTAL_KB = 1000L

/** Tay naap par sirf quality ghatti hai. Kram halke se bhaari, isliye [searchSteps] sabse saaf wali dhoondhta hai. */
private val QUALITY_STEPS = listOf(95, 90, 85, 80, 72, 64, 56, 48, 40, 32, 24)

/** Bina naap wali photo me pehle quality, phir size ghatta hai. */
private val SHRINK_STEPS = listOf(
    1f to 90,
    1f to 75,
    0.8f to 70,
    0.65f to 65,
    0.5f to 60,
    0.4f to 55,
    0.3f to 50,
    0.2f to 45,
    0.15f to 40,
    0.1f to 35,
)

private const val JPEG_COMMENT_MAX = 65_533
private const val JPEG_MARK = 0xFF
private const val JPEG_START = 0xD8
private const val JPEG_COMMENT = 0xFE
private const val BYTE_MASK = 0xFF
private const val MARKER_BYTES = 2
private const val SEGMENT_HEADER = 4
private const val NAME_STRIP = 0.17f
private const val NAME_TEXT = 0.42f
private const val NAME_MARGIN = 0.94f

/** Photo kaise naap me aaye. Photo kinare se kat sakti hai, signature kabhi nahi katna chahiye. */
enum class PhotoFit { Crop, Pad }

/** Form ki shart. [width] aur [height] 0 hon to photo ka apna naap rehta hai. */
data class PhotoSpec(
    val minKb: Int,
    val maxKb: Int,
    val width: Int = 0,
    val height: Int = 0,
    val fit: PhotoFit = PhotoFit.Crop,
) {
    val hasSize: Boolean get() = width > 0 && height > 0
    val maxBytes: Long get() = maxKb * PORTAL_KB
    val minBytes: Long get() = minKb * KB
}

/**
 * Sirf woh exam jinke niyam 2026 ke kai sources me ek jaise mile. SSC aur UPSC ab photo form bharte waqt live
 * khinchte hain, isliye unki photo ka preset nahi. Baaki ke liye KB wale aur apne naap wale option hain.
 */
enum class PhotoPreset(@param:StringRes val label: Int, val spec: PhotoSpec, val forExam: Boolean = false) {
    IbpsPhoto(R.string.preset_ibps_photo, PhotoSpec(minKb = 20, maxKb = 50, width = 200, height = 230), forExam = true),
    IbpsSign(
        R.string.preset_ibps_sign,
        PhotoSpec(minKb = 10, maxKb = 20, width = 140, height = 60, fit = PhotoFit.Pad),
        forExam = true,
    ),
    SscSign(R.string.preset_ssc_sign, PhotoSpec(minKb = 10, maxKb = 20), forExam = true),
    NeetPhoto(R.string.preset_neet_photo, PhotoSpec(minKb = 10, maxKb = 200), forExam = true),
    NeetSign(R.string.preset_neet_sign, PhotoSpec(minKb = 4, maxKb = 30), forExam = true),
    Kb10(R.string.compress_10, PhotoSpec(minKb = 0, maxKb = 10)),
    Kb20(R.string.compress_20, PhotoSpec(minKb = 0, maxKb = 20)),
    Kb50(R.string.compress_50, PhotoSpec(minKb = 0, maxKb = 50)),
    Kb100(R.string.compress_100, PhotoSpec(minKb = 0, maxKb = 100)),
    Kb200(R.string.compress_200, PhotoSpec(minKb = 0, maxKb = 200)),
}

/** [fits] false: upar ki seema tak nahi pahunchi, sabse chhoti di. [padded]: neeche ki seema ke liye bhari gayi. */
class ExamPhotoResult(val fits: Boolean, val padded: Boolean)

/**
 * Exam ya job form ke liye photo ya signature. [caption] ho to photo ke neeche naam aur date likhte hain.
 * Photo jitni ho sake saaf rehti hai: seema me aane wali sabse oonchi quality chuni jati hai.
 */
fun examPhoto(source: () -> InputStream?, spec: PhotoSpec, caption: String?, out: File): ExamPhotoResult {
    val loaded = loadPhoto(source) ?: throw IOException("unreadable photo")
    val flat = loaded.flattened().also { if (it !== loaded) loaded.recycle() }
    val sized = if (spec.hasSize) sizedTo(flat, spec) else flat
    if (sized !== flat) flat.recycle()
    if (!caption.isNullOrBlank()) drawCaption(sized, caption)
    val best = try {
        if (spec.hasSize) smallestAtSize(sized, spec.maxBytes) else smallestScaled(sized, spec.maxBytes)
    } finally {
        sized.recycle()
    }
    val padded = best.size < spec.minBytes
    out.writeBytes(if (padded) padJpeg(best, spec.minBytes) else best)
    return ExamPhotoResult(fits = best.size <= spec.maxBytes, padded = padded)
}

private fun smallestAtSize(photo: Bitmap, maxBytes: Long): ByteArray {
    var best: ByteArray? = null
    searchSteps(QUALITY_STEPS.size, smallestOnly = false) { index ->
        val bytes = photo.jpeg(QUALITY_STEPS[index])
        best = pickBetter(best, bytes, maxBytes)
        if (bytes.size <= maxBytes) StepOutcome.Fits else StepOutcome.TooBig
    }
    return best ?: throw IOException("nothing written")
}

private fun smallestScaled(photo: Bitmap, maxBytes: Long): ByteArray {
    var best: ByteArray? = null
    searchSteps(SHRINK_STEPS.size, smallestOnly = false) { index ->
        val (scale, quality) = SHRINK_STEPS[index]
        val width = (photo.width * scale).toInt().coerceAtLeast(1)
        val height = (photo.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(photo, width, height, true) else photo
        val bytes = scaled.jpeg(quality)
        if (scaled !== photo) scaled.recycle()
        best = pickBetter(best, bytes, maxBytes)
        if (bytes.size <= maxBytes) StepOutcome.Fits else StepOutcome.TooBig
    }
    return best ?: throw IOException("nothing written")
}

/** Seema me aane wali koshish hamesha jeetti hai. Koi na aaye to sabse chhoti. */
private fun pickBetter(best: ByteArray?, bytes: ByteArray, maxBytes: Long): ByteArray {
    if (best == null) return bytes
    val bestFits = best.size <= maxBytes
    val fits = bytes.size <= maxBytes
    return when {
        fits && !bestFits -> bytes
        fits -> if (bytes.size > best.size) bytes else best
        !bestFits && bytes.size < best.size -> bytes
        else -> best
    }
}

private fun Bitmap.jpeg(quality: Int): ByteArray =
    ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

/** Tay naap. Photo beech se katti hai, signature safed kinare ke saath poora aata hai. */
private fun sizedTo(photo: Bitmap, spec: PhotoSpec): Bitmap {
    val target = createBitmap(spec.width, spec.height)
    val canvas = Canvas(target)
    canvas.drawColor(Color.WHITE)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    val photoRatio = photo.width.toFloat() / photo.height
    val targetRatio = spec.width.toFloat() / spec.height
    if (spec.fit == PhotoFit.Crop) {
        val crop = if (photoRatio > targetRatio) {
            val width = (photo.height * targetRatio).toInt()
            Rect((photo.width - width) / 2, 0, (photo.width + width) / 2, photo.height)
        } else {
            val height = (photo.width / targetRatio).toInt()
            Rect(0, (photo.height - height) / 2, photo.width, (photo.height + height) / 2)
        }
        canvas.drawBitmap(photo, crop, RectF(0f, 0f, spec.width.toFloat(), spec.height.toFloat()), paint)
    } else {
        val scale = minOf(spec.width.toFloat() / photo.width, spec.height.toFloat() / photo.height)
        val width = photo.width * scale
        val height = photo.height * scale
        val left = (spec.width - width) / 2
        val top = (spec.height - height) / 2
        canvas.drawBitmap(photo, null, RectF(left, top, left + width, top + height), paint)
    }
    return target
}

/** Purane SSC jaise form photo ke neeche naam aur photo ki date maangte hain. Patti photo ke andar hi banti hai. */
private fun drawCaption(photo: Bitmap, caption: String) {
    val canvas = Canvas(photo)
    val strip = photo.height * NAME_STRIP
    val white = Paint().apply { color = Color.WHITE }
    canvas.drawRect(0f, photo.height - strip, photo.width.toFloat(), photo.height.toFloat(), white)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        textSize = strip * NAME_TEXT
    }
    val room = photo.width * NAME_MARGIN
    val width = paint.measureText(caption)
    if (width > room) paint.textSize *= room / width
    val baseline = photo.height - strip / 2 - (paint.ascent() + paint.descent()) / 2
    canvas.drawText(caption, photo.width / 2f, baseline, paint)
}

/**
 * Bahut saaf ya chhoti photo form ki neeche ki seema se bhi chhoti ban jati hai, aur portal use mana kar deta hai.
 * JPEG ke comment hisse me khaali bytes jodte hain: photo bilkul nahi badalti, bas file utni badi ho jati hai.
 */
internal fun padJpeg(jpeg: ByteArray, minBytes: Long): ByteArray {
    val isJpeg = jpeg.size >= MARKER_BYTES && jpeg[0] == JPEG_MARK.toByte() && jpeg[1] == JPEG_START.toByte()
    if (!isJpeg || jpeg.size >= minBytes) return jpeg
    val out = ByteArrayOutputStream(minBytes.toInt())
    out.write(jpeg, 0, MARKER_BYTES)
    var missing = minBytes - jpeg.size
    while (missing > 0) {
        val payload = minOf(missing - SEGMENT_HEADER, JPEG_COMMENT_MAX.toLong()).coerceAtLeast(0).toInt()
        // Segment ki lambai me uske apne do length bytes bhi gine jate hain.
        val length = payload + MARKER_BYTES
        out.write(JPEG_MARK)
        out.write(JPEG_COMMENT)
        out.write(length shr Byte.SIZE_BITS)
        out.write(length and BYTE_MASK)
        out.write(ByteArray(payload))
        missing -= payload + SEGMENT_HEADER
    }
    out.write(jpeg, MARKER_BYTES, jpeg.size - MARKER_BYTES)
    return out.toByteArray()
}
